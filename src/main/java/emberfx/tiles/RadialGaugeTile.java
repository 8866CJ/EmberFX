package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;

/**
 * A dial like a speedometer: a ring of ticks from min to max that light up to the value,
 * with labels on the major ticks and the value in the middle.
 */
public final class RadialGaugeTile extends CanvasTile {
    static final Color LIT = Color.web("#0ea5e9"), UNLIT = Color.web("#334155"), WARN = Color.web("#dc2626"),
        LABEL = Color.web("#94a3b8"), VALUE = Color.web("#f8fafc");
    private static final double START = 45, STOP = 315; // degrees clockwise from straight down; the gap is at the bottom
    private static final double STEP = 1.5, MAJOR = 67.5; // degrees between ticks, and between the 5 labelled ticks

    private final DoubleSubscriber sub;
    private long stamp = -1;

    public RadialGaugeTile(TileConfig config) {
        super(config);
        sub = track(NT.subDouble(config.topic(), 0.0));
    }

    @Override
    protected boolean needsRedraw() {
        long now = changeStamp(sub);
        boolean changed = now != stamp;
        stamp = now;
        return changed;
    }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double min = config.optionDouble("min"), max = config.optionDouble("max");
        if (!(max > min)) { min = 0; max = 1; }
        double value = sub.get(), warn = config.optionDouble("warnAbove");
        double at = START + (STOP - START) * Math.max(0, Math.min(1, (value - min) / (max - min)));
        Color lit = !Double.isNaN(warn) && value > warn ? WARN : LIT;

        double r = Math.min(w, h * 1.08) / 2 - 2; // the bottom gap lets the dial sit a little taller than the tile
        double cx = w / 2, cy = Math.min(h / 2 + r * 0.06, h - r * 0.75);
        double s = r / 100; // drawn on a 100-unit radius, then scaled

        // A faint wedge from the start to the value.
        g.setFill(lit.deriveColor(0, 1, 1, 0.12));
        g.fillArc(cx - 91 * s, cy - 91 * s, 182 * s, 182 * s, screenAngle(START), -(at - START), ArcType.ROUND);

        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineWidth(Math.max(1, 1.2 * s));
        for (double v = START; v <= STOP + 1e-9; v += STEP) {
            boolean major = Math.abs((v - START) % MAJOR) < 1e-9;
            double inner = (major ? 88 : 92) * s, outer = 99 * s;
            double dx = Math.sin(Math.toRadians(v - 180)), dy = -Math.cos(Math.toRadians(v - 180));
            g.setStroke(v <= at ? lit : UNLIT);
            g.strokeLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer);
        }

        g.setFill(LABEL);
        g.setFont(Font.font(MONO, Math.max(9, 11 * s)));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (double v = START; v <= STOP + 1e-9; v += MAJOR) {
            double dx = Math.sin(Math.toRadians(v - 180)), dy = -Math.cos(Math.toRadians(v - 180));
            g.fillText(tickLabel(min + (max - min) * (v - START) / (STOP - START)), cx + dx * 74 * s, cy + dy * 74 * s);
        }

        g.setFill(VALUE);
        g.setFont(Font.font(MONO, FontWeight.BOLD, Math.max(12, 24 * s)));
        g.fillText(TextTile.number(value, config.option("format")), cx, cy);
    }

    /** Gauge degrees (clockwise from straight down) to JavaFX arc degrees (counterclockwise from 3 o'clock). */
    private static double screenAngle(double v) { return 270 - v; }

    /** Short scale labels: 0, 2.5, 1250, -0.75. */
    static String tickLabel(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s.equals("-0") ? "0" : s;
    }
}
