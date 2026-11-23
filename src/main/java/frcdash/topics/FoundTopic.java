package frcdash.topics;

/**
 * A topic the user might want a tile for.
 *
 * @param path       full NetworkTables path, e.g. /SmartDashboard/Shooter/RPM
 * @param type       "double", "boolean", "string", "double[]", ... or a Sendable type such as
 *                   "String Chooser" or "Command". Empty when unknown.
 * @param source     where it was found: "robot" (live) or "File.java:42" (robot code)
 * @param robotReads true when the robot code reads this value (SmartDashboard.getNumber, ...),
 *                   so the dashboard should offer a control that writes it
 */
public record FoundTopic(String path, String type, String source, boolean robotReads) {
    /** A readable default tile title: the path without its usual table prefix, e.g. "Intake Enabled". */
    public String name() {
        String p = path;
        for (String prefix : new String[] {"/SmartDashboard/", "/AdvantageKit/RealOutputs/", "/Shuffleboard/", "/CameraPublisher/"}) {
            if (p.startsWith(prefix)) { p = p.substring(prefix.length()); break; }
        }
        return p.replace('/', ' ').trim();
    }
}
