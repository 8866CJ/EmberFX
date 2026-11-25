package emberfx.tiles;

/**
 * A two-sided (tank) drive base with an arrow on each side as long as that side's motor output.
 * Reads a WPILib {@code DifferentialDrive} sendable (keys "Left Motor Speed", "Right Motor Speed",
 * outputs from -1 to 1):
 *
 * <pre>
 * SmartDashboard.putData("Drive", drive);
 * </pre>
 */
public final class DifferentialDriveTile extends DriveTile {
    public DifferentialDriveTile(TileConfig config) {
        super(config, new String[] {"Left Motor Speed", "Right Motor Speed"},
            new double[][] {{-0.6, 0, 1.2}, {0.6, 0, 1.2}}, new int[] {0, 0});
    }
}
