package emberfx;

import java.util.List;

/**
 * One user-editable setting: a module setting or a tile option.
 * The settings dialogs are generated from these, so nothing needs its own form code.
 */
public record Setting(String key, String label, Kind kind, String defaultValue, List<String> choices) {
    public enum Kind { TOPIC_READ, TOPIC_WRITE, TEXT, NUMBER, BOOL, CHOICE }

    /** A topic that is only read. */
    public static Setting reads(String key, String label, String topic) { return of(key, label, Kind.TOPIC_READ, topic); }

    /** A topic that is published to the robot. */
    public static Setting writes(String key, String label, String topic) { return of(key, label, Kind.TOPIC_WRITE, topic); }

    public static Setting text(String key, String label, String value) { return of(key, label, Kind.TEXT, value); }

    public static Setting number(String key, String label, double value) { return of(key, label, Kind.NUMBER, fmt(value)); }

    /** A number that may be left blank, e.g. "auto" graph limits. */
    public static Setting optionalNumber(String key, String label) { return of(key, label, Kind.NUMBER, ""); }

    public static Setting bool(String key, String label, boolean value) { return of(key, label, Kind.BOOL, Boolean.toString(value)); }

    /** One of a fixed list; the first choice is the default. */
    public static Setting choice(String key, String label, String... choices) {
        return new Setting(key, label, Kind.CHOICE, choices[0], List.of(choices));
    }

    private static Setting of(String key, String label, Kind kind, String value) { return new Setting(key, label, kind, value, List.of()); }

    private static String fmt(double v) { return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v); }
}
