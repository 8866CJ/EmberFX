package emberfx.tiles;

import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoubleArraySubscriber;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.IntegerSubscriber;
import edu.wpi.first.networktables.StringArraySubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import emberfx.NT;
import emberfx.autos.AutoLibrary;
import emberfx.autos.AutoPreview;
import emberfx.config.Config;
import emberfx.readiness.CheckResult;
import emberfx.readiness.CheckResult.Status;
import emberfx.readiness.Inputs;
import emberfx.readiness.Readiness;
import emberfx.structs.Poses;
import emberfx.structs.StructSubscriber;
import emberfx.topics.FoundTopic;
import emberfx.topics.LiveTopics;
import emberfx.ui.Style;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * "Is the robot ready for the match?" A big verdict (READY, CHECK, NOT READY) over one row per check.
 *
 * It only reads. Each check finds its own data and is hidden when the robot doesn't publish it:
 * connection, battery (a PowerDistribution sendable or the "Battery topic" option), WPILib Alerts,
 * DogLog faults, E-stop, the chosen auto and whether its file exists, whether the robot sits at the
 * auto's start, a checklist table, and Limelight heartbeats. The logic is in {@link Readiness}.
 */
public final class ReadinessTile extends Tile {
    private static final System.Logger LOG = System.getLogger(ReadinessTile.class.getName());
    private static final long EVAL_NANOS = 250_000_000L, DISCOVER_MS = 1000;
    private static final Pattern LIMELIGHT_HB = Pattern.compile("/(limelight(-[^/]*)?)/hb");
    private static final String ERROR = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM9 9l6 6M15 9l-6 6",
        WARNING = "M12 3.5L22 20H2zM12 10v4M12 17h.01";
    /** DogLog's documented active-faults topic, and the table older versions published under. */
    private static final List<String> DOGLOG_FAULTS = List.of("/Telemetry/Faults/Active", "/Robot/Faults/Active");

    /** A Limelight heartbeat: when its value last changed, as seen by this tile. */
    private static final class Heartbeat {
        final DoubleSubscriber sub;
        double last = Double.NaN;
        long changedAtMs = System.currentTimeMillis();

        Heartbeat(DoubleSubscriber sub) { this.sub = sub; }
    }

    private final VBox rows = new VBox(6);
    private final Label verdict = new Label();
    private final BooleanSubscriber red = track(NT.subBool("/FMSInfo/IsRedAlliance", false));
    private final IntegerSubscriber control = track(NT.instance().getIntegerTopic("/FMSInfo/FMSControlData").subscribe(0));
    private final StringSubscriber autoChoice;
    private final String chooser;
    private final TopicGroup checklist;
    private final DoubleSubscriber batteryOption;

    private final Map<String, DoubleSubscriber> batteries = new LinkedHashMap<>();
    private final Map<String, StringArraySubscriber[]> alerts = new TreeMap<>(); // folder -> errors, warnings
    private final Map<String, StringArraySubscriber> faultSubs = new LinkedHashMap<>();
    private final Map<String, Heartbeat> limelights = new TreeMap<>(); // name -> heartbeat
    private final Map<String, DoubleArraySubscriber> fieldPoses = new HashMap<>();
    private final Map<String, StructSubscriber> structPoses = new HashMap<>();
    private String sendableBattery = "", sendableField = "";
    private long lastEval, lastDiscover;
    private List<CheckResult> shown;

