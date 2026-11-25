package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;

import java.util.Locale;

import static emberfx.tiles.RadialGaugeTile.LABEL;
import static emberfx.tiles.RadialGaugeTile.UNLIT;
import static emberfx.tiles.RadialGaugeTile.VALUE;

/**
 * A compass dial for a WPILib gyro: a ring with ticks every 10 degrees, labels at 0/90/180/270 and a
 * needle at the heading, with the heading in degrees below. WPILib gyros count clockwise as positive,
 * which is how the dial turns by default; the option flips it for counterclockwise-positive gyros.
 * Reads the "Gyro" sendable's "Value" in degrees (ADXRS450_Gyro, AnalogGyro):
 *
 * <pre>
 * SmartDashboard.putData("Gyro", gyro);
 * </pre>
 */
public final class GyroTile extends CanvasTile {
    private static final int TICK = 10, LABEL_STEP = 90;

    private final DoubleSubscriber sub;
    private long lastTenths = Long.MIN_VALUE;
    private double heading = Double.NaN;
    private boolean ccw, lastCcw, dirty = true;

    public GyroTile(TileConfig config) {
        super(config);
        sub = track(NT.subDouble(config.topic() + "/Value", Double.NaN));
    }

    @Override
    public void update() {
        double v = sub.get();
        ccw = config.optionBool("counterclockwise");
        long tenths = Double.isNaN(v) ? Long.MIN_VALUE + 1 : Math.round(v * 10);
        dirty = tenths != lastTenths || ccw != lastCcw;
        if (dirty) {
            lastTenths = tenths;
            lastCcw = ccw;
            heading = v;
        }
        super.update();
    }

    /** True only when the heading (to 0.1 degree) or the direction option changed. */
    @Override
    protected boolean needsRedraw() { return dirty; }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double textH = 30, r = Math.min(w / 2, (h - textH) / 2) - 14, cx = w / 2, cy = (h - textH) / 2;
        if (r < 20) return;
        int dir = ccw ? -1 : 1;

        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineWidth(1.5);
        for (int d = 0; d < 360; d += TICK) {
            boolean major = d % LABEL_STEP == 0;
            double a = Math.toRadians(d * dir), sin = Math.sin(a), cos = -Math.cos(a), inner = r * (major ? 0.82 : 0.9);
            g.setStroke(major ? LABEL : UNLIT);
            g.strokeLine(cx + sin * inner, cy + cos * inner, cx + sin * r, cy + cos * r);
        }
        g.setFill(LABEL);
        g.setFont(DisplayStyle.Fonts.SMALL);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (int d = 0; d < 360; d += LABEL_STEP) {
            double a = Math.toRadians(d * dir);
            g.fillText(Integer.toString(d), cx + Math.sin(a) * (r + 9), cy - Math.cos(a) * (r + 9));
        }

        if (!Double.isNaN(heading)) {
            double a = Math.toRadians(heading * dir), sin = Math.sin(a), cos = -Math.cos(a);
            g.setStroke(DisplayStyle.HEADING);
            g.setLineWidth(3);
            g.strokeLine(cx - sin * r * 0.2, cy - cos * r * 0.2, cx + sin * r * 0.75, cy + cos * r * 0.75);
            g.setFill(DisplayStyle.HEADING);
            g.fillOval(cx - 4, cy - 4, 8, 8);
        }
        g.setFill(VALUE);
        g.setFont(DisplayStyle.Fonts.BIG);
        g.fillText(Double.isNaN(heading) ? "--" : String.format(Locale.ROOT, "%.1f\u00b0", normalize(heading)), cx, h - textH / 2);
    }

    /** Any angle in degrees as 0 (inclusive) to 360 (exclusive). */
    static double normalize(double degrees) {
        double d = degrees % 360;
        if (d < 0) d += 360;
        return d >= 360 ? 0 : d + 0.0; // + 0.0 turns -0.0 into 0.0
    }
}
