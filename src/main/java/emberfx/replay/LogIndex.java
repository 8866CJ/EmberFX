package emberfx.replay;

import edu.wpi.first.util.datalog.DataLogReader;
import edu.wpi.first.util.datalog.DataLogRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every value in one or more .wpilog files, held in memory column by column (one timestamp array
 * and one value array per entry) so {@link Replayer} can find the value of any entry at any time.
 *
 * <p>Log entry names become NetworkTables topic paths, so a replayed value lands where the live
 * dashboard would have seen it:
 * <ul>
 *   <li>{@code NT:/x} (DataLogManager, EmberFX's own logs) becomes {@code /x}.</li>
 *   <li>AdvantageKit logs: in a file with any entry under {@code /RealOutputs/}, {@code /ReplayOutputs/},
 *       {@code /RealMetadata/} or {@code /ReplayMetadata/}, every name that starts with "/" gets
 *       {@code /AdvantageKit} in front, as AdvantageKit publishes it live. Names under those four
 *       prefixes get it in any file.</li>
 *   <li>{@code DS:x} (Driver Station data, also written by owlet) becomes {@code /DriverStation/x}.</li>
 *   <li>owlet output (converted hoot logs): device signals get the CAN bus after the vendor, since
 *       device IDs repeat across buses: {@code Phoenix6/TalonFX-1/Velocity} becomes
 *       {@code /Phoenix6/rio/TalonFX-1/Velocity} or {@code /Phoenix6/<CANivore name>/TalonFX-1/Velocity}.
 *       The bus is the log's extra header, which owlet sets.</li>
 *   <li>Anything else (owlet's {@code DriveState/Pose} and {@code RobotMode}) becomes "/" + the
 *       name without its leading slashes.</li>
 *   <li>Schema entries ({@code .schema/struct:Pose2d} with any of the prefixes above) are not topics:
 *       they are kept as {@link Schema}s, which the Replayer registers with addSchema.</li>
 * </ul>
 * Files are merged by topic: entries that map to the same topic and have the same type become one
 * column, ordered by timestamp. The same topic with a different type is dropped with a warning.
 *
 * <p>A record that repeats the previous value of its entry is not stored: the value as of any time
 * is the same without it, and owlet output is mostly such repeats. Records from several files that
 * carry the same signal (both hoot files of one robot have the roboRIO's DS and pose data) collapse
 * the same way.
 *
 * <p>Timestamps are the log's own (robot FPGA time in microseconds for robot and hoot logs).
 * Files are merged on that time base without shifting.
 */
public final class LogIndex {
    private static final System.Logger LOG = System.getLogger(LogIndex.class.getName());
    private static final String[] AK_PREFIXES = {"/RealOutputs/", "/ReplayOutputs/", "/RealMetadata/", "/ReplayMetadata/"};
    private static final String SCHEMA_DIR = ".schema/";
    private static final Pattern PHOENIX = Pattern.compile("(Phoenix\\d*)/"); // owlet device signals: Phoenix6/TalonFX-1/...
    private static final int CHECK_EVERY = 1 << 16; // records between interrupt checks and progress reports
    private static final int HEADER = 12;           // bytes in a .wpilog header with no extra header
    private static final int CHUNK = 16 << 20;      // bytes read at a time

    final Column[] columns;
    final List<Schema> schemas;
    private final List<String> sources;
    private final List<Phase> phases;
    private final long start, end;
    private long records;

    /** Robot mode from startMicros to endMicros. Mode is "Disabled", "Auto", "Teleop" or "Test". */
    public record Phase(long startMicros, long endMicros, String mode) {}

    /** A data schema from the log, for NetworkTableInstance.addSchema(name, type, data). */
    public record Schema(String name, String type, byte[] data) {}

    private LogIndex(Column[] columns, List<Schema> schemas, List<String> sources) {
        this.columns = columns;
        this.schemas = schemas;
        this.sources = sources;
        long s = Long.MAX_VALUE, e = Long.MIN_VALUE;
        for (Column c : columns) {
            s = Math.min(s, c.ts[0]);
            e = Math.max(e, c.ts[c.size - 1]);
        }
        start = columns.length == 0 ? 0 : s;
        end = columns.length == 0 ? 0 : e;
        phases = phases(this);
    }

    /** Load and merge one or more .wpilog files. Progress 0..1. Throws IOException with a readable message. */
    public static LogIndex load(List<Path> wpilogs, DoubleConsumer progress) throws IOException, InterruptedException {
        if (wpilogs.isEmpty()) throw new IOException("No log files given");
        long total = 0;
        for (Path p : wpilogs) total += size(p);
        Map<String, Column> merged = new LinkedHashMap<>();
        Map<String, Schema> schemas = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();
        long done = 0, records = 0;
        for (Path p : wpilogs) {
            long size = size(p);
            final long before = done, all = Math.max(1, total);
            records += readFile(p, merged, schemas, read -> progress.accept((before + read) / (double) all));
            done += size;
            sources.add(p.getFileName().toString());
        }
        List<Column> cols = new ArrayList<>();
        for (Column c : merged.values()) {
            c.finish();
            if (c.size > 0) cols.add(c);
        }
        progress.accept(1);
        LogIndex index = new LogIndex(cols.toArray(new Column[0]), List.copyOf(schemas.values()), List.copyOf(sources));
        index.records = records;
        return index;
    }

    public long startMicros() { return start; }

    public long endMicros() { return end; }

    /** Number of topics with at least one value (schemas not counted). */
    public int entryCount() { return columns.length; }

    /** File names loaded, for the title. */
    public List<String> sourceNames() { return sources; }

    /** Robot mode over time, for timeline colors; empty if the logs have no mode data. */
    public List<Phase> phases() { return phases; }

    /** Topic paths in the index, in load order. */
    public List<String> topics() {
        List<String> out = new ArrayList<>(columns.length);
        for (Column c : columns) out.add(c.topic);
        return out;
    }

    /** Schemas found in the logs. */
    public List<Schema> schemas() { return schemas; }

    /** Records read from the files, control records included. */
    public long recordCount() { return records; }

    /** Total stored values over all topics (after dropping repeats). */
    public long valueCount() {
        long n = 0;
        for (Column c : columns) n += c.size;
        return n;
    }

    private static long size(Path p) throws IOException {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new IOException("Can't read " + p + ": " + e.getMessage(), e);
        }
    }

    /** Columns by entry id: an array for the usual small ids (no boxing per record), a map for others. */
    private static final class Active {
        private Column[] small = new Column[256];
        private final Map<Integer, Column> big = new HashMap<>();

        Column get(int id) {
            return id >= 0 && id < small.length ? small[id] : big.get(id);
        }

        void put(int id, Column c) {
            if (id >= 0 && id < (1 << 20)) {
                if (id >= small.length) small = Arrays.copyOf(small, Math.max(id + 1, small.length * 2));
                small[id] = c;
            } else if (c == null) {
                big.remove(id);
            } else {
                big.put(id, c);
            }
        }
    }

    /** Thrown inside DataLogReader.forEach to stop reading; turned into InterruptedException. */
    private static final class Cancelled extends RuntimeException {
        Cancelled() { super(null, null, false, false); }
    }

    private static long readFile(Path path, Map<String, Column> merged, Map<String, Schema> schemas,
                                 LongConsumer bytesRead) throws IOException, InterruptedException {
        String file = path.getFileName().toString();
        Active active = new Active();
        Map<Integer, String[]> schemaEntries = new HashMap<>(); // id -> {schema name, type}
        Map<String, Column> byName = new LinkedHashMap<>(); // log name + type -> column, so a reused name continues
        long[] state = {0, 0}; // records, records with no start
        String[] extraHeader = {""};
        try {
            extraHeader[0] = readRecords(path, bytesRead, r -> {
                if ((++state[0] & (CHECK_EVERY - 1)) == 0 && Thread.interrupted()) throw new Cancelled();
                if (r.isControl()) {
                    if (r.isStart()) {
                        DataLogRecord.StartRecordData s = r.getStartData();
                        String schema = schemaName(s.name);
                        if (schema != null) {
                            schemaEntries.put(s.entry, new String[] {schema, s.type});
                            active.put(s.entry, null);
                        } else {
                            schemaEntries.remove(s.entry);
                            active.put(s.entry, byName.computeIfAbsent(s.name + "\n" + s.type, k -> new Column(s.name, s.type)));
                        }
                    } else if (r.isFinish()) {
                        active.put(r.getFinishEntry(), null);
                        schemaEntries.remove(r.getFinishEntry());
                    }
                    return; // set-metadata: nothing to keep
                }
                Column c = active.get(r.getEntry());
                if (c != null) {
                    c.add(r);
                    return;
                }
                String[] schema = schemaEntries.get(r.getEntry());
                if (schema != null) {
                    if (r.getSize() > 0) schemas.putIfAbsent(schema[0], new Schema(schema[0], schema[1], r.getRaw()));
                } else {
                    state[1]++;
                }
            });
        } catch (Cancelled | ClosedByInterruptException e) {
            Thread.interrupted();
            throw new InterruptedException("Loading " + file + " was cancelled");
        }
        if (state[1] > 0) LOG.log(System.Logger.Level.WARNING, file + ": skipped " + state[1] + " records of entries with no start record");

        boolean ak = false;
        for (Column c : byName.values()) {
            if (startsWithAny(c.logName, AK_PREFIXES)) ak = true;
        }
        String bus = canBus(extraHeader[0], byName.values());
        for (Column c : byName.values()) {
            c.report(file);
            if (c.size == 0) continue;
            c.topic = topicName(c.logName, ak, bus);
            Column old = merged.get(c.topic);
            if (old == null) merged.put(c.topic, c);
            else if (old.logType.equals(c.logType)) old.append(c);
            else LOG.log(System.Logger.Level.WARNING, file + ": " + c.logName + " is " + c.logType + " but " + c.topic
                + " was already loaded as " + old.logType + "; skipping it");
        }
        return state[0];
    }

    /**
     * Feed every record of a .wpilog to the handler, reading the file in chunks into one reused heap
     * buffer. DataLogReader(String) would memory-map the whole file instead, which on Windows keeps
     * the file locked until the mapping is garbage collected. Each chunk is handed to DataLogReader
     * behind a copy of a minimal header. A record cut off at the end of the file is dropped.
     * Returns the file's extra header.
     */
    private static String readRecords(Path path, LongConsumer bytesRead, Consumer<DataLogRecord> handler) throws IOException {
        String file = path.getFileName().toString();
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer head = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN);
            while (head.hasRemaining() && ch.read(head) >= 0) { /* fill */ }
            if (head.hasRemaining() || !new DataLogReader(head.flip()).isValid()) throw new IOException(file + " is not a .wpilog file");
            int extraLen = head.getInt(8);
            if (extraLen < 0 || extraLen > ch.size() - HEADER) throw new IOException(file + " has a broken header");
            ByteBuffer extra = ByteBuffer.allocate(extraLen);
            while (extra.hasRemaining() && ch.read(extra) >= 0) { /* fill */ }
            String extraHeader = new String(extra.array(), StandardCharsets.UTF_8);

            ByteBuffer buf = ByteBuffer.allocate(HEADER + CHUNK).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(new byte[] {'W', 'P', 'I', 'L', 'O', 'G', 0, 1, 0, 0, 0, 0}); // version 1.0, no extra header
            boolean eof = false;
            while (true) {
                while (buf.hasRemaining() && !eof) eof = ch.read(buf) < 0;
                int limit = buf.position(), end = HEADER;
                long len;
                while ((len = recordLength(buf, end, limit)) > 0 && end + len <= limit) end += (int) len;
                if (end > HEADER) {
                    new DataLogReader(buf.duplicate().position(0).limit(end).slice()).forEach(handler);
                    bytesRead.accept(ch.position() - (limit - end));
                }
                if (eof) {
                    if (end < limit) LOG.log(System.Logger.Level.WARNING, file + " ends in a cut-off record; ignored it");
                    return extraHeader;
                }
                ByteBuffer rest = buf.duplicate().position(end).limit(limit); // the partial record
                if (end == HEADER) { // one record bigger than the buffer: grow it
                    if (len > Integer.MAX_VALUE - HEADER) throw new IOException(file + " has a record of " + len + " bytes");
                    buf = ByteBuffer.allocate(HEADER + (int) len).order(ByteOrder.LITTLE_ENDIAN).put(buf.flip());
                } else {
                    buf.position(HEADER);
                    buf.put(rest);
                }
            }
        } catch (NoSuchFileException e) {
            throw new IOException("Can't find " + path, e);
        } catch (ClosedByInterruptException e) {
            throw e;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(file)) throw e;
            throw new IOException("Can't read " + file + ": " + e.getMessage(), e);
        }
    }

    /** Byte length (header + data) of the record at pos, or -1 if its header isn't all before limit. */
    private static long recordLength(ByteBuffer buf, int pos, int limit) {
        if (pos >= limit) return -1;
        int lenByte = buf.get(pos) & 0xff;
        int entryLen = (lenByte & 0x3) + 1, sizeLen = ((lenByte >> 2) & 0x3) + 1, tsLen = ((lenByte >> 4) & 0x7) + 1;
        int headerLen = 1 + entryLen + sizeLen + tsLen;
        if (pos + headerLen > limit) return -1;
        long size = 0;
        for (int i = 0; i < sizeLen; i++) size |= (buf.get(pos + 1 + entryLen + i) & 0xffL) << (8 * i);
        return headerLen + size;
    }

    private static boolean startsWithAny(String s, String[] prefixes) {
        for (String p : prefixes) if (s.startsWith(p)) return true;
        return false;
    }

    /** The schema name ("struct:Pose2d") if this log name is a schema entry, else null. */
    static String schemaName(String logName) {
        String n = logName.startsWith("NT:") ? logName.substring(3) : logName;
        while (n.startsWith("/")) n = n.substring(1);
        if (n.startsWith("AdvantageKit/")) n = n.substring("AdvantageKit/".length());
        return n.startsWith(SCHEMA_DIR) && n.length() > SCHEMA_DIR.length() ? n.substring(SCHEMA_DIR.length()) : null;
    }

    /**
     * The CAN bus of an owlet-converted hoot log (owlet writes the bus name as the extra header:
     * "roboRIO Native CAN Bus" or the CANivore's name), as "rio" or that name; null if not a hoot log.
     */
    static String canBus(String extraHeader, Iterable<Column> columns) {
        if (extraHeader.isBlank()) return null;
        boolean phoenix = false;
        for (Column c : columns) if (PHOENIX.matcher(c.logName).lookingAt()) phoenix = true;
        if (!phoenix) return null;
        return extraHeader.equals("roboRIO Native CAN Bus") ? "rio" : extraHeader.trim().replace('/', '_');
    }

    /** NetworkTables topic path for a log entry name; see the class comment. Bus: see canBus, or null. */
    static String topicName(String logName, boolean advantageKitFile, String bus) {
        Matcher phoenix = PHOENIX.matcher(logName);
        if (bus != null && phoenix.lookingAt()) return "/" + phoenix.group(1) + "/" + bus + "/" + logName.substring(phoenix.end());
        if (logName.startsWith("NT:")) {
            String n = logName.substring(3);
            return n.startsWith("/") ? n : "/" + n;
        }
        if (logName.startsWith("DS:")) return "/DriverStation/" + logName.substring(3);
        if (startsWithAny(logName, AK_PREFIXES) || (advantageKitFile && logName.startsWith("/"))) {
            return "/AdvantageKit" + logName;
        }
        int i = 0;
        while (i < logName.length() && logName.charAt(i) == '/') i++;
        return "/" + logName.substring(i);
    }

    // ---- phases ----

    private static List<Phase> phases(LogIndex index) {
        Map<String, Column> byTopic = new HashMap<>();
        for (Column c : index.columns) byTopic.put(c.topic, c);
        List<long[]> changes = new ArrayList<>(); // {time, mode index}
        Column fms = byTopic.get("/FMSInfo/FMSControlData");
        Column enabled = first(byTopic, "/DriverStation/Enabled", "/AdvantageKit/DriverStation/Enabled");
        Column robotMode = byTopic.get("/RobotMode");
        Column robotEnable = byTopic.get("/RobotEnable");
        if (fms != null && fms.kind != Kind.OBJECT && fms.kind != Kind.BOOL) {
            for (int i = 0; i < fms.size; i++) {
                long w = fms.kind == Kind.INT ? fms.l[i] : (long) fms.d[i];
                changes.add(new long[] {fms.ts[i], (w & 1) == 0 ? 0 : (w & 4) != 0 ? 3 : (w & 2) != 0 ? 1 : 2});
            }
        } else if (enabled != null && enabled.kind == Kind.BOOL) {
            String base = enabled.topic.substring(0, enabled.topic.length() - "Enabled".length());
            Column auto = byTopic.get(base + "Autonomous"), test = byTopic.get(base + "Test");
            Column[] cols = {enabled, auto != null && auto.kind == Kind.BOOL ? auto : null, test != null && test.kind == Kind.BOOL ? test : null};
            boolean[] v = new boolean[3];
            int[] at = new int[3];
            while (true) { // sweep the three columns in time order
                int next = -1;
                for (int k = 0; k < 3; k++) {
                    if (cols[k] != null && at[k] < cols[k].size && (next < 0 || cols[k].ts[at[k]] < cols[next].ts[at[next]])) next = k;
                }
                if (next < 0) break;
                long t = cols[next].ts[at[next]];
                v[next] = cols[next].b[at[next]++];
                changes.add(new long[] {t, !v[0] ? 0 : v[2] ? 3 : v[1] ? 1 : 2});
            }
        } else if (robotMode != null && robotMode.kind == Kind.OBJECT) { // owlet
            for (int i = 0; i < robotMode.size; i++) {
                String m = String.valueOf(robotMode.o[i]);
                changes.add(new long[] {robotMode.ts[i], m.startsWith("Auto") ? 1 : m.startsWith("Teleop") ? 2 : m.startsWith("Test") ? 3 : 0});
            }
        } else if (robotEnable != null && robotEnable.kind == Kind.BOOL) { // enabled only; no auto/teleop
            for (int i = 0; i < robotEnable.size; i++) changes.add(new long[] {robotEnable.ts[i], robotEnable.b[i] ? 2 : 0});
        }
        return spans(changes, index.end);
    }

    private static final String[] MODES = {"Disabled", "Auto", "Teleop", "Test"};

    /** Mode changes (in time order) to spans, merging repeats; the last span ends at end. */
    static List<Phase> spans(List<long[]> changes, long end) {
        List<Phase> out = new ArrayList<>();
        long spanStart = 0;
        int mode = -1;
        for (long[] c : changes) {
            if (c[1] == mode) continue;
            if (mode >= 0 && c[0] > spanStart) out.add(new Phase(spanStart, c[0], MODES[mode]));
            spanStart = c[0];
            mode = (int) c[1];
        }
        if (mode >= 0 && end > spanStart) out.add(new Phase(spanStart, end, MODES[mode]));
        // two changes at one time can leave equal neighbours; merge them
        List<Phase> merged = new ArrayList<>();
        for (Phase p : out) {
            Phase last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.mode.equals(p.mode) && last.endMicros == p.startMicros) {
                merged.set(merged.size() - 1, new Phase(last.startMicros, p.endMicros, p.mode));
            } else {
                merged.add(p);
            }
        }
        return Collections.unmodifiableList(merged);
    }

    private static Column first(Map<String, Column> byTopic, String... topics) {
        for (String t : topics) if (byTopic.get(t) != null) return byTopic.get(t);
        return null;
    }

    // ---- storage ----

    enum Kind { DOUBLE, INT, BOOL, OBJECT }

    /**
     * One topic's values in time order: ts[i] with d[i], l[i], b[i] or o[i] depending on kind.
     * Strings are interned per column; arrays, raw and struct values are kept as their arrays.
     */
    static final class Column {
        final String logName, logType;
        final Kind kind;
        String topic;
        int size;
        long[] ts = new long[8];
        double[] d;
        long[] l;
        boolean[] b;
        Object[] o;
        private boolean sorted = true;
        private final boolean isFloat;
        private Map<Object, Object> strings = new HashMap<>();
        private byte[] lastRaw; // bytes of the last stored value, while loading
        private long badRecords;

        Column(String logName, String logType) {
            this.logName = logName;
            this.logType = logType;
            isFloat = logType.equals("float");
            kind = switch (logType) {
                case "double", "float" -> Kind.DOUBLE;
                case "int64" -> Kind.INT;
                case "boolean" -> Kind.BOOL;
                default -> Kind.OBJECT;
            };
            switch (kind) {
                case DOUBLE -> d = new double[8];
                case INT -> l = new long[8];
                case BOOL -> b = new boolean[8];
                default -> o = new Object[8];
            }
        }

        void add(DataLogRecord r) {
            long t = r.getTimestamp();
            int n = size;
            try {
                switch (kind) {
                    case DOUBLE -> {
                        double v = isFloat ? r.getFloat() : r.getDouble();
                        if (n > 0 && sorted && t >= ts[n - 1] && Double.doubleToRawLongBits(d[n - 1]) == Double.doubleToRawLongBits(v)) return;
                        grow();
                        d[n] = v;
                    }
                    case INT -> {
                        long v = r.getInteger();
                        if (n > 0 && sorted && t >= ts[n - 1] && l[n - 1] == v) return;
                        grow();
                        l[n] = v;
                    }
                    case BOOL -> {
                        if (r.getSize() < 1) throw new IndexOutOfBoundsException("empty boolean");
                        boolean v = r.getBoolean();
                        if (n > 0 && sorted && t >= ts[n - 1] && b[n - 1] == v) return;
                        grow();
                        b[n] = v;
                    }
                    default -> {
                        ByteBuffer raw = r.getRawBuffer();
                        // compare bytes before decoding: most records repeat the last one
                        if (n > 0 && sorted && t >= ts[n - 1] && lastRaw != null && raw.equals(ByteBuffer.wrap(lastRaw))) return;
                        Object v = decode(r);
                        lastRaw = r.getRaw();
                        grow();
                        o[n] = v;
                    }
                }
            } catch (RuntimeException e) { // a record shorter than its type; counted and reported once
                badRecords++;
                return;
            }
            if (n > 0 && t < ts[n - 1]) sorted = false;
            ts[n] = t;
            size = n + 1;
        }

        private Object decode(DataLogRecord r) {
            Object v = switch (logType) {
                case "string", "json" -> r.getString();
                case "double[]" -> r.getDoubleArray();
                case "float[]" -> r.getFloatArray();
                case "int64[]" -> r.getIntegerArray();
                case "boolean[]" -> r.getBooleanArray();
                case "string[]" -> r.getStringArray();
                default -> r.getRaw();
            };
            if (v instanceof String) {
                Object known = strings.putIfAbsent(v, v);
                if (known != null) v = known;
            }
            return v;
        }

        private void grow() {
            if (size < ts.length) return;
            int cap = ts.length * 2;
            ts = Arrays.copyOf(ts, cap);
            switch (kind) {
                case DOUBLE -> d = Arrays.copyOf(d, cap);
                case INT -> l = Arrays.copyOf(l, cap);
                case BOOL -> b = Arrays.copyOf(b, cap);
                default -> o = Arrays.copyOf(o, cap);
            }
        }

        void report(String file) {
            if (badRecords > 0) {
                LOG.log(System.Logger.Level.WARNING, file + ": skipped " + badRecords + " unreadable " + logType + " records of " + logName);
                badRecords = 0;
            }
        }

        /** Add another column's values (same type); finish() puts them in time order. */
        void append(Column c) {
            if (c.size == 0) return;
            if (size > 0 && c.ts[0] < ts[size - 1]) sorted = false;
            sorted &= c.sorted;
            int n = size + c.size;
            ts = Arrays.copyOf(ts, n);
            System.arraycopy(c.ts, 0, ts, size, c.size);
            switch (kind) {
                case DOUBLE -> { d = Arrays.copyOf(d, n); System.arraycopy(c.d, 0, d, size, c.size); }
                case INT -> { l = Arrays.copyOf(l, n); System.arraycopy(c.l, 0, l, size, c.size); }
                case BOOL -> { b = Arrays.copyOf(b, n); System.arraycopy(c.b, 0, b, size, c.size); }
                default -> { o = Arrays.copyOf(o, n); System.arraycopy(c.o, 0, o, size, c.size); }
            }
            size = n;
        }

        /** Sort by time if needed (stable), drop repeated values, trim the arrays. */
        void finish() {
            strings = null;
            lastRaw = null;
            if (!sorted) {
                Integer[] order = new Integer[size];
                for (int i = 0; i < size; i++) order[i] = i;
                long[] t0 = ts;
                Arrays.sort(order, (x, y) -> Long.compare(t0[x], t0[y])); // ponytail: boxed sort, only for unsorted or merged columns
                long[] nts = new long[size];
                for (int i = 0; i < size; i++) nts[i] = ts[order[i]];
                ts = nts;
                switch (kind) {
                    case DOUBLE -> { double[] a = new double[size]; for (int i = 0; i < size; i++) a[i] = d[order[i]]; d = a; }
                    case INT -> { long[] a = new long[size]; for (int i = 0; i < size; i++) a[i] = l[order[i]]; l = a; }
                    case BOOL -> { boolean[] a = new boolean[size]; for (int i = 0; i < size; i++) a[i] = b[order[i]]; b = a; }
                    default -> { Object[] a = new Object[size]; for (int i = 0; i < size; i++) a[i] = o[order[i]]; o = a; }
                }
                sorted = true;
            }
            int n = 0;
            for (int i = 0; i < size; i++) {
                if (n > 0 && sameValue(n - 1, i)) continue;
                ts[n] = ts[i];
                switch (kind) {
                    case DOUBLE -> d[n] = d[i];
                    case INT -> l[n] = l[i];
                    case BOOL -> b[n] = b[i];
                    default -> o[n] = o[i];
                }
                n++;
            }
            size = n;
            ts = Arrays.copyOf(ts, n);
            switch (kind) {
                case DOUBLE -> d = Arrays.copyOf(d, n);
                case INT -> l = Arrays.copyOf(l, n);
                case BOOL -> b = Arrays.copyOf(b, n);
                default -> o = Arrays.copyOf(o, n);
            }
        }

        private boolean sameValue(int i, int j) {
            return switch (kind) {
                case DOUBLE -> Double.doubleToRawLongBits(d[i]) == Double.doubleToRawLongBits(d[j]);
                case INT -> l[i] == l[j];
                case BOOL -> b[i] == b[j];
                default -> Objects.deepEquals(o[i], o[j]);
            };
        }

        /** Index of the last value at or before t, or -1 if t is before the first. */
        int indexAt(long t) {
            int lo = 0, hi = size - 1, found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (ts[mid] <= t) { found = mid; lo = mid + 1; }
                else hi = mid - 1;
            }
            return found;
        }
    }
}
