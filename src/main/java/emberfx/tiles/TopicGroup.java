package emberfx.tiles;

import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.Topic;
import emberfx.NT;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The topics a multi-value tile shows: the topic itself when it's a single value, or every value
 * directly inside it when it's a table (e.g. /SmartDashboard/Motor Temps -> Intake, Feeder, ...).
 * New topics are picked up once a second; entries are sorted by name.
 */
final class TopicGroup {
    /** One value in the group. */
    record Entry(String name, GenericSubscriber sub) {}

    private static final long RESCAN_MS = 1000;

    private final String path;
    private final Function<GenericSubscriber, GenericSubscriber> track;
    private final List<Entry> entries = new ArrayList<>();
    private long lastScan;

    /** @param track the tile's track(...), so subscribers close with the tile */
    TopicGroup(String path, Function<GenericSubscriber, GenericSubscriber> track) {
        this.path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        this.track = track;
    }

    /** True when a rescan found new topics, so the tile can rebuild its rows. */
    boolean refresh() {
        long now = System.currentTimeMillis();
        if (now - lastScan < RESCAN_MS) return false;
        lastScan = now;
        Topic single = NT.instance().getTopic(path);
        if (single.exists()) { // one value, not a table
            if (!entries.isEmpty()) return false;
            entries.add(new Entry(lastPart(path), track.apply(single.genericSubscribe())));
            return true;
        }
        int before = entries.size();
        for (Topic t : NT.instance().getTopics(path + "/")) {
            String name = t.getName().substring(path.length() + 1);
            if (name.startsWith(".") || name.contains("/") || has(name)) continue;
            entries.add(new Entry(name, track.apply(t.genericSubscribe())));
        }
        entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return entries.size() != before;
    }

    List<Entry> entries() { return entries; }

    /** Changes whenever any value in the group changes (sum of last-change times). */
    long changeStamp() {
        long sum = entries.size();
        for (Entry e : entries) sum += e.sub().getLastChange();
        return sum;
    }

    /** The entry's value as a number, or NaN when it isn't one (or hasn't arrived yet). */
    static double number(Entry e) {
        NetworkTableValue v = e.sub().get();
        return switch (v.getType()) {
            case kDouble, kFloat, kInteger -> ((Number) v.getValue()).doubleValue();
            default -> Double.NaN;
        };
    }

    /** The entry's value as a boolean; false when it isn't one. */
    static boolean bool(Entry e) {
        NetworkTableValue v = e.sub().get();
        return v.isBoolean() && v.getBoolean();
    }

    private boolean has(String name) {
        for (Entry e : entries) if (e.name().equals(name)) return true;
        return false;
    }

    private static String lastPart(String p) { return p.substring(p.lastIndexOf('/') + 1); }
}
