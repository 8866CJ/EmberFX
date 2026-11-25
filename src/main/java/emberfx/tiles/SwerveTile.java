package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import emberfx.structs.Poses;
import emberfx.structs.StructSubscriber;
import emberfx.structs.StructValue;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;

import java.util.List;

import static emberfx.tiles.DisplayStyle.ARROW;
import static emberfx.tiles.DisplayStyle.FRAME;
import static emberfx.tiles.DisplayStyle.HEADING;
import static emberfx.tiles.DisplayStyle.WHEEL;

/**
 * Swerve modules seen from above: the chassis turned to the robot's heading, each wheel turned to its
 * module angle, and an arrow per wheel as long as its speed. Modules are in the order front left,
 * front right, back left, back right. It reads any of:
 *
 * <ul>
 * <li>a "SwerveDrive" sendable, the layout Elastic also uses:
 * <pre>
 * SmartDashboard.putData("Swerve", builder -> {
 *     builder.setSmartDashboardType("SwerveDrive");
 *     builder.addDoubleProperty("Front Left Angle", () -> frontLeft.getAngle().getRadians(), null);
 *     builder.addDoubleProperty("Front Left Velocity", () -> frontLeft.getSpeed(), null);
 *     // ... Front Right, Back Left, Back Right ...
 *     builder.addDoubleProperty("Robot Angle", () -> getHeading().getRadians(), null);
 * });
 * </pre></li>
 * <li>a struct SwerveModuleState[] topic, e.g. Logger.recordOutput("Swerve/States", states);</li>
 * <li>a table holding "ModuleStates" and "Pose" structs, like CTRE's generated swerve Telemetry ("DriveState").</li>
 * </ul>
 */
public final class SwerveTile extends CanvasTile {
    static final String RADIANS = "Radians", DEGREES = "Degrees";
    private static final String[] MODULES = {"Front Left", "Front Right", "Back Left", "Back Right"};
    private static final double[][] CORNERS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}; // front is up on screen
    private static final long RESCAN_MS = 1000;

    /** Where the module states come from. Fills angles (degrees), speeds and heading (degrees). */
    private interface Source {
        long stamp();
        void read(double[] angles, double[] speeds, double[] heading);
    }

    private final double[] angles = new double[4], speeds = new double[4], heading = new double[1];
    private Source source;
    private long lastScan, stamp = -1;

    public SwerveTile(TileConfig config) {
        super(config);
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();
        if (source == null && now - lastScan > RESCAN_MS) { // wait until the robot says what kind of topic this is
            lastScan = now;
            source = findSource(config.topic());
        }
        super.update();
    }

    @Override
    protected boolean needsRedraw() {
        if (source == null) { // nothing to read yet: draw the empty chassis once
            boolean first = stamp != 0;
            stamp = 0;
            return first;
        }
        long now = source.stamp();
        if (now == stamp) return false;
        stamp = now;
        source.read(angles, speeds, heading);
        return true;
    }

    /** Decide from the topics the robot publishes. Null until there is something to read. */
    private Source findSource(String topic) {
        var nt = NT.instance();
        if (nt.getTopic(topic).getTypeString().startsWith("struct:SwerveModuleState")) {
            return structSource(track(NT.subStruct(topic)), null);
        }
        if (nt.getTopic(topic + "/ModuleStates").exists()) {
            StructSubscriber pose = nt.getTopic(topic + "/Pose").exists() ? track(NT.subStruct(topic + "/Pose")) : null;
            return structSource(track(NT.subStruct(topic + "/ModuleStates")), pose);
        }
        if (nt.getTopic(topic + "/Robot Angle").exists()) return sendableSource(topic);
        return null;
    }

    private Source structSource(StructSubscriber states, StructSubscriber pose) {
        return new Source() {
            @Override public long stamp() { // the schema version too: a schema may arrive after the last value
                return states.lastChange() + (pose == null ? 0 : pose.lastChange()) + NT.structs().version();
            }

            @Override public void read(double[] a, double[] s, double[] h) {
                List<double[]> list = Poses.swerveStates(states.getArray());
                for (int i = 0; i < 4; i++) {
                    a[i] = i < list.size() ? list.get(i)[1] : 0;
                    s[i] = i < list.size() ? list.get(i)[0] : 0;
                }
                StructValue p = pose == null ? null : pose.get();
                double[] xyDeg = Poses.pose2d(p);
                h[0] = xyDeg == null ? 0 : xyDeg[2];
            }
        };
    }

    private Source sendableSource(String topic) {
        DoubleSubscriber[] subs = new DoubleSubscriber[9];
        for (int i = 0; i < 4; i++) {
            subs[i] = track(NT.subDouble(topic + "/" + MODULES[i] + " Angle", 0));
            subs[4 + i] = track(NT.subDouble(topic + "/" + MODULES[i] + " Velocity", 0));
        }
        subs[8] = track(NT.subDouble(topic + "/Robot Angle", 0));
        return new Source() {
            @Override public long stamp() { return changeStamp(subs); }

            @Override public void read(double[] a, double[] s, double[] h) {
                boolean degrees = DEGREES.equals(config.option("angleUnits"));
                for (int i = 0; i < 4; i++) {
                    a[i] = degrees ? subs[i].get() : Math.toDegrees(subs[i].get());
                    s[i] = subs[4 + i].get();
                }
                h[0] = degrees ? subs[8].get() : Math.toDegrees(subs[8].get());
            }
        };
    }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        double maxSpeed = config.optionDouble("maxSpeed") > 0 ? config.optionDouble("maxSpeed") : 4.5;
        double half = Math.min(w, h) * 0.28, wheelW = half * 0.16, wheelL = half * 0.42;

        g.save();
        g.translate(w / 2, h / 2);
        g.rotate(-heading[0]); // counterclockwise on the field is clockwise-negative on screen
        g.setLineCap(StrokeLineCap.ROUND);

        g.setStroke(FRAME);
        g.setLineWidth(2);
        g.strokeRoundRect(-half, -half, half * 2, half * 2, half * 0.2, half * 0.2);
        // Heading chevron in the middle, pointing to the front of the robot.
        g.setStroke(HEADING);
        g.strokeLine(-half * 0.18, -half * 0.05, 0, -half * 0.25);
        g.strokeLine(half * 0.18, -half * 0.05, 0, -half * 0.25);

        for (int i = 0; i < 4; i++) {
            g.save();
            g.translate(CORNERS[i][0] * half, CORNERS[i][1] * half);
            g.setStroke(FRAME);
            g.setLineWidth(1.5);
            g.strokeOval(-wheelL * 0.75, -wheelL * 0.75, wheelL * 1.5, wheelL * 1.5);
            g.rotate(-angles[i]);
            g.setStroke(WHEEL);
            g.strokeRoundRect(-wheelW / 2, -wheelL / 2, wheelW, wheelL, wheelW, wheelW);

            double speed = speeds[i], len = Math.min(1.5, Math.abs(speed) / maxSpeed) * half * 0.9;
            if (len > 1) {
                double dir = Math.signum(speed); // backwards speed points the arrow back
                g.setStroke(ARROW);
                g.setLineWidth(2.5);
                g.strokeLine(0, 0, 0, -dir * len);
                g.strokeLine(0, -dir * len, -wheelW * 0.8, -dir * (len - wheelW * 1.2));
                g.strokeLine(0, -dir * len, wheelW * 0.8, -dir * (len - wheelW * 1.2));
            }
            g.restore();
        }
        g.restore();
    }
}
