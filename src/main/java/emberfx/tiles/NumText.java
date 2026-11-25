package emberfx.tiles;

import java.math.BigDecimal;
import java.math.MathContext;

/** Number to and from field text, shared by the editable tiles. */
final class NumText {
    private NumText() {}

    /** NaN when blank or not a finite number. */
    static double parse(String text) {
        try {
            double v = Double.parseDouble(text.trim());
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** A number as short plain text: 0.1 not 0.1000000000000000055, 3 not 3.0, no exponent. "" for NaN. */
    static String format(double v) {
        if (Double.isNaN(v)) return "";
        if (Double.isInfinite(v)) return v > 0 ? "inf" : "-inf"; // e.g. a PIDController's default izone
        BigDecimal d = new BigDecimal(v).round(new MathContext(8)).stripTrailingZeros();
        return d.signum() == 0 ? "0" : d.toPlainString();
    }

    /** Equal as numbers when both parse (so "0.10" equals "0.1"), else as trimmed text. */
    static boolean same(String a, String b, boolean numeric) {
        if (numeric) {
            double x = parse(a), y = parse(b);
            if (!Double.isNaN(x) && !Double.isNaN(y)) return x == y;
        }
        return a.trim().equals(b.trim());
    }
}
