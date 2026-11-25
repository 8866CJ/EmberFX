package emberfx.structs;

import java.util.List;
import java.util.Locale;

/** Compact one-line text for decoded structs, for the Text tile. */
public final class StructFormat {
    private static final int MAX_ITEMS = 8;

    private StructFormat() {}

    /** E.g. "Pose2d(x=1.20, y=3.40, rot=90.0 deg)"; other structs list their fields. */
    public static String toText(StructValue v) {
        if (v == null) return "--";
        return switch (v.type()) {
            case "Pose2d" -> {
                double[] p = Poses.pose2d(v);
                yield p == null ? generic(v)
                    : String.format(Locale.ROOT, "Pose2d(x=%.2f, y=%.2f, rot=%.1f deg)", p[0], p[1], p[2]);
            }
            case "Translation2d" -> String.format(Locale.ROOT, "Translation2d(x=%.2f, y=%.2f)",
                v.number("x"), v.number("y"));
            case "Rotation2d" -> String.format(Locale.ROOT, "Rotation2d(%.1f deg)", Poses.rotation2dDegrees(v));
            default -> generic(v);
        };
    }

    /** E.g. "[3 x Pose2d]". */
    public static String toText(List<StructValue> array) {
        if (array == null) return "--";
        String type = array.isEmpty() ? "?" : array.get(0).type();
        return "[" + array.size() + " x " + type + "]";
    }

    private static String generic(StructValue v) {
        StringBuilder sb = new StringBuilder(v.type()).append('(');
        for (int i = 0; i < v.names().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(v.names().get(i)).append('=');
            value(sb, v.values().get(i));
        }
        return sb.append(')').toString();
    }

    private static void value(StringBuilder sb, Object o) {
        if (o instanceof Double d) {
            sb.append(String.format(Locale.ROOT, "%.2f", d));
        } else if (o instanceof StructValue s) {
            sb.append(toText(s));
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size() && i < MAX_ITEMS; i++) {
                if (i > 0) sb.append(", ");
                value(sb, l.get(i));
            }
            if (l.size() > MAX_ITEMS) sb.append(", ...");
            sb.append(']');
        } else {
            sb.append(o);
        }
    }
}