    public ReadinessTile(TileConfig config) {
        super(config);
        String c = config.option("autoChooser").trim();
        chooser = c.isEmpty() ? "/SmartDashboard/Auto Chooser" : c;
        autoChoice = track(NT.instance().getStringTopic(chooser + "/active").subscribe(""));
        String battery = config.option("batteryTopic").trim();
        batteryOption = battery.isEmpty() ? null : track(NT.subDouble(battery, Double.NaN));
        String list = config.option("checklist").trim();
        checklist = list.isEmpty() ? null : new TopicGroup(list, this::track);
        String faults = config.option("faultsTopic").trim();
        for (String f : faults.isEmpty() ? DOGLOG_FAULTS : List.of(faults)) {
            faultSubs.put(f, track(NT.instance().getStringArrayTopic(f).subscribe(new String[0])));
        }

        verdict.getStyleClass().add("verdict");
        verdict.setMaxWidth(Double.MAX_VALUE);
        verdict.setAlignment(Pos.CENTER);
        // The verdict stays put; a long list of checks scrolls under it.
        ScrollPane scroll = Style.styled(new ScrollPane(rows), "plain-scroll");
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox box = new VBox(10, verdict, scroll);
        box.setPadding(new Insets(4, 0, 0, 0));
        setCenter(box);
    }

    @Override
    public void update() {
        long now = System.nanoTime();
        if (now - lastEval < EVAL_NANOS) return;
        lastEval = now;
        discover();
        List<CheckResult> results = Readiness.evaluate(gather());
        if (results.equals(shown)) return;
        shown = results;
        render(results);
    }

    /** Find sendables and Limelights, at most once a second. Subscribers are created once per topic. */
    private void discover() {
        long now = System.currentTimeMillis();
        if (now - lastDiscover < DISCOVER_MS || !NT.isConnected()) return;
        lastDiscover = now;
        List<FoundTopic> found = LiveTopics.list();
        found.sort((a, b) -> a.path().compareTo(b.path()));
        for (FoundTopic t : found) {
            switch (t.type()) {
                case "PowerDistribution" -> {
                    if (sendableBattery.isEmpty()) {
                        sendableBattery = t.path();
                        batteries.put(sendableBattery, track(NT.subDouble(t.path() + "/Voltage", Double.NaN)));
                    }
                }
                case "Alerts" -> alerts.computeIfAbsent(t.path(), p -> new StringArraySubscriber[] {
                    track(NT.instance().getStringArrayTopic(p + "/errors").subscribe(new String[0])),
                    track(NT.instance().getStringArrayTopic(p + "/warnings").subscribe(new String[0]))});
                case "Field2d" -> {
                    if (sendableField.isEmpty()) sendableField = t.path();
                }
                default -> {
                    var m = LIMELIGHT_HB.matcher(t.path());
                    if (m.matches()) {
                        limelights.computeIfAbsent(m.group(1), n -> new Heartbeat(track(NT.subDouble(t.path(), Double.NaN))));
                    }
                }
            }
        }
    }

