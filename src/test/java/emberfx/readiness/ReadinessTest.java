package emberfx.readiness;

import emberfx.readiness.CheckResult.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessTest {
    private static CheckResult find(Inputs in, String name) {
        return Readiness.evaluate(in).stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

    private static Status status(Inputs in, String name) {
        CheckResult r = find(in, name);
        return r == null ? Status.SKIP : r.status();
    }

    @Test
    void nothingPublishedIsJustConnectionAndReady() {
        List<CheckResult> r = Readiness.evaluate(new Inputs());
        assertEquals(1, r.size());
        assertEquals(Status.PASS, Readiness.verdict(r));
    }

    @Test
    void notConnectedShortCircuits() {
        Inputs in = new Inputs();
        in.connected = false;
        in.batteryVolts = 5;
        List<CheckResult> r = Readiness.evaluate(in);
        assertEquals(List.of(new CheckResult("Connection", Status.FAIL, "Robot not connected")), r);
        assertEquals(Status.FAIL, Readiness.verdict(r));
    }

    @Test
    void battery() {
        Inputs in = new Inputs();
        assertEquals(Status.SKIP, status(in, "Battery"));
        in.batteryVolts = 12.8;
        assertEquals(Status.PASS, status(in, "Battery"));
        in.batteryVolts = 12.3;
        assertEquals(Status.WARN, status(in, "Battery"));
        assertEquals("12.3 V, below 12.5 V", find(in, "Battery").detail());
        in.batteryVolts = 11.9;
        assertEquals(Status.FAIL, status(in, "Battery"));
    }

    @Test
    void alerts() {
        Inputs in = new Inputs();
        assertEquals(Status.SKIP, status(in, "Alerts"));
        in.alertErrors = List.of();
        in.alertWarnings = List.of();
        assertEquals(Status.PASS, status(in, "Alerts"));
        in.alertWarnings = List.of("Low pressure");
        assertEquals(Status.WARN, status(in, "Alerts"));
        in.alertErrors = List.of("Gyro not found");
        assertEquals(Status.FAIL, status(in, "Alerts"));
        assertTrue(find(in, "Alerts").detail().startsWith("Gyro not found"));
        in.alertErrors = List.of("x".repeat(300));
        in.alertWarnings = List.of();
        assertTrue(find(in, "Alerts").detail().length() <= 90);
    }

    @Test
    void faults() {
        Inputs in = new Inputs();
        assertEquals(Status.SKIP, status(in, "Faults"));
        in.faults = List.of();
        assertEquals(Status.PASS, status(in, "Faults"));
        in.faults = List.of("ArmStalled");
        assertEquals(Status.FAIL, status(in, "Faults"));
    }

    @Test
    void estopOnlyFailsOnBit8() {
        Inputs in = new Inputs();
        assertEquals(Status.SKIP, status(in, "E-stop"));
        in.fmsControlBits = 1; // enabled before the match is not a failure
        assertEquals(Status.PASS, status(in, "E-stop"));
        in.fmsControlBits = 8;
        assertEquals(Status.FAIL, status(in, "E-stop"));
    }

    @Test
    void autoChosenAndNoAutoNames() {
        Inputs in = new Inputs();
        assertEquals(Status.SKIP, status(in, "Auto"));
        in.autoName = "Two Leg";
        assertEquals(Status.PASS, status(in, "Auto"));
        for (String none : new String[] {"", "  ", "none", "DO NOTHING", " No Auto "}) {
            in.autoName = none;
            assertEquals(Status.WARN, status(in, "Auto"), none);
        }
        in.noAutoNames = List.of("Idle");
        in.autoName = "None";
        assertEquals(Status.PASS, status(in, "Auto"));
        in.autoName = "idle";
        assertEquals(Status.WARN, status(in, "Auto"));
    }

    @Test
    void autoFile() {
        Inputs in = new Inputs();
        in.autoName = "Two Leg";
        assertEquals(Status.SKIP, status(in, "Auto file"));
        in.projectLinked = true;
        assertEquals(Status.SKIP, status(in, "Auto file")); // no auto files at all: built in code
        in.projectAutos = List.of("Three Leg");
        assertEquals(Status.WARN, status(in, "Auto file"));
        in.projectAutos = List.of("Three Leg", "Two Leg");
        assertEquals(Status.PASS, status(in, "Auto file"));
        in.autoName = "None";
        assertEquals(Status.SKIP, status(in, "Auto file"));
    }

    @Test
    void startPositionWithRedFlip() {
        Inputs in = new Inputs();
        in.autoName = "Two Leg";
        in.autoStart = new double[] {2, 3, 0};
        in.robotPose = new double[] {2.05, 3, 0};
        assertEquals(Status.PASS, status(in, "Start position"));
        in.robotPose = new double[] {2.42, 3, 0};
        CheckResult far = find(in, "Start position");
        assertEquals(Status.WARN, far.status());
        assertEquals("Auto 'Two Leg' starts 0.42 m from the robot", far.detail());

        in.redAlliance = true; // start becomes (16.54 - 2, 8.07 - 3); a robot on the blue spot is now far away
        assertEquals(Status.WARN, status(in, "Start position"));
        in.robotPose = new double[] {14.54, 5.07, 180};
        assertEquals(Status.PASS, status(in, "Start position"));
        in.mirrored = true; // mirrored keeps y
        assertEquals(Status.WARN, status(in, "Start position"));
        in.robotPose = new double[] {14.54, 3, 180};
        assertEquals(Status.PASS, status(in, "Start position"));

        in.fmsControlBits = 1; // enabled: not checked
        in.robotPose = new double[] {0, 0, 0};
        assertEquals(Status.SKIP, status(in, "Start position"));
    }

    @Test
    void startPositionSkippedWithoutData() {
        Inputs in = new Inputs();
        in.autoName = "A";
        in.robotPose = new double[] {1, 1, 0};
        assertEquals(Status.SKIP, status(in, "Start position"));
        in.robotPose = null;
        in.autoStart = new double[] {1, 1, 0};
        assertEquals(Status.SKIP, status(in, "Start position"));
    }

    @Test
    void checklist() {
        Inputs in = new Inputs();
        assertEquals(Status.SKIP, status(in, "Checklist"));
        in.checklist = Map.of("Intake", true);
        assertEquals(Status.PASS, status(in, "Checklist"));
        in.checklist = Map.of("Intake", true, "Shooter", false);
        assertEquals(Status.FAIL, status(in, "Checklist"));
        assertEquals("Shooter not ready", find(in, "Checklist").detail());
    }

    @Test
    void limelight() {
        Inputs in = new Inputs();
        assertNull(find(in, "limelight"));
        in.limelightAgeMs = Map.of("limelight", 200L);
        assertEquals(Status.PASS, status(in, "limelight"));
        in.limelightAgeMs = Map.of("limelight", 1500L);
        assertEquals(Status.FAIL, status(in, "limelight"));
        assertEquals("Heartbeat stopped: camera not responding", find(in, "limelight").detail());
    }

    @Test
    void verdictRules() {
        CheckResult pass = new CheckResult("a", Status.PASS, ""), warn = new CheckResult("b", Status.WARN, ""),
            fail = new CheckResult("c", Status.FAIL, "");
        assertEquals(Status.PASS, Readiness.verdict(List.of(pass)));
        assertEquals(Status.WARN, Readiness.verdict(List.of(pass, warn)));
        assertEquals(Status.FAIL, Readiness.verdict(List.of(warn, fail, pass)));
    }
}
