package emberfx.tiles;

import com.fasterxml.jackson.databind.node.ObjectNode;
import emberfx.Setting;
import emberfx.topics.FoundTopic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every tile type the Add tile dialog offers: the built-ins below, plus any that team modules register.
 * To add a built-in tile, write the class and add one line to BUILT_IN.
 */
public final class TileTypes {
    private static final Set<String> NUMBERS = Set.of("double", "float", "int");
    /** Where tuning libraries put values meant to be changed from the dashboard (AdvantageKit, DogLog, WPILib Preferences). */
    private static final List<String> TUNABLE_PREFIXES = List.of("/Tuning/", "/Tunable/", "/Robot/Tunable/", "/Preferences/");

    private static final List<TileType> BUILT_IN = List.of(
        new TileType("text", "Text", "Shows any value as text. Numbers go through a format like %.2f V.",
            List.of(Setting.text("format", "Number format", "%.2f"),
                Setting.text("trueText", "Text when true", "TRUE"),
                Setting.text("falseText", "Text when false", "FALSE"),
                Setting.number("fontSize", "Font size", 28)),
            220, 120, Set.of("double", "float", "int", "string", "double[]", "float[]", "int[]", "string[]", "boolean[]"),
            TextTile::new),
        new TileType("boolean", "Boolean box", "Fills green when true and red when false.",
            List.of(Setting.text("trueText", "Text when true", ""),
                Setting.text("falseText", "Text when false", ""),
                Setting.text("trueColor", "Color when true", "#22c55e"),
                Setting.text("falseColor", "Color when false", "#ef4444")),
            180, 120, Set.of("boolean"), BooleanTile::new),
        new TileType("bar", "Number bar", "A bar that fills between a min and a max, like battery voltage.",
            List.of(Setting.number("min", "Min", 0), Setting.number("max", "Max", 100),
                Setting.text("format", "Number format", "%.1f")),
            240, 110, Set.of(), NumberBarTile::new),
        new TileType("graph", "Graph", "Plots a number over the last few seconds.",
            List.of(Setting.number("seconds", "Seconds shown", 10),
                Setting.optionalNumber("min", "Y min (blank = auto)"),
                Setting.optionalNumber("max", "Y max (blank = auto)")),
            380, 240, Set.of(), GraphTile::new),
        new TileType("gauge", "Radial gauge", "A dial from min to max, like a speedometer.",
            List.of(Setting.number("min", "Min", 0), Setting.number("max", "Max", 100),
                Setting.text("format", "Number format", "%.0f"),
                Setting.optionalNumber("warnAbove", "Turn red above (blank = never)")),
            260, 240, Set.of(), RadialGaugeTile::new),
        new TileType("lgauge", "Linear gauge", "Segmented bars from min to max. Use a table (e.g. /SmartDashboard/Motor Temps) for one bar per number in it.",
            List.of(Setting.number("min", "Min", 0), Setting.number("max", "Max", 100),
                Setting.text("format", "Number format", "%.0f"),
                Setting.optionalNumber("warnAbove", "Turn red above (blank = never)"),
                Setting.choice("direction", "Bars run", LinearGaugeTile.ACROSS, LinearGaugeTile.UP)),
            380, 150, Set.of(), LinearGaugeTile::new),
        new TileType("checklist", "Checklist", "A check for each true/false value in a table (e.g. /SmartDashboard/Ready).",
            List.of(), 220, 170, Set.of(), ChecklistTile::new),
        new TileType("readiness", "Pre-match check", "READY, CHECK or NOT READY from the battery, alerts, faults, E-stop, chosen auto, robot position, checklist and Limelights. Checks without data are hidden.",
            List.of(Setting.reads("batteryTopic", "Battery voltage topic (blank = PowerDistribution)", ""),
                Setting.number("batteryFail", "Battery not ready below (V)", 12.0),
                Setting.number("batteryWarn", "Battery check below (V)", 12.5),
                Setting.text("autoChooser", "Auto chooser", "/SmartDashboard/Auto Chooser"),
                Setting.text("noAutoNames", "Choices that mean no auto (comma list)", "None,Do Nothing,No Auto"),
                Setting.reads("poseTopic", "Robot pose: Field2d or struct Pose2d (blank = first Field2d)", ""),
                Setting.number("startTolerance", "Start position tolerance (m)", 0.15),
                Setting.number("fieldLength", "Field length (m), used to flip autos", 16.54),
                Setting.number("fieldWidth", "Field width (m)", 8.07),
                Setting.choice("symmetry", "Red alliance autos are", "Rotated", "Mirrored"),
                Setting.reads("checklist", "Checklist table of booleans (blank = none)", ""),
                Setting.reads("faultsTopic", "DogLog active faults topic (blank = /Telemetry/Faults/Active)", "")),
            300, 300, Set.of(), ReadinessTile::new),
        new TileType("alerts", "Alerts", "Errors, warnings and infos from WPILib Alerts (e.g. /SmartDashboard/Alerts).",
            List.of(), 320, 170, Set.of("Alerts"), AlertsTile::new),
        new TileType("swerve", "Swerve", "Swerve modules and heading from a SwerveModuleState[] struct, CTRE's DriveState table, or a \"SwerveDrive\" sendable.",
            List.of(Setting.choice("angleUnits", "Angles are in", SwerveTile.RADIANS, SwerveTile.DEGREES),
                Setting.number("maxSpeed", "Top wheel speed (full arrow)", 4.5)),
            260, 280, Set.of("SwerveDrive", "struct:SwerveModuleState[]"), SwerveTile::new),
        new TileType("matchtime", "Match timer", "Time left in the match, with mode, alliance and game data from the FMS.",
            List.of(Setting.number("caution", "Yellow at (seconds)", 30), Setting.number("danger", "Red at (seconds)", 15),
                Setting.number("fontSize", "Font size", 48),
                Setting.text("fmsTable", "FMS info table", "/FMSInfo")),
            320, 160, Set.of(), MatchTimerTile::new),
        new TileType("button", "Button", "Sends true when pressed. On a robot Command, it runs the command.",
            List.of(Setting.text("text", "Button text", "Press"),
                Setting.choice("mode", "When pressed", ButtonTile.PULSE, ButtonTile.HOLD)),
            180, 110, Set.of("Command"), ButtonTile::new),
        new TileType("toggle", "Toggle", "Turns a true/false value on the robot on and off.",
            List.of(Setting.text("onText", "Text when on", "ON"), Setting.text("offText", "Text when off", "OFF")),
            180, 110, Set.of(), ToggleTile::new),
        new TileType("slider", "Slider", "Sends a number to the robot as you drag. Shows the robot's value otherwise.",
            List.of(Setting.number("min", "Min", -1), Setting.number("max", "Max", 1),
                Setting.number("step", "Step", 0.05), Setting.text("format", "Number format", "%.2f")),
            280, 120, Set.of(), SliderTile::new),
        new TileType("input", "Input", "Type a number or text and send it to the robot.",
            List.of(Setting.choice("kind", "Value type", InputTile.AUTO, InputTile.NUMBER, InputTile.TEXT)),
            260, 110, Set.of(), InputTile::new),
        new TileType("chooser", "Chooser", "Dropdown for a SendableChooser, like auto selection.",
            List.of(), 260, 110, Set.of("String Chooser"), ChooserTile::new),
        new TileType("camera", "Camera", "Live video from CameraServer, or any MJPEG stream URL (e.g. PhotonVision).",
            List.of(Setting.text("url", "Stream URL (blank = from the topic)", ""),
                Setting.text("resolution", "Resolution, e.g. 320x240 (blank = camera's)", ""),
                Setting.number("fps", "Max FPS (0 = camera's)", 15),
                Setting.number("compression", "JPEG quality 1-100 (0 = camera's)", 0)),
            400, 320, Set.of("Camera"), CameraTile::new),
        new TileType("field", "Field", "Robot position on the field from a WPILib Field2d.",
            List.of(Setting.number("length", "Field length (m), also used to flip autos", 16.54),
                Setting.number("width", "Field width (m)", 8.07),
                Setting.number("robotSize", "Robot size (m)", 0.9),
                Setting.text("image", "Field image file (optional)", ""),
                Setting.bool("showAuto", "Show the chosen auto (needs a linked robot project)", true),
                Setting.text("autoChooser", "Auto chooser", "/SmartDashboard/Auto Chooser"),
                Setting.choice("symmetry", "Red alliance paths are", "Rotated", "Mirrored")),
            480, 260, Set.of("Field2d", "struct:Pose2d", "struct:Pose2d[]"), FieldTile::new),
        // WPILib Sendables: controls
        new TileType("pid", "PID tuner", "Edit p, i, d, izone and setpoint of a WPILib PIDController live.",
            List.of(), 240, 230, Set.of("PIDController"), PidTile::new),
        new TileType("subsystem", "Subsystem", "The command a WPILib subsystem is running, or idle, with its default command.",
            List.of(), 240, 110, Set.of("Subsystem"), SubsystemTile::new),
        new TileType("scheduler", "Scheduler", "The commands the robot is running, each with a cancel button.",
            List.of(), 280, 200, Set.of("Scheduler"), SchedulerTile::new),
        new TileType("preferences", "Preferences", "Edit every WPILib Preferences key with an editor for its type.",
            List.of(), 320, 260, Set.of("RobotPreferences"), PreferencesTile::new),
        // WPILib Sendables: displays
        new TileType("pdp", "Power distribution", "Current on each PDP/PDH channel, with battery voltage and total current.",
            List.of(Setting.number("maxCurrent", "Full bar at (A)", 40),
                Setting.optionalNumber("warnAbove", "Turn red above (A, blank = never)")),
            300, 220, Set.of("PowerDistribution"), PowerDistributionTile::new),
        new TileType("gyro", "Gyro", "A compass dial with a needle at the heading, from a WPILib gyro.",
            List.of(Setting.bool("counterclockwise", "Counterclockwise is positive", false)),
            220, 240, Set.of("Gyro"), GyroTile::new),
        new TileType("encoder", "Encoder", "Speed, distance and distance per tick from a WPILib Encoder.",
            List.of(Setting.text("format", "Number format", "%.2f"),
                Setting.text("speedUnit", "Speed unit", ""),
                Setting.text("distanceUnit", "Distance unit", "")),
            240, 150, Set.of("Encoder", "Quadrature Encoder"), EncoderTile::new),
        new TileType("diffdrive", "Differential drive", "Left and right motor outputs from a WPILib DifferentialDrive.",
            List.of(Setting.number("maxSpeed", "Output for a full arrow", 1)),
            220, 240, Set.of("DifferentialDrive"), DifferentialDriveTile::new),
        new TileType("mecanumdrive", "Mecanum drive", "The four wheel outputs from a WPILib MecanumDrive.",
            List.of(Setting.number("maxSpeed", "Output for a full arrow", 1)),
            220, 240, Set.of("MecanumDrive"), MecanumDriveTile::new),
        new TileType("accel", "Accelerometer", "X, Y and Z bars from a 3-axis accelerometer, or one bar from a single-axis one.",
            List.of(Setting.number("range", "Range (+/- g)", 2)),
            260, 130, Set.of("3AxisAccelerometer", "Accelerometer"), AccelerometerTile::new));

