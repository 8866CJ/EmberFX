package emberfx.readiness;

import java.util.List;
import java.util.Map;

/**
 * Plain values for {@link Readiness#evaluate}, gathered from NetworkTables by the tile.
 * A null list, map, pose or number means "that data source isn't there", so its check is skipped.
 * Poses are {x, y, degrees} in meters on the blue-origin field; the auto start is unflipped.
 */
public final class Inputs {
    public boolean connected = true;
    public double batteryVolts = Double.NaN;
    public double batteryFail = 12.0, batteryWarn = 12.5;
    public List<String> alertErrors, alertWarnings;
    public List<String> faults;
    public Integer fmsControlBits;
    public String autoName;
    public List<String> noAutoNames = List.of("None", "Do Nothing", "No Auto");
    public boolean projectLinked;
    public List<String> projectAutos = List.of();
    public double[] autoStart, robotPose;
    public boolean redAlliance, mirrored;
    public double fieldLength = 16.54, fieldWidth = 8.07;
    public double startTolerance = 0.15;
    public Map<String, Boolean> checklist;
    public Map<String, Long> limelightAgeMs;
    public long limelightTimeoutMs = 1000;
}
