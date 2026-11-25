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
 * Current on every channel of a CTRE PDP (16 channels) or REV PDH (24) as a grid of small bars, with
 * battery voltage and total current in the title bar. Bars turn red above the warning current.
 * Reads a WPILib {@code PowerDistribution} sendable (keys Chan0.., Voltage, TotalCurrent):
 *
 * <pre>
 * SmartDashboard.putData("PDH", new PowerDistribution());
 * </pre>
 */
public final class PowerDistributionTile extends CanvasTile {
    private static final int MAX_CHANNELS = 24, VOLTAGE = MAX_CHANNELS, TOTAL = MAX_CHANNELS + 1;
    private static final String[] KEYS = new String[MAX_CHANNELS + 2];

    static {
        for (int i = 0; i < MAX_CHANNELS; i++) KEYS[i] = "Chan" + i;
        KEYS[VOLTAGE] = "Voltage";
        KEYS[TOTAL] = "TotalCurrent";
    }

    private final DoubleSubscriber[] subs;
    private final double[] last = new double[KEYS.length];
    private boolean dirty = true;

    public PowerDistributionTile(TileConfig config) {
        super(config);
        subs = DisplayStyle.subscribe(config, this::track, KEYS);
        Arrays.fill(last, Double.NaN);
    }

    @Override
    public void update() {
        dirty = DisplayStyle.sync(subs, last);
        if (dirty) {
            setHeaderValue((Double.isNaN(last[VOLTAGE]) ? "" : String.format(Locale.ROOT, "%.1f V", last[VOLTAGE]))
                + (Double.isNaN(last[TOTAL]) ? "" : String.format(Locale.ROOT, "   %.0f A", last[TOTAL])));
        }
        super.update();
    }

    /** True only when a published value changed since the last frame. */
    @Override
    protected boolean needsRedraw() { return dirty; }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        int n = channelCount(last, MAX_CHANNELS);
        if (n == 0) return;
        double full = config.optionDouble("maxCurrent"), warn = config.optionDouble("warnAbove");
        if (!(full > 0)) full = 40;
        int cols = columns(n, w, h), rows = (n + cols - 1) / cols;
        double cw = w / cols, ch = h / rows, pad = Math.min(14, cw * 0.16), barH = Math.max(3, Math.min(6, ch * 0.18));
        g.setTextBaseline(VPos.CENTER);
        g.setFont(DisplayStyle.Fonts.SMALL);
        for (int i = 0; i < n; i++) {
            double v = last[i], x = (i % cols) * cw + pad / 2, y = (i / cols) * ch, bw = cw - pad;
            double by = y + ch - barH - 3, ty = y + (by - y) / 2;
            g.setFill(LABEL);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(Integer.toString(i), x, ty);
            g.setFill(UNLIT);
            g.fillRect(x, by, bw, barH);
            if (Double.isNaN(v)) continue;
            g.setFill(VALUE);
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(Long.toString(Math.round(v)), x + bw, ty);
            if (v > 0) {
                g.setFill(!Double.isNaN(warn) && v > warn ? WARN : LIT);
                g.fillRect(x, by, bw * Math.min(1, v / full), barH);
            }
        }
    }

    /** Channels to show: one more than the highest channel that has published a number. */
    static int channelCount(double[] currents, int max) {
        for (int i = Math.min(max, currents.length) - 1; i >= 0; i--) if (!Double.isNaN(currents[i])) return i + 1;
        return 0;
    }

    /** Columns that give roughly 1.7:1 cells in a w x h area. */
    static int columns(int n, double w, double h) {
        int cols = (int) Math.round(Math.sqrt(n * Math.max(1, w) / Math.max(1, h) / 1.7));
        return Math.max(1, Math.min(n, cols));
    }
}
