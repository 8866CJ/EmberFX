package emberfx.tiles;

import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.IntegerSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.networktables.TopicInfo;
import emberfx.NT;
import emberfx.autos.AutoLibrary;
import emberfx.autos.AutoPreview;
import emberfx.autos.FieldSymmetry;
import emberfx.config.Config;
import emberfx.structs.Poses;
import emberfx.structs.StructSubscriber;
import emberfx.ui.Style;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The field from above with the robot on it.
 *
 * The topic is either a WPILib Field2d (SmartDashboard.putData("Field", field)), drawn with all its
 * objects (more than 8 poses draw as a path), or a struct Pose2d / Pose2d[] topic, drawn as the robot
 * (e.g. Logger.recordOutput("Odometry/Robot", pose) or a StructPublisher).
 *
 * When a robot project is linked, the auto picked in the chooser is drawn too: its PathPlanner or
 * Choreo paths from the project's deploy folder, flipped for the red alliance. While the robot is
 * disabled, a line shows how far the robot is from where that auto starts.
 *
 * Field size is an option, and teams can point the "Field image" option at their own picture of
 * this year's field. Poses are in meters, with the origin at the bottom left (blue alliance wall).
 */
public final class FieldTile extends CanvasTile {
    private static final long RESCAN_MS = 1000;
    private static final double OFF_START_METERS = 0.15; // closer than this to the auto's start counts as "on it"
    private static final Color FIELD = Color.web("#0b1a2e"), MIDLINE = Color.web("#1f2a44"), EDGE = Color.web(Style.GREY),
        ROBOT = Color.web(Style.RED), OBJECT = Color.web(Style.YELLOW), PATH = Color.WHITE,
        AUTO = Color.web("#f59e0b"), AUTO_FAINT = Color.web("#f59e0b", 0.35), MARKER_TEXT = Color.web("#fde68a");
    private static final Font MARKER_FONT = Font.font(MONO, 10), LABEL_FONT = Font.font(SANS, 12);

    /** One thing on the field: its last-change time and its poses as x, y, degrees triples. */
    private interface FieldObject {
        long lastChange();
        double[] poses();
    }

    private final Map<String, FieldObject> objects = new LinkedHashMap<>(); // object name -> poses
    private final Image background;
    private final StringSubscriber autoChoice;
    private final BooleanSubscriber red;
    private final IntegerSubscriber control;
    private AutoPreview auto; // the chosen auto, already flipped for the alliance; null when none
    private String autoKey = "";
    private long lastScan, stamp = -1;

    public FieldTile(TileConfig config) {
        super(config);
        String image = config.option("image").trim();
        background = image.isEmpty() || !new File(image).isFile() ? null : new Image(new File(image).toURI().toString());
        String chooser = config.option("autoChooser").trim();
        autoChoice = track(NT.instance().getStringTopic((chooser.isEmpty() ? "/SmartDashboard/Auto Chooser" : chooser) + "/active").subscribe(""));
        red = track(NT.subBool("/FMSInfo/IsRedAlliance", false));
        control = track(NT.instance().getIntegerTopic("/FMSInfo/FMSControlData").subscribe(0));
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();
        if (now - lastScan > RESCAN_MS) {
            lastScan = now;
            findObjects();
            refreshAuto();
        }
        super.update();
    }

    @Override
    protected boolean needsRedraw() {
        long now = objects.size() + autoKey.hashCode() + control.getLastChange() + NT.structs().version();
        for (FieldObject o : objects.values()) now += o.lastChange();
        boolean changed = now != stamp;
        stamp = now;
        return changed;
    }

    /**
     * Subscribe to every object the robot adds to the Field2d (Robot, plus any getObject("...") it uses),
     * or to the topic itself when it is a struct pose.
     */
    private void findObjects() {
        String topic = config.topic();
        String type = NT.instance().getTopic(topic).getTypeString();
        if (type.startsWith("struct:Pose2d") && !objects.containsKey("Robot")) {
            objects.put("Robot", structObject(track(NT.subStruct(topic))));
            return;
        }
        for (TopicInfo t : NT.instance().getTopicInfo(topic + "/")) {
            String name = t.name.substring(topic.length() + 1);
            if (name.startsWith(".") || name.contains("/") || objects.containsKey(name)) continue;
            if (t.typeStr.startsWith("struct:Pose2d")) {
                objects.put(name, structObject(track(NT.subStruct(t.name))));
            } else {
                DoubleArraySubscriber s = track(NT.instance().getDoubleArrayTopic(t.name).subscribe(new double[0]));
                objects.put(name, new FieldObject() {
                    @Override public long lastChange() { return s.getLastChange(); }
                    @Override public double[] poses() { return s.get(); }
                });
            }
        }
    }

