package emberfx.topics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds topic names in a team's robot code, so tiles can be set up before the robot is on.
 *
 * Recognized (string literals only, so a key built from a constant isn't found):
 *   SmartDashboard.putNumber/Boolean/String/...("Key", ...)  -> /SmartDashboard/Key
 *   SmartDashboard.getNumber/setDefaultNumber/...("Key", ...) -> /SmartDashboard/Key, read by the robot
 *   SmartDashboard.putData("Key", ...)                       -> /SmartDashboard/Key (a Sendable)
 *   Logger.recordOutput("Key", ...)                          -> /AdvantageKit/RealOutputs/Key
 *   getDoubleTopic("/Full/Path"), getTopic("/Full/Path"), ... -> /Full/Path
 */
public final class ProjectScanner {
    private static final Set<String> SKIP_DIRS = Set.of("build", "bin", ".gradle", ".git", ".vscode", "node_modules");

    private static final Pattern SMARTDASHBOARD = Pattern.compile(
        "SmartDashboard\\s*\\.\\s*(put|get|setDefault)(Number|Boolean|String|NumberArray|BooleanArray|StringArray|Data)\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern ADVANTAGEKIT = Pattern.compile("Logger\\s*\\.\\s*recordOutput\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern TOPIC = Pattern.compile(
        "get(Double|Boolean|String|Integer|Float|DoubleArray|BooleanArray|StringArray|IntegerArray|FloatArray)?Topic\\s*\\(\\s*\"(/[^\"]+)\"");

    private ProjectScanner() {}

    /** Every topic found under the project folder, one entry per path. */
    public static List<FoundTopic> scan(Path project) throws IOException {
        Map<String, FoundTopic> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(project)) {
            for (Path file : files.filter(ProjectScanner::isSource).toList()) scanFile(file, found);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return new ArrayList<>(found.values());
    }

    private static boolean isSource(Path p) {
        for (Path part : p) if (SKIP_DIRS.contains(part.toString())) return false;
        String name = p.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".kt");
    }

    /** Public for tests: scan one file's text. */
    public static void scanText(String text, String fileName, Map<String, FoundTopic> found) {
        Matcher m = SMARTDASHBOARD.matcher(text);
        while (m.find()) {
            boolean reads = !m.group(1).equals("put");
            add(found, "/SmartDashboard/" + m.group(3), smartDashboardType(m.group(2)), where(fileName, text, m.start()), reads);
        }
        m = ADVANTAGEKIT.matcher(text);
        while (m.find()) {
            String key = m.group(1).startsWith("/") ? m.group(1).substring(1) : m.group(1);
            add(found, "/AdvantageKit/RealOutputs/" + key, "", where(fileName, text, m.start()), false);
        }
        m = TOPIC.matcher(text);
        while (m.find()) add(found, m.group(2), topicType(m.group(1)), where(fileName, text, m.start()), false);
    }

    private static void scanFile(Path file, Map<String, FoundTopic> found) throws IOException {
        scanText(Files.readString(file), file.getFileName().toString(), found);
    }

    /** Merge: a key that is both written and read counts as read; a known type beats an unknown one. */
    private static void add(Map<String, FoundTopic> found, String path, String type, String source, boolean reads) {
        FoundTopic old = found.get(path);
        if (old != null) {
            type = old.type().isEmpty() ? type : old.type();
            source = old.source();
            reads |= old.robotReads();
        }
        found.put(path, new FoundTopic(path, type, source, reads));
    }

    private static String where(String fileName, String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) if (text.charAt(i) == '\n') line++;
        return fileName + ":" + line;
    }

    private static String smartDashboardType(String kind) {
        return switch (kind) {
            case "Number" -> "double";
            case "Boolean" -> "boolean";
            case "String" -> "string";
            case "NumberArray" -> "double[]";
            case "BooleanArray" -> "boolean[]";
            case "StringArray" -> "string[]";
            default -> ""; // putData: some Sendable, type known only once the robot runs
        };
    }

    private static String topicType(String kind) {
        if (kind == null) return "";
        String base = kind.replace("Array", "");
        String t = switch (base) {
            case "Double" -> "double";
            case "Boolean" -> "boolean";
            case "String" -> "string";
            case "Integer" -> "int";
            case "Float" -> "float";
            default -> "";
        };
        return kind.endsWith("Array") ? t + "[]" : t;
    }
}
