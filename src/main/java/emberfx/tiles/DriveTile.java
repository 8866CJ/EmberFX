package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;

import java.util.Arrays;

/**
 * Shared drawing for the drive-base tiles: a chassis from above, front up, with a block per wheel
 * and an arrow per wheel as long as its motor output (backwards points down). Subclasses give the
 * wheel keys and where each wheel sits.
 */
abstract class DriveTile extends CanvasTile {
    private final DoubleSubscriber[] subs;
    private final double[] last;
    private final double[][] wheels; // {x, y, length} in units of half the drawing size, front is -y
    private final int[] rollers; // per wheel: 0 plain, +1 or -1 for the slant of mecanum rollers
    private boolean dirty = true;

    DriveTile(TileConfig config, String[] keys, double[][] wheels, int[] rollers) {
        super(config);
        this.subs = DisplayStyle.subscribe(config, this::track, keys);
        this.last = new double[keys.length];
        Arrays.fill(last, Double.NaN);
        this.wheels = wheels;
        this.rollers = rollers;
    }

    @Override
    public void update() {
        dirty = DisplayStyle.sync(subs, last);
        super.update();
    }

    /** True only when a wheel speed changed since the last frame. */
    @Override
    protected boolean needsRedraw() { return dirty; }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double full = config.optionDouble("maxSpeed") > 0 ? config.optionDouble("maxSpeed") : 1;
        double u = Math.min(w, h) / 2 * 0.85, ww = u * 0.22;
        g.save();
        g.translate(w / 2, h / 2);
        g.setLineCap(StrokeLineCap.ROUND);

        g.setStroke(DisplayStyle.FRAME);
        g.setLineWidth(2);
        g.strokeRoundRect(-0.6 * u, -0.85 * u, 1.2 * u, 1.7 * u, u * 0.15, u * 0.15);
        g.setStroke(DisplayStyle.HEADING); // chevron toward the front
        g.strokeLine(-u * 0.15, -u * 0.5, 0, -u * 0.7);
        g.strokeLine(u * 0.15, -u * 0.5, 0, -u * 0.7);

        for (int i = 0; i < wheels.length; i++) {
            double x = wheels[i][0] * u, y = wheels[i][1] * u, len = wheels[i][2] * u;
            g.setStroke(DisplayStyle.WHEEL);
            g.setLineWidth(1.5);
            g.strokeRoundRect(x - ww / 2, y - len / 2, ww, len, ww * 0.5, ww * 0.5);
            if (rollers[i] != 0) g.strokeLine(x - ww / 2, y + rollers[i] * len * 0.3, x + ww / 2, y - rollers[i] * len * 0.3);
            double v = last[i];
            if (Double.isNaN(v)) continue;
            g.setStroke(DisplayStyle.ARROW);
            g.setLineWidth(2.5);
            DisplayStyle.arrow(g, x, y, Math.max(-1.2, Math.min(1.2, v / full)) * u * 0.7, ww * 0.8);
        }
        g.restore();
    }
}
