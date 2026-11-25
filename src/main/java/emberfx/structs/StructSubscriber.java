package emberfx.structs;

import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTableType;
import edu.wpi.first.networktables.NetworkTableValue;

import java.util.List;

/**
 * Latest decoded value of one struct topic ("struct:Name" or "struct:Name[]"). Decodes only when
 * the topic's last-change time moves, or when a schema arrived since a decode failed for lack of one.
 * Call from one thread (the JavaFX thread); wrap in a tile's track(...) to close with the tile.
 */
public final class StructSubscriber implements AutoCloseable {
    private final StructRegistry registry;
    private final GenericSubscriber sub;
    private long seenChange = -1;
    private int failedAtVersion = -1;
    private Object latest;

    public StructSubscriber(NetworkTableInstance inst, StructRegistry registry, String topic) {
        this.registry = registry;
        this.sub = inst.getTopic(topic).genericSubscribe();
    }

    /** Latest value if the topic is a single struct, else null (also null with no data or unknown schema). */
    public StructValue get() {
        refresh();
        return latest instanceof StructValue s ? s : null;
    }

    /** Latest value if the topic is a struct array, else null. */
    @SuppressWarnings("unchecked")
    public List<StructValue> getArray() {
        refresh();
        return latest instanceof List<?> l ? (List<StructValue>) l : null;
    }

    private void refresh() {
        long change = sub.getLastChange();
        boolean retry = failedAtVersion >= 0 && failedAtVersion != registry.version();
        if (change == seenChange && !retry) return;
        NetworkTableValue v = sub.get();
        if (v.getType() != NetworkTableType.kRaw) {
            latest = null;
            return; // no data yet; seenChange stays so we look again next frame
        }
        Object decoded = registry.decodeTyped(sub.getTopic().getTypeString(), v.getRaw());
        latest = decoded;
        seenChange = change;
        failedAtVersion = decoded == null ? registry.version() : -1;
    }

    /** When the topic last got a value, for cheap change checks. */
    public long lastChange() {
        return sub.getLastChange();
    }

    @Override
    public void close() {
        sub.close();
    }
}
