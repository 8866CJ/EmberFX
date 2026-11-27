package emberfx.replay;

import edu.wpi.first.networktables.GenericPublisher;
import edu.wpi.first.networktables.NetworkTableInstance;

import java.util.Arrays;

/**
 * Plays a {@link LogIndex} into a NetworkTableInstance: {@link #seek} makes every topic hold its
 * value as of a log time, by publishing locally. Local subscribers (every tile) see local publishers'
 * values, so the dashboard replays unchanged while its client is stopped.
 *
 * <p>A topic is published only once the replay time reaches its first value, and unpublished again
 * when seeking back before it. Values are set with NetworkTables timestamp 0, i.e. "now": tiles see
 * an update at the moment it is replayed, not the original log time (log time is FPGA time, which
 * means nothing to the local instance).
 *
 * <p>The log's schemas are registered with addSchema in the constructor, so struct tiles decode.
 * NetworkTables has no way to remove those, so they stay after {@link #close()}; registering the
 * same name again is ignored.
 *
 * <p>Not thread-safe: call seek and close from one thread (the FX thread).
 */
public final class Replayer implements AutoCloseable {
    /** Steps a cursor walks forward before falling back to binary search. */
    private static final int MAX_STEPS = 16;

    private final LogIndex.Column[] cols;
    private final NetworkTableInstance inst;
    private final int[] shown;
    private final GenericPublisher[] pubs;

    public Replayer(LogIndex index, NetworkTableInstance inst) {
        this.cols = index.columns;
        this.inst = inst;
        shown = new int[cols.length];
        Arrays.fill(shown, -1);
        pubs = new GenericPublisher[cols.length];
        for (LogIndex.Schema s : index.schemas) inst.addSchema(s.name(), s.type(), s.data());
    }

    /** Make every topic hold its value as of this time. Cheap when called every frame moving forward; also handles jumps backward. */
    public void seek(long micros) {
        for (int i = 0; i < cols.length; i++) {
            LogIndex.Column c = cols[i];
            int cur = shown[i];
            int idx = cur;
            if (cur + 1 < c.size && c.ts[cur + 1] <= micros) {
                int steps = 0;
                do idx++; while (idx + 1 < c.size && c.ts[idx + 1] <= micros && ++steps < MAX_STEPS);
                if (idx + 1 < c.size && c.ts[idx + 1] <= micros) idx = c.indexAt(micros);
            } else if (cur >= 0 && c.ts[cur] > micros) {
                idx = c.indexAt(micros);
            }
            if (idx == cur) continue;
            shown[i] = idx;
            if (idx < 0) {
                pubs[i].close();
                pubs[i] = null;
            } else {
                publish(i, c, idx);
            }
        }
    }

    private void publish(int i, LogIndex.Column c, int idx) {
        GenericPublisher p = pubs[i];
        if (p == null) {
            p = inst.getTopic(c.topic).genericPublish(ntType(c.logType));
            pubs[i] = p;
        }
        switch (c.kind) {
            case DOUBLE -> {
                if (c.logType.equals("float")) p.setFloat((float) c.d[idx], 0);
                else p.setDouble(c.d[idx], 0);
            }
            case INT -> p.setInteger(c.l[idx], 0);
            case BOOL -> p.setBoolean(c.b[idx], 0);
            default -> {
                Object v = c.o[idx];
                if (v instanceof String s) p.setString(s, 0);
                else if (v instanceof double[] a) p.setDoubleArray(a, 0);
                else if (v instanceof float[] a) p.setFloatArray(a, 0);
                else if (v instanceof long[] a) p.setIntegerArray(a, 0);
                else if (v instanceof boolean[] a) p.setBooleanArray(a, 0);
                else if (v instanceof String[] a) p.setStringArray(a, 0);
                else p.setRaw((byte[]) v, 0);
            }
        }
    }

    /** NetworkTables type string for a log type: the same except int64 is "int". */
    static String ntType(String logType) {
        return switch (logType) {
            case "int64" -> "int";
            case "int64[]" -> "int[]";
            default -> logType;
        };
    }

    /** Unpublish everything it published. */
    @Override
    public void close() {
        for (int i = 0; i < pubs.length; i++) {
            if (pubs[i] != null) pubs[i].close();
            pubs[i] = null;
            shown[i] = -1;
        }
    }
}
