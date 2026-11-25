package emberfx.tiles;

/**
 * A mecanum drive base with a slanted-roller wheel at each corner and an arrow per wheel as long as
 * its motor output. Reads a WPILib {@code MecanumDrive} sendable (keys "Front Left Motor Speed",
 * "Front Right Motor Speed", "Rear Left Motor Speed", "Rear Right Motor Speed", outputs -1 to 1):
 *
 * <pre>
 * SmartDashboard.putData("Drive", drive);
 * </pre>
 */
public final class MecanumDriveTile extends DriveTile {
    public MecanumDriveTile(TileConfig config) {
        super(config,
            new String[] {"Front Left Motor Speed", "Front Right Motor Speed", "Rear Left Motor Speed", "Rear Right Motor Speed"},
            new double[][] {{-0.6, -0.5, 0.6}, {0.6, -0.5, 0.6}, {-0.6, 0.5, 0.6}, {0.6, 0.5, 0.6}},
            new int[] {1, -1, -1, 1});
    }
}