    /** A struct Pose2d or Pose2d[] topic as x, y, degrees triples, converted only when it changes. */
    private static FieldObject structObject(StructSubscriber s) {
        return new FieldObject() {
            private long converted = -1;
            private double[] poses = new double[0];

            @Override public long lastChange() { return s.lastChange(); }

            @Override public double[] poses() {
                long change = s.lastChange();
                if (change == converted) return poses;
                double[] one = Poses.pose2d(s.get());
                List<double[]> many = one != null ? List.of(one) : Poses.pose2dArray(s.getArray());
                poses = new double[many.size() * 3];
                for (int i = 0; i < many.size(); i++) System.arraycopy(many.get(i), 0, poses, i * 3, 3);
                if (one != null || s.getArray() != null) converted = change; // otherwise the schema isn't here yet: retry
                return poses;
            }
        };
    }

    /** Pick up a new auto choice, alliance or edited auto file. Cheap: the library only re-reads changed files. */
    private void refreshAuto() {
        AutoLibrary library = AutoLibrary.shared(Config.robotProject());
        if (library == null || !config.optionBool("showAuto")) {
            auto = null;
            autoKey = "";
            return;
        }
        AutoLibrary.refreshShared(library);
        String choice = autoChoice.get().trim();
        boolean onRed = red.get();
        AutoPreview base = library.preview(choice).orElse(null); // the same object until one of its files changes
        String key = choice + "|" + onRed + "|" + System.identityHashCode(base);
        if (key.equals(autoKey)) return;
        autoKey = key;
        FieldSymmetry symmetry = "Mirrored".equals(config.option("symmetry")) ? FieldSymmetry.MIRRORED : FieldSymmetry.ROTATIONAL;
        auto = base == null ? null : onRed ? base.flipped(symmetry, length(), width()) : base;
    }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double length = length(), width = width();
        double scale = Math.min(w / length, h / width);
        double ox = (w - length * scale) / 2, oy = (h - width * scale) / 2; // center the field in the tile

        if (background != null) g.drawImage(background, ox, oy, length * scale, width * scale);
        else {
            g.setFill(FIELD);
            g.fillRect(ox, oy, length * scale, width * scale);
            g.setStroke(MIDLINE);
            g.setLineWidth(1);
            g.strokeLine(ox + length * scale / 2, oy, ox + length * scale / 2, oy + width * scale);
        }
        g.setStroke(EDGE);
        g.setLineWidth(1);
        g.strokeRect(ox, oy, length * scale, width * scale);

        double robotSize = positive(config.optionDouble("robotSize"), 0.9) * scale;
        if (auto != null) drawAuto(g, ox, oy, scale, width, robotSize);

        for (Map.Entry<String, FieldObject> e : objects.entrySet()) {
            double[] p = e.getValue().poses();
            boolean robot = e.getKey().equals("Robot");
            if (p.length > 24) { // more than 8 poses: draw as a path
                g.setStroke(PATH);
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
                    robot ? robotSize : robotSize / 3, robot ? ROBOT : OBJECT);
            }
        }
    }

    /** The chosen auto's paths, its start pose as a faint robot, its event markers, and the off-start line. */
    private void drawAuto(GraphicsContext g, double ox, double oy, double scale, double width, double robotSize) {
        g.setStroke(AUTO);
        g.setLineWidth(2);
        for (List<AutoPreview.Pose> segment : auto.segments()) {
            g.beginPath();
            for (int i = 0; i < segment.size(); i++) {
                AutoPreview.Pose p = segment.get(i);
                double x = ox + p.x() * scale, y = oy + (width - p.y()) * scale;
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            g.stroke();
        }
        g.setFont(MARKER_FONT);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        for (AutoPreview.Marker m : auto.markers()) {
            double x = ox + m.point().x() * scale, y = oy + (width - m.point().y()) * scale;
            g.setFill(AUTO);
            g.fillOval(x - 3, y - 3, 6, 6);
            g.setFill(MARKER_TEXT);
            g.fillText(m.name(), x + 6, y);
        }
        AutoPreview.Pose start = auto.start();
        if (start == null) return;
        double sx = ox + start.x() * scale, sy = oy + (width - start.y()) * scale;
        drawPose(g, sx, sy, start.headingDeg(), robotSize, AUTO_FAINT);

        // Before the match: is the robot where the auto expects it?
        FieldObject robot = objects.get("Robot");
        boolean enabled = (control.get() & 1) != 0;
        if (robot == null || enabled || robot.poses().length < 3) return;
        double[] r = robot.poses();
        double off = Math.hypot(r[0] - start.x(), r[1] - start.y());
        if (off < OFF_START_METERS) return;
        double rx = ox + r[0] * scale, ry = oy + (width - r[1]) * scale;
        g.setStroke(AUTO);
        g.setLineWidth(1.5);
        g.setLineDashes(6, 4);
        g.strokeLine(rx, ry, sx, sy);
        g.setLineDashes(null);
        g.setFont(LABEL_FONT);
        g.setFill(AUTO);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(String.format(Locale.ROOT, "%.2f m from auto start", off), (rx + sx) / 2, (ry + sy) / 2 - 10);
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

    private double length() { return positive(config.optionDouble("length"), 16.54); }

    private double width() { return positive(config.optionDouble("width"), 8.07); }

    private static double positive(double v, double fallback) {
        return v > 0 ? v : fallback;
    }
}
