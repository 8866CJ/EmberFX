package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.UnaryOperator;

/**
 * Colors, fonts and small helpers shared by the tiles that show WPILib display Sendables
 * (power distribution, gyro, drive bases, accelerometer). Colors live here so a light theme
 * only has to change one place; the gauge colors are reused from {@link RadialGaugeTile}.
 */
final class DisplayStyle {
    static final Color FRAME = Color.web("#94a3b8"), WHEEL = Color.web("#e2e8f0"),
        ARROW = Color.web("#ef4444"), HEADING = Color.web("#22c55e");

    /** Created on first use, so tests of the pure helpers never touch the font system. */
    static final class Fonts {
        static final Font SMALL = Font.font(CanvasTile.MONO, 10), MONO = Font.font(CanvasTile.MONO, 12),
            BOLD = Font.font(CanvasTile.MONO, FontWeight.BOLD, 13), BIG = Font.font(CanvasTile.MONO, FontWeight.BOLD, 22);

        private Fonts() {}
    }

    private DisplayStyle() {}

    /** Subscribes to config.topic() + "/" + key for each key, with NaN until the robot publishes it. */
    static DoubleSubscriber[] subscribe(TileConfig config, UnaryOperator<DoubleSubscriber> track, String... keys) {
        DoubleSubscriber[] subs = new DoubleSubscriber[keys.length];
        for (int i = 0; i < keys.length; i++) subs[i] = track.apply(NT.subDouble(config.topic() + "/" + keys[i], Double.NaN));
        return subs;
    }

    /** Copies each subscriber's value into last[] and says whether any differed (NaN equals NaN). No allocation. */
    static boolean sync(DoubleSubscriber[] subs, double[] last) {
        boolean changed = false;
        for (int i = 0; i < subs.length; i++) {
            double v = subs[i].get();
            if (Double.compare(v, last[i]) != 0) {
                last[i] = v;
                changed = true;
            }
        }
        return changed;
    }

    /** value / full clamped to -1..1; 0 for NaN or a non-positive scale. */
    static double fraction(double value, double full) {
        if (Double.isNaN(value) || !(full > 0)) return 0;
        return Math.max(-1, Math.min(1, value / full));
    }

    /** A line from (x, y) up the screen by len (down for negative) with an arrow head, in the current stroke. */
    static void arrow(GraphicsContext g, double x, double y, double len, double head) {
        if (Math.abs(len) < 1) return;
        double dir = Math.signum(len), tip = y - len;
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokeLine(x, y, x, tip);
        g.strokeLine(x, tip, x - head, tip + dir * head * 1.5);
        g.strokeLine(x, tip, x + head, tip + dir * head * 1.5);
    }
}
