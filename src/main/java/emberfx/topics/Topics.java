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

    /** Topics in the linked robot project's code, or none if no project is linked or it can't be read. */
    public static List<FoundTopic> fromProject() {
        String project = Config.robotProject();
        if (project.isEmpty()) return List.of();
        try {
            return ProjectScanner.scan(Path.of(project));
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }
}
