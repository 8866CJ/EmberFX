package emberfx.topics;

import emberfx.config.Config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Every topic the user can pick: live ones from the robot plus ones found in the linked robot project. */
public final class Topics {
    private static final System.Logger LOG = System.getLogger(Topics.class.getName());
    private static final long SCAN_REUSE_MS = 10_000;
    private static String scannedProject = "";
    private static long scannedAt;
    private static List<FoundTopic> scanned = List.of();

    private Topics() {}

    /** One entry per path, sorted. Live data wins for the type; the code tells whether the robot reads it. */
    public static List<FoundTopic> all() {
        Map<String, FoundTopic> byPath = new TreeMap<>();
        for (FoundTopic t : fromProject()) byPath.put(t.path(), t);
        for (FoundTopic live : LiveTopics.list()) {
            FoundTopic code = byPath.get(live.path());
            byPath.put(live.path(), code == null ? live
                : new FoundTopic(live.path(), live.type(), "robot, " + code.source(), code.robotReads()));
        }
        return new ArrayList<>(byPath.values());
    }

    /**
     * Topics in the linked robot project's code, or none if no project is linked or it can't be read.
     * A scan is reused for a few seconds, because the Topics panel and Browse ask often and a big folder is slow.
     */
    public static synchronized List<FoundTopic> fromProject() {
        String project = Config.robotProject();
        if (project.isEmpty()) return List.of();
        long now = System.currentTimeMillis();
        if (project.equals(scannedProject) && now - scannedAt < SCAN_REUSE_MS) return scanned;
        try {
            scanned = List.copyOf(ProjectScanner.scan(Path.of(project)));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Couldn't scan the robot project at " + project, e);
            scanned = List.of();
        }
        scannedProject = project;
        scannedAt = now;
        return scanned;
    }

    /** Scan the project again on the next request, e.g. after Refresh or linking a project. */
    public static synchronized void rescanProject() {
        scannedAt = 0;
    }
}
