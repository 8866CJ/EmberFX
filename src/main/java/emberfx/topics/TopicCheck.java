package emberfx.topics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compares the topics named in the robot code with the topics the robot publishes.
 *
 * Pure logic, no NetworkTables: feed it {@link Topics#fromProject()} and {@link LiveTopics#list()}.
 * Both sides already use full paths (/SmartDashboard/Key, /AdvantageKit/RealOutputs/Key), so paths are
 * only tidied (leading slash, no doubled or trailing slashes) before they are matched.
 */
public final class TopicCheck {
    /** Code topic and the live topic with the same path, whose types disagree. */
    public record Mismatch(FoundTopic code, FoundTopic live) {}

    /**
     * @param missing    in the code, not on the robot (the robot should publish it but doesn't)
     * @param unexpected on the robot, never mentioned in the code
     * @param mismatched in both, with different types
     * @param waiting    the robot only reads these, so the dashboard is the one to write them
     */
    public record Result(List<FoundTopic> missing, List<FoundTopic> unexpected,
                         List<Mismatch> mismatched, List<FoundTopic> waiting) {}

    /** Live topics that WPILib, NetworkTables and AdvantageKit publish themselves; never "unexpected". */
    static final List<String> IGNORED_PREFIXES = List.of(
        "/FMSInfo", "/LiveWindow", "/CameraPublisher", "/Shuffleboard", "/Preferences", "/Metadata",
        "/AdvantageKit/Timestamp", "/AdvantageKit/ApplicationInfo", "/AdvantageKit/DriverStation",
        "/AdvantageKit/SystemStats", "/AdvantageKit/PowerDistribution", "/AdvantageKit/RealOutputs/LoggedRobot");

    private TopicCheck() {}

    public static Result compare(List<FoundTopic> code, List<FoundTopic> live) {
        Map<String, FoundTopic> codeBy = index(code), liveBy = index(live);
        List<FoundTopic> missing = new ArrayList<>(), unexpected = new ArrayList<>(), waiting = new ArrayList<>();
        List<Mismatch> mismatched = new ArrayList<>();
        codeBy.forEach((path, c) -> {
            FoundTopic l = liveBy.get(path);
            if (l == null) {
                (c.robotReads() ? waiting : missing).add(c);
            } else if (typesDiffer(c.type(), l.type())) {
                mismatched.add(new Mismatch(c, l));
            }
        });
        liveBy.forEach((path, l) -> {
            if (!codeBy.containsKey(path) && !ignored(path)) unexpected.add(l);
        });
        return new Result(missing, unexpected, mismatched, waiting);
    }

    /** True for NT/WPILib internals: "$" meta topics, ".schema"/".type" style names, and {@link #IGNORED_PREFIXES}. */
    static boolean ignored(String path) {
        if (path.startsWith("/$") || path.contains("/.") || path.contains("/$")) return true;
        for (String p : IGNORED_PREFIXES) if (path.equals(p) || path.startsWith(p + "/")) return true;
        return false;
    }

    /** Leading slash, no "//", no trailing slash. */
    static String normalizePath(String path) {
        String p = path.replaceAll("/{2,}", "/");
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.startsWith("/") ? p : "/" + p;
    }

    /**
     * Comparable type name: int, float and double are all "number" (NetworkTables converts between them),
     * "json" is "string". Anything else (Sendable types, structs, raw) or unknown gives "" and is never compared.
     */
    static String normalizeType(String type) {
        String t = type.trim().toLowerCase(Locale.ROOT);
        String suffix = t.endsWith("[]") ? "[]" : "";
        if (!suffix.isEmpty()) t = t.substring(0, t.length() - 2);
        String base = switch (t) {
            case "double", "float", "int", "integer", "int64", "long", "number" -> "number";
            case "boolean" -> "boolean";
            case "string", "json" -> "string";
            default -> "";
        };
        return base.isEmpty() ? "" : base + suffix;
    }

    private static boolean typesDiffer(String codeType, String liveType) {
        String a = normalizeType(codeType), b = normalizeType(liveType);
        return !a.isEmpty() && !b.isEmpty() && !a.equals(b);
    }

    /** By normalized path; the first entry for a path wins. Paths in the result are the normalized ones. */
    private static Map<String, FoundTopic> index(List<FoundTopic> topics) {
        Map<String, FoundTopic> by = new LinkedHashMap<>();
        for (FoundTopic t : topics) {
            String path = normalizePath(t.path());
            by.putIfAbsent(path, new FoundTopic(path, t.type(), t.source(), t.robotReads()));
        }
        return by;
    }
}
