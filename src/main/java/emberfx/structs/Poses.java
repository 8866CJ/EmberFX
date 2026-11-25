package emberfx.structs;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure readers that turn decoded WPILib geometry and kinematics structs into plain numbers.
 * Field names follow the wpimath struct schemas. Null or malformed input yields null (or an empty list).
 */
public final class Poses {
    private Poses() {}

    /** Pose2d to {x meters, y meters, heading degrees}. */
    public static double[] pose2d(StructValue pose) {
        if (pose == null) return null;
        StructValue t = pose.struct("translation");
        StructValue r = pose.struct("rotation");
        if (t == null || r == null) return null;
        return new double[] {t.number("x"), t.number("y"), wrapDegrees(Math.toDegrees(r.number("value")))};
    }

    /** An angle in degrees brought into (-180, 180]: robots that keep turning report 1611 degrees, which reads badly. */
    public static double wrapDegrees(double deg) {
        double d = deg % 360;
        if (d <= -180) d += 360;
        if (d > 180) d -= 360;
        return d;
    }

    /** Pose2d[] to a list of {x, y, degrees}; empty for null. */
    public static List<double[]> pose2dArray(List<StructValue> poses) {
        List<double[]> out = new ArrayList<>();
        if (poses == null) return out;
        for (StructValue p : poses) {
            double[] d = pose2d(p);
            if (d != null) out.add(d);
        }
        return out;
    }

    /** Rotation2d to degrees, or NaN. */
    public static double rotation2dDegrees(StructValue rotation) {
        return rotation == null ? Double.NaN : wrapDegrees(Math.toDegrees(rotation.number("value")));
    }

    /** SwerveModuleState to {speed m/s, angle degrees}. */
    public static double[] swerveState(StructValue state) {
        if (state == null) return null;
        return new double[] {state.number("speed"), rotation2dDegrees(state.struct("angle"))};
    }

    /** SwerveModuleState[] to a list of {speed, degrees}; empty for null. */
    public static List<double[]> swerveStates(List<StructValue> states) {
        List<double[]> out = new ArrayList<>();
        if (states == null) return out;
        for (StructValue s : states) {
            double[] d = swerveState(s);
            if (d != null) out.add(d);
        }
        return out;
    }

    /** ChassisSpeeds to {vx m/s, vy m/s, omega rad/s}. */
    public static double[] chassisSpeeds(StructValue speeds) {
        if (speeds == null) return null;
        return new double[] {speeds.number("vx"), speeds.number("vy"), speeds.number("omega")};
    }
}
