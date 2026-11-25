package emberfx.readiness;

import emberfx.readiness.CheckResult.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The pre-match checks: pure functions from {@link Inputs} to results, so they can be tested without
 * JavaFX or NetworkTables. A check whose data source is absent is left out, never failed.
 */
public final class Readiness {
    private static final int MAX_DETAIL = 90;

    private Readiness() {}

    /** Every check that has data, in display order. Not connected gives one FAIL row. */
    public static List<CheckResult> evaluate(Inputs in) {
        List<CheckResult> out = new ArrayList<>();
        if (!in.connected) {
            out.add(new CheckResult("Connection", Status.FAIL, "Robot not connected"));
            return out;
        }
        out.add(new CheckResult("Connection", Status.PASS, "Robot connected"));
        add(out, battery(in));
        add(out, alerts(in));
        add(out, faults(in));
        add(out, estop(in));
        add(out, autoChosen(in));
        if (in.autoName != null && !isNoAuto(in)) add(out, autoFile(in));
        add(out, atStart(in));
        add(out, checklist(in));
        limelights(in, out);
        return out;
    }

    /** Any FAIL is NOT READY (FAIL), else any WARN is CHECK (WARN), else READY (PASS). */
    public static Status verdict(List<CheckResult> results) {
        Status worst = Status.PASS;
        for (CheckResult r : results) {
            if (r.status() == Status.FAIL) return Status.FAIL;
            if (r.status() == Status.WARN) worst = Status.WARN;
        }
        return worst;
    }

    private static void add(List<CheckResult> out, CheckResult r) {
        if (r != null && r.status() != Status.SKIP) out.add(r);
    }

    private static CheckResult battery(Inputs in) {
        double v = in.batteryVolts;
        if (Double.isNaN(v)) return null;
        if (v < in.batteryFail) return new CheckResult("Battery", Status.FAIL, volts(v) + ", below " + volts(in.batteryFail));
        if (v < in.batteryWarn) return new CheckResult("Battery", Status.WARN, volts(v) + ", below " + volts(in.batteryWarn));
        return new CheckResult("Battery", Status.PASS, volts(v));
    }

    private static String volts(double v) { return String.format(Locale.ROOT, "%.1f V", v); }

    private static CheckResult alerts(Inputs in) {
        if (in.alertErrors == null && in.alertWarnings == null) return null;
        List<String> errors = in.alertErrors == null ? List.of() : in.alertErrors;
        List<String> warnings = in.alertWarnings == null ? List.of() : in.alertWarnings;
        if (!errors.isEmpty()) {
            String more = warnings.isEmpty() ? "" : " (+" + warnings.size() + " warning" + (warnings.size() == 1 ? "" : "s") + ")";
            return new CheckResult("Alerts", Status.FAIL, join(errors) + more);
        }
        if (!warnings.isEmpty()) return new CheckResult("Alerts", Status.WARN, join(warnings));
        return new CheckResult("Alerts", Status.PASS, "No alerts");
    }

    private static CheckResult faults(Inputs in) {
        if (in.faults == null) return null;
        if (in.faults.isEmpty()) return new CheckResult("Faults", Status.PASS, "No active faults");
        return new CheckResult("Faults", Status.FAIL, join(in.faults));
    }

    private static CheckResult estop(Inputs in) {
        if (in.fmsControlBits == null) return null;
        if ((in.fmsControlBits & 8) != 0) return new CheckResult("E-stop", Status.FAIL, "Robot is E-stopped");
        return new CheckResult("E-stop", Status.PASS, "Not E-stopped");
    }

    private static boolean isNoAuto(Inputs in) {
        String name = in.autoName == null ? "" : in.autoName.trim();
        if (name.isEmpty()) return true;
        for (String n : in.noAutoNames) if (n.trim().equalsIgnoreCase(name)) return true;
        return false;
    }

    private static CheckResult autoChosen(Inputs in) {
        if (in.autoName == null) return null;
        if (isNoAuto(in)) return new CheckResult("Auto", Status.WARN, "No auto selected");
        return new CheckResult("Auto", Status.PASS, in.autoName.trim());
    }

    /** Skipped when the project has no auto files at all: that team builds its autos in code. */
    private static CheckResult autoFile(Inputs in) {
        if (!in.projectLinked || in.projectAutos.isEmpty()) return null;
        String name = in.autoName.trim();
        if (in.projectAutos.contains(name)) return new CheckResult("Auto file", Status.PASS, "Found in the robot project");
        return new CheckResult("Auto file", Status.WARN,
            "'" + name + "' not found in deploy/pathplanner or deploy/choreo (fine if it's built in code)");
    }

    private static CheckResult atStart(Inputs in) {
        if (in.autoStart == null || in.robotPose == null || in.autoName == null) return null;
        if (in.fmsControlBits != null && (in.fmsControlBits & 1) != 0) return null; // enabled: not before the match
        double sx = in.autoStart[0], sy = in.autoStart[1];
        if (in.redAlliance) { // same flip as the field tile: rotational (L-x, W-y) or mirrored (L-x, y)
            sx = in.fieldLength - sx;
            if (!in.mirrored) sy = in.fieldWidth - sy;
        }
        double d = Math.hypot(in.robotPose[0] - sx, in.robotPose[1] - sy);
        if (d > in.startTolerance) {
            return new CheckResult("Start position", Status.WARN,
                String.format(Locale.ROOT, "Auto '%s' starts %.2f m from the robot", in.autoName.trim(), d));
        }
        return new CheckResult("Start position", Status.PASS, String.format(Locale.ROOT, "%.2f m from the auto start", d));
    }

    private static CheckResult checklist(Inputs in) {
        if (in.checklist == null || in.checklist.isEmpty()) return null;
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : in.checklist.entrySet()) if (!e.getValue()) bad.add(e.getKey() + " not ready");
        if (bad.isEmpty()) return new CheckResult("Checklist", Status.PASS, "All " + in.checklist.size() + " ready");
        return new CheckResult("Checklist", Status.FAIL, join(bad));
    }

    private static void limelights(Inputs in, List<CheckResult> out) {
        if (in.limelightAgeMs == null) return;
        for (Map.Entry<String, Long> e : in.limelightAgeMs.entrySet()) {
            boolean dead = e.getValue() > in.limelightTimeoutMs;
            out.add(new CheckResult(e.getKey(), dead ? Status.FAIL : Status.PASS,
                dead ? "Heartbeat stopped: camera not responding" : "Responding")); // fixed text: no rebuild every tick
        }
    }

    private static String join(List<String> items) {
        String s = String.join("; ", items);
        return s.length() <= MAX_DETAIL ? s : s.substring(0, MAX_DETAIL - 3) + "...";
    }
}
