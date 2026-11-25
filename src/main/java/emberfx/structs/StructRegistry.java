package emberfx.structs;

import edu.wpi.first.networktables.NetworkTableEvent;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTableType;
import edu.wpi.first.networktables.NetworkTableValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Struct schemas by name (e.g. "Pose2d"), and decoding of little-endian struct bytes with them.
 *
 * Threading: schemas are added from NetworkTables listener threads (or tests) and read from the
 * JavaFX thread. All state is in concurrent maps and {@link #version()}, so no extra locking is
 * needed; a schema added mid-decode is simply seen on the next call.
 */
public final class StructRegistry {
    private static final System.Logger LOG = System.getLogger(StructRegistry.class.getName());
    private static final String SCHEMA_PREFIX = "/.schema/struct:";
    private static final String STRUCT_PREFIX = "struct:";

    private final Map<String, StructSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, Integer> sizes = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger();

    /** Changes every time a schema is added; lets callers retry a decode that lacked one. */
    public int version() {
        return version.get();
    }

    /** The schema for a struct name, or null if not seen yet. */
    public StructSchema schema(String name) {
        return schemas.get(name);
    }

    /** Parse and add a schema. Returns false (and logs) if the text is invalid. */
    public boolean addSchema(String name, String text) {
        try {
            schemas.put(name, StructSchema.parse(name, text));
        } catch (IllegalArgumentException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring bad struct schema " + name, e);
            return false;
        }
        sizes.clear();
        version.incrementAndGet();
        return true;
    }

    /**
     * Fill from the robot: listens to "/.schema/struct:*" topics, including ones already published.
     * Callbacks run on a NetworkTables thread. Close the result to stop.
     */
    public AutoCloseable listen(NetworkTableInstance inst) {
        int handle = inst.addListener(new String[] {SCHEMA_PREFIX},
            EnumSet.of(NetworkTableEvent.Kind.kValueAll, NetworkTableEvent.Kind.kImmediate), event -> {
                if (event.valueData == null) return;
                NetworkTableValue v = event.valueData.value;
                if (v.getType() != NetworkTableType.kRaw) return;
                String topic = event.valueData.getTopic().getName();
                addSchema(topic.substring(SCHEMA_PREFIX.length()), new String(v.getRaw(), StandardCharsets.UTF_8));
            });
        return () -> inst.removeListener(handle);
    }

    /** Byte size of one struct, or -1 if its schema (or a nested one) is unknown. */
    public int size(String name) {
        Integer cached = sizes.get(name);
        if (cached != null) return cached;
        StructSchema s = schemas.get(name);
        if (s == null) return -1;
        int size = s.size(schemas::get);
        if (size >= 0) sizes.put(name, size);
        return size;
    }

    /**
     * Decode a raw NT value by its topic type string: "struct:Name" gives a StructValue,
     * "struct:Name[]" a List of StructValue. Null if the type isn't a struct, the schema is
     * unknown, or the byte length doesn't fit.
     */
    public Object decodeTyped(String typeString, byte[] data) {
        if (typeString == null || !typeString.startsWith(STRUCT_PREFIX)) return null;
        String name = typeString.substring(STRUCT_PREFIX.length());
        if (name.endsWith("[]")) return decodeArray(name.substring(0, name.length() - 2), data);
        return decode(name, data);
    }

    /** Decode exactly one struct; null if unknown schema or wrong length. */
    public StructValue decode(String name, byte[] data) {
        int size = size(name);
        if (size < 0 || data.length != size) return null;
        return decodeAt(schemas.get(name), data, 0);
    }

    /** Decode a packed array of structs (byte length / struct size); null if unknown schema or ragged. */
    public List<StructValue> decodeArray(String name, byte[] data) {
        int size = size(name);
        if (size <= 0 || data.length % size != 0) return null;
        StructSchema schema = schemas.get(name);
        List<StructValue> out = new ArrayList<>(data.length / size);
        for (int off = 0; off < data.length; off += size) out.add(decodeAt(schema, data, off));
        return List.copyOf(out);
    }

    private StructValue decodeAt(StructSchema schema, byte[] data, int base) {
        List<String> names = new ArrayList<>(schema.fields().size());
        List<Object> values = new ArrayList<>(schema.fields().size());
        StructSchema.Cursor c = new StructSchema.Cursor();
        for (StructSchema.Field f : schema.fields()) {
            names.add(f.name());
            if (f.bitWidth() > 0) {
                int shift = c.bits(f);
                values.add(bitfield(f, readUnsigned(data, base + c.unit, c.unitBytes), shift)); // read the whole shared unit
                continue;
            }
            int each = f.kind() == StructSchema.Kind.STRUCT ? size(f.structName()) : f.kind().size;
            int count = Math.max(1, f.arrayLength());
            int off = base + c.plain(each * count);
            if (f.kind() == StructSchema.Kind.CHAR && f.arrayLength() > 0) {
                values.add(string(data, off, f.arrayLength()));
            } else if (f.arrayLength() > 0) {
                List<Object> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) items.add(read(f, data, off + i * each));
                values.add(List.copyOf(items));
            } else {
                values.add(read(f, data, off));
            }
        }
        return new StructValue(schema.name(), names, values);
    }

    private Object read(StructSchema.Field f, byte[] data, int off) {
        StructSchema.Kind k = f.kind();
        return switch (k) {
            case STRUCT -> decodeAt(schemas.get(f.structName()), data, off);
            case BOOL -> data[off] != 0;
            case CHAR -> string(data, off, 1);
            case FLOAT -> (double) Float.intBitsToFloat((int) readUnsigned(data, off, 4));
            case DOUBLE -> Double.longBitsToDouble(readUnsigned(data, off, 8));
            default -> named(f, k.signed ? signExtend(readUnsigned(data, off, k.size), k.size * 8)
                : readUnsigned(data, off, k.size));
        };
    }

    private static Object bitfield(StructSchema.Field f, long unit, int shift) {
        long raw = unit >>> shift;
        if (f.bitWidth() < 64) raw &= (1L << f.bitWidth()) - 1;
        if (f.kind() == StructSchema.Kind.BOOL) return raw != 0;
        return named(f, f.kind().signed ? signExtend(raw, f.bitWidth()) : raw);
    }

    private static Object named(StructSchema.Field f, long value) {
        String name = f.enumValues().get(value);
        return name != null ? name : (Object) value;
    }

    private static long signExtend(long v, int bits) {
        int s = 64 - bits;
        return (v << s) >> s;
    }

    private static long readUnsigned(byte[] d, int off, int size) {
        long v = 0;
        for (int i = size - 1; i >= 0; i--) v = (v << 8) | (d[off + i] & 0xFFL);
        return v;
    }

    private static String string(byte[] d, int off, int len) {
        int end = off + len;
        while (end > off && d[end - 1] == 0) end--;
        return new String(d, off, end - off, StandardCharsets.UTF_8);
    }
}
