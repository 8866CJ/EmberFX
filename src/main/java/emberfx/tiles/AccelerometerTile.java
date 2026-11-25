package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.TextAlignment;

import java.util.Arrays;
import java.util.Locale;

import static emberfx.tiles.RadialGaugeTile.LABEL;
import static emberfx.tiles.RadialGaugeTile.LIT;
import static emberfx.tiles.RadialGaugeTile.UNLIT;
import static emberfx.tiles.RadialGaugeTile.VALUE;
import static emberfx.tiles.RadialGaugeTile.WARN;

/**
 * One bar per axis, growing left or right of zero out to the range. Shows X, Y and Z from a
 * "3AxisAccelerometer" (BuiltInAccelerometer, ADXL345) or a single bar from an "Accelerometer"
 * (AnalogAccelerometer, key "Value"). A bar turns red once it passes the range.
 *
 * <pre>
 * SmartDashboard.putData("Accel", new BuiltInAccelerometer());
 * </pre>
 */
public final class AccelerometerTile extends CanvasTile {
    private static final String[] KEYS = {"X", "Y", "Z", "Value"};
    private static final int VALUE_KEY = 3;

    private final DoubleSubscriber[] subs;
    private final double[] last = new double[KEYS.length];
    private boolean dirty = true;

    public AccelerometerTile(TileConfig config) {
        super(config);
        subs = DisplayStyle.subscribe(config, this::track, KEYS);
        Arrays.fill(last, Double.NaN);
    }

    @Override
    public void update() {
        dirty = DisplayStyle.sync(subs, last);
        super.update();
    }

    /** True only when an axis value changed since the last frame. */
    @Override
    protected boolean needsRedraw() { return dirty; }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double range = config.optionDouble("range");
        if (!(range > 0)) range = 2;
        int first = 0, count = 3;
        if (Double.isNaN(last[0]) && Double.isNaN(last[1]) && Double.isNaN(last[2])) { // single-axis sensor
            first = VALUE_KEY;
            count = 1;
        }
        double labelW = 24, valueW = 54, bx = labelW, bw = w - labelW - valueW, rowH = h / count, barH = Math.min(16, rowH - 8);
        g.setTextBaseline(VPos.CENTER);
        g.setFont(DisplayStyle.Fonts.MONO);
        for (int i = 0; i < count; i++) {
            double v = last[first + i], cy = rowH * i + rowH / 2;
            g.setFill(LABEL);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(count == 1 ? "" : KEYS[first + i], 0, cy);
            g.setFill(UNLIT);
            g.fillRect(bx, cy - barH / 2, bw, barH);
            g.setFill(LABEL);
            g.fillRect(bx + bw / 2 - 0.5, cy - barH / 2 - 3, 1, barH + 6); // zero line
            if (Double.isNaN(v)) continue;
            double f = DisplayStyle.fraction(v, range);
            g.setFill(Math.abs(v) > range ? WARN : LIT);
            g.fillRect(f >= 0 ? bx + bw / 2 : bx + bw / 2 + f * bw / 2, cy - barH / 2, Math.abs(f) * bw / 2, barH);
            g.setFill(VALUE);
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(String.format(Locale.ROOT, "%.2f g", v), w, cy);
        }
    }
}
