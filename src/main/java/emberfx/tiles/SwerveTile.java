package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * Swerve modules seen from above: the chassis turned to the robot's heading, each wheel turned to its
 * module angle, and an arrow per wheel as long as its speed. Reads the "SwerveDrive" layout that
 * Elastic also uses:
 *
 * <pre>
 * SmartDashboard.putData("Swerve", builder -> {
 *     builder.setSmartDashboardType("SwerveDrive");
 *     builder.addDoubleProperty("Front Left Angle", () -> frontLeft.getAngle().getRadians(), null);
 *     builder.addDoubleProperty("Front Left Velocity", () -> frontLeft.getSpeed(), null);
 *     // ... Front Right, Back Left, Back Right ...
 *     builder.addDoubleProperty("Robot Angle", () -> getHeading().getRadians(), null);
 * });
 * </pre>
 */
public final class SwerveTile extends CanvasTile {
    static final String RADIANS = "Radians", DEGREES = "Degrees";
    private static final String[] MODULES = {"Front Left", "Front Right", "Back Left", "Back Right"};
    private static final double[][] CORNERS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}; // front is up on screen
    private static final Color FRAME = Color.web("#94a3b8"), WHEEL = Color.web("#e2e8f0"),
        ARROW = Color.web("#ef4444"), HEADING = Color.web("#22c55e");

    private final DoubleSubscriber[] angles = new DoubleSubscriber[4], speeds = new DoubleSubscriber[4];
    private final DoubleSubscriber robotAngle;

    public SwerveTile(TileConfig config) {
        super(config);
        for (int i = 0; i < 4; i++) {
            angles[i] = track(NT.subDouble(config.topic() + "/" + MODULES[i] + " Angle", 0));
            speeds[i] = track(NT.subDouble(config.topic() + "/" + MODULES[i] + " Velocity", 0));
        }
        robotAngle = track(NT.subDouble(config.topic() + "/Robot Angle", 0));
    }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        boolean degrees = DEGREES.equals(config.option("angleUnits"));
        double maxSpeed = config.optionDouble("maxSpeed") > 0 ? config.optionDouble("maxSpeed") : 4.5;
        double half = Math.min(w, h) * 0.28, wheelW = half * 0.16, wheelL = half * 0.42;

        g.save();
        g.translate(w / 2, h / 2);
        g.rotate(-degrees(robotAngle.get(), degrees)); // counterclockwise on the field is clockwise-negative on screen
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
            g.rotate(-degrees(angles[i].get(), degrees));
            g.setStroke(WHEEL);
            g.strokeRoundRect(-wheelW / 2, -wheelL / 2, wheelW, wheelL, wheelW, wheelW);

            double speed = speeds[i].get(), len = Math.min(1.5, Math.abs(speed) / maxSpeed) * half * 0.9;
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

    private static double degrees(double v, boolean alreadyDegrees) { return alreadyDegrees ? v : Math.toDegrees(v); }
}
