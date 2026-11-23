package emberfx.tiles;

import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.TopicInfo;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws a WPILib Field2d (SmartDashboard.putData("Field", field)): the robot as a box with a heading line,
 * other objects as small boxes, and objects with more than 8 poses as a path (trajectories).
 *
 * Field size is an option, and teams can point the "Field image" option at their own picture of
 * this year's field. Poses are in meters, with the origin at the bottom left (blue alliance wall).
 */
public final class FieldTile extends CanvasTile {
    private static final long RESCAN_MS = 1000;

    private final Map<String, DoubleArraySubscriber> objects = new LinkedHashMap<>(); // object name -> poses
    private final Image background;
    private long lastScan;

    public FieldTile(TileConfig config) {
        super(config);
        String image = config.option("image").trim();
        background = image.isEmpty() || !new File(image).isFile() ? null : new Image(new File(image).toURI().toString());
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();
        if (now - lastScan > RESCAN_MS) {
            lastScan = now;
            findObjects();
        }
        super.update();
    }

    /** Subscribe to every object the robot adds to the Field2d (Robot, plus any getObject("...") it uses). */
    private void findObjects() {
        for (TopicInfo t : NT.instance().getTopicInfo(config.topic() + "/")) {
            String name = t.name.substring(config.topic().length() + 1);
            if (name.startsWith(".") || name.contains("/") || objects.containsKey(name)) continue;
            objects.put(name, track(NT.instance().getDoubleArrayTopic(t.name).subscribe(new double[0])));
        }
    }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double length = positive(config.optionDouble("length"), 17.5), width = positive(config.optionDouble("width"), 8.0);
        double scale = Math.min(w / length, h / width);
        double ox = (w - length * scale) / 2, oy = (h - width * scale) / 2; // center the field in the tile

        if (background != null) g.drawImage(background, ox, oy, length * scale, width * scale);
        else {
            g.setFill(Color.web("#0b1a2e"));
            g.fillRect(ox, oy, length * scale, width * scale);
            g.setStroke(Color.web("#1f2a44"));
            g.setLineWidth(1);
            g.strokeLine(ox + length * scale / 2, oy, ox + length * scale / 2, oy + width * scale);
        }
        g.setStroke(Color.web(Style.GREY));
        g.strokeRect(ox, oy, length * scale, width * scale);

        double robotSize = positive(config.optionDouble("robotSize"), 0.9) * scale;
        for (Map.Entry<String, DoubleArraySubscriber> e : objects.entrySet()) {
            double[] p = e.getValue().get();
            boolean robot = e.getKey().equals("Robot");
            if (p.length > 24) { // more than 8 poses: draw as a path
                g.setStroke(Color.WHITE);
                g.setLineWidth(2);
                g.beginPath();
                for (int i = 0; i + 2 < p.length; i += 3) {
                    double x = ox + p[i] * scale, y = oy + (width - p[i + 1]) * scale;
                    if (i == 0) g.moveTo(x, y);
                    else g.lineTo(x, y);
                }
                g.stroke();
                continue;
            }
            for (int i = 0; i + 2 < p.length; i += 3) {
                drawPose(g, ox + p[i] * scale, oy + (width - p[i + 1]) * scale, p[i + 2],
                    robot ? robotSize : robotSize / 3, robot ? Color.web(Style.RED) : Color.web(Style.YELLOW));
            }
        }
    }

    /** A box at (x, y) turned by degrees (counterclockwise, like WPILib), with a line toward the front. */
    private static void drawPose(GraphicsContext g, double x, double y, double degrees, double size, Color color) {
        g.save();
        g.translate(x, y);
        g.rotate(-degrees); // screen y points down, so counterclockwise on the field is negative here
        g.setStroke(color);
        g.setLineWidth(2);
        g.strokeRect(-size / 2, -size / 2, size, size);
        g.strokeLine(0, 0, size / 2, 0);
        g.restore();
    }

    private static double positive(double v, double fallback) {
        return v > 0 ? v : fallback;
    }
}