    private Inputs gather() {
        Inputs in = new Inputs();
        in.connected = NT.isConnected();
        if (!in.connected) return in;

        DoubleSubscriber battery = batteryOption != null ? batteryOption : batteries.get(sendableBattery);
        if (battery != null) in.batteryVolts = battery.get();
        double fail = config.optionDouble("batteryFail"), warn = config.optionDouble("batteryWarn");
        if (!Double.isNaN(fail)) in.batteryFail = fail;
        if (!Double.isNaN(warn)) in.batteryWarn = warn;

        if (!alerts.isEmpty()) {
            in.alertErrors = new ArrayList<>();
            in.alertWarnings = new ArrayList<>();
            for (StringArraySubscriber[] a : alerts.values()) {
                in.alertErrors.addAll(Arrays.asList(a[0].get()));
                in.alertWarnings.addAll(Arrays.asList(a[1].get()));
            }
        }

        for (Map.Entry<String, StringArraySubscriber> f : faultSubs.entrySet()) {
            if (!NT.instance().getTopic(f.getKey()).exists()) continue;
            if (in.faults == null) in.faults = new ArrayList<>();
            in.faults.addAll(Arrays.asList(f.getValue().get()));
        }

        if (NT.instance().getTopic("/FMSInfo/FMSControlData").exists()) in.fmsControlBits = (int) control.get();
        in.redAlliance = red.get();

        if (NT.instance().getTopic(chooser + "/active").exists()) in.autoName = autoChoice.get().trim();
        List<String> noAuto = new ArrayList<>();
        for (String n : config.option("noAutoNames").split(",")) if (!n.isBlank()) noAuto.add(n.trim());
        in.noAutoNames = noAuto;

        in.mirrored = "Mirrored".equals(config.option("symmetry"));
        double length = config.optionDouble("fieldLength"), width = config.optionDouble("fieldWidth"), tol = config.optionDouble("startTolerance");
        if (length > 0) in.fieldLength = length;
        if (width > 0) in.fieldWidth = width;
        if (tol >= 0) in.startTolerance = tol;
        AutoLibrary library = AutoLibrary.shared(Config.robotProject());
        if (library != null) {
            AutoLibrary.refreshShared(library);
            in.projectLinked = true;
            in.projectAutos = library.autoNames();
            if (in.autoName != null && in.projectAutos.contains(in.autoName)) {
                AutoPreview preview = library.preview(in.autoName).orElse(null);
                if (preview != null && preview.start() != null) {
                    in.autoStart = new double[] {preview.start().x(), preview.start().y(), preview.start().headingDeg()};
                }
            }
        }
        in.robotPose = robotPose();

        if (checklist != null) {
            checklist.refresh();
            if (!checklist.entries().isEmpty()) {
                in.checklist = new LinkedHashMap<>();
                for (TopicGroup.Entry e : checklist.entries()) in.checklist.put(e.name(), TopicGroup.bool(e));
            }
        }

        if (!limelights.isEmpty()) {
            long now = System.currentTimeMillis();
            in.limelightAgeMs = new LinkedHashMap<>();
            for (Map.Entry<String, Heartbeat> e : limelights.entrySet()) {
                Heartbeat h = e.getValue();
                double v = h.sub.get();
                if (v != h.last) {
                    h.last = v;
                    h.changedAtMs = now;
                }
                in.limelightAgeMs.put(e.getKey(), now - h.changedAtMs);
            }
        }
        return in;
    }

    /** The robot's {x, y, degrees}: the "Pose topic" option (a Field2d or a struct Pose2d), else the first Field2d. */
    private double[] robotPose() {
        String path = config.option("poseTopic").trim();
        if (path.isEmpty()) path = sendableField;
        if (path.isEmpty()) return null;
        try {
            if (NT.instance().getTopic(path).getTypeString().startsWith("struct:Pose2d")) {
                return Poses.pose2d(structPoses.computeIfAbsent(path, p -> track(NT.subStruct(p))).get());
            }
            double[] v = fieldPoses.computeIfAbsent(path,
                p -> track(NT.instance().getDoubleArrayTopic(p + "/Robot").subscribe(new double[0]))).get();
            return v.length >= 3 ? v : null;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Couldn't read the robot pose from " + path, e);
            return null;
        }
    }

    private void render(List<CheckResult> results) {
        Status overall = Readiness.verdict(results);
        verdict.setText(overall == Status.FAIL ? "NOT READY" : overall == Status.WARN ? "CHECK" : "READY");
        verdict.getStyleClass().removeAll("verdict-ready", "verdict-check", "verdict-notready");
        verdict.getStyleClass().add(overall == Status.FAIL ? "verdict-notready" : overall == Status.WARN ? "verdict-check" : "verdict-ready");

        int issues = 0;
        rows.getChildren().clear();
        for (CheckResult r : results) {
            if (r.status() != Status.PASS) issues++;
            SVGPath icon = Style.icon(switch (r.status()) {
                case FAIL -> ERROR;
                case WARN -> WARNING;
                default -> ChecklistTile.CHECKED;
            });
            icon.setStyle("-fx-stroke: " + switch (r.status()) {
                case FAIL -> "#dc2626";
                case WARN -> "#d97706";
                default -> Style.GREEN;
            } + ";");
            Label detail = Style.styled(new Label(r.detail()), "muted");
            detail.setWrapText(true);
            Label name = new Label(r.name());
            name.setStyle("-fx-font-weight: bold;");
            HBox row = new HBox(8, icon, new VBox(1, name, detail));
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
        setHeaderValue(issues == 0 ? "" : issues + (issues == 1 ? " issue" : " issues"));
    }
}