    private static final List<TileType> all = new ArrayList<>(BUILT_IN);

    private TileTypes() {}

    public static List<TileType> all() { return all; }

    /** Add a tile type from a team module. */
    public static void register(TileType type) {
        if (find(type.id()) != null) throw new IllegalArgumentException("A tile type with id \"" + type.id() + "\" already exists");
        all.add(type);
    }

    public static TileType find(String id) {
        for (TileType t : all) if (t.id().equals(id)) return t;
        return null;
    }

    /**
     * The best tile for a topic. Values the robot reads get a control (toggle, input);
     * everything else gets the first type that lists the topic's data type, or Text.
     */
    public static TileType suggest(FoundTopic topic) {
        String type = topic.type();
        if (topic.robotReads() || isTunable(topic.path())) {
            if (type.equals("boolean")) return find("toggle");
            if (NUMBERS.contains(type) || type.equals("string")) return find("input");
        }
        for (TileType t : all) if (t.dataTypes().contains(type)) return t;
        return find("text");
    }

    /** True for values a tuning library expects the dashboard to change. */
    static boolean isTunable(String path) {
        for (String prefix : TUNABLE_PREFIXES) if (path.startsWith(prefix)) return true;
        return false;
    }

    /** Make the tile for a saved config entry. Never throws: unknown types get a placeholder. */
    public static Tile create(ObjectNode json) {
        String id = json.path("type").asText("text");
        TileType type = find(id);
        TileConfig config = new TileConfig(json, type);
        return type == null ? Tile.missing(config, id) : type.factory().apply(config);
    }
}
