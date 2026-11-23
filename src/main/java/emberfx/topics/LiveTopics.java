package emberfx.topics;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.TopicInfo;
import emberfx.NT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Topics the connected robot is publishing right now.
 *
 * WPILib Sendables (choosers, commands, Field2d, ...) are a folder of topics with a ".type" entry.
 * Each one is listed once, as the folder, with that type, and its inner topics are hidden.
 * CameraServer cameras are listed the same way, with the type "Camera".
 */
public final class LiveTopics {
    private LiveTopics() {}

    public static List<FoundTopic> list() {
        NetworkTableInstance inst = NT.instance();
        Map<String, String> sendables = new HashMap<>(); // folder -> Sendable type
        List<TopicInfo> all = new ArrayList<>();
        for (TopicInfo t : inst.getTopicInfo()) {
            if (t.name.startsWith("$")) continue; // NetworkTables' own meta topics
            if (t.name.endsWith("/.type")) {
                String folder = t.name.substring(0, t.name.length() - "/.type".length());
                sendables.put(folder, inst.getEntry(t.name).getString(""));
            }
            // CameraServer has no ".type"; each camera is a folder under /CameraPublisher with a "streams" list.
            if (t.name.startsWith("/CameraPublisher/") && t.name.endsWith("/streams")) {
                sendables.put(t.name.substring(0, t.name.length() - "/streams".length()), "Camera");
            }
            all.add(t);
        }

        List<FoundTopic> out = new ArrayList<>();
        sendables.forEach((folder, type) -> out.add(new FoundTopic(folder, type, "robot", false)));
        for (TopicInfo t : all) {
            if (t.name.contains("/.") || insideSendable(t.name, sendables)) continue;
            out.add(new FoundTopic(t.name, t.typeStr, "robot", false));
        }
        return out;
    }

    private static boolean insideSendable(String path, Map<String, String> sendables) {
        for (String folder : sendables.keySet()) if (path.startsWith(folder + "/")) return true;
        return false;
    }
}
