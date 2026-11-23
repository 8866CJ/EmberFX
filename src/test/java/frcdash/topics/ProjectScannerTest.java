package frcdash.topics;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectScannerTest {
    private static final String ROBOT_CODE = """
        SmartDashboard.putNumber("Shooter/RPM", rpm);
        SmartDashboard.putData("Auto", chooser);
        double speed = SmartDashboard.getNumber("Drive/MaxSpeed", 4.5);
        SmartDashboard.putBoolean("Drive/MaxSpeed", false); // written too; still counts as read
        Logger.recordOutput("Vision/HasTarget", seen);
        var p = inst.getDoubleTopic("/Tuning/kP").subscribe(0);
        SmartDashboard.putNumber(KEY, 1); // constant key: can't be found
        """;

    private static Map<String, FoundTopic> scan() {
        Map<String, FoundTopic> found = new LinkedHashMap<>();
        ProjectScanner.scanText(ROBOT_CODE, "Robot.java", found);
        return found;
    }

    @Test
    void findsEveryLiteralKey() {
        assertEquals(5, scan().size());
    }

    @Test
    void smartDashboardPutsGiveTypeAndLine() {
        assertEquals(new FoundTopic("/SmartDashboard/Shooter/RPM", "double", "Robot.java:1", false),
            scan().get("/SmartDashboard/Shooter/RPM"));
        assertEquals("", scan().get("/SmartDashboard/Auto").type(), "putData's Sendable type is only known at runtime");
    }

    @Test
    void aKeyTheRobotReadsIsMarkedEvenIfItAlsoWritesIt() {
        FoundTopic maxSpeed = scan().get("/SmartDashboard/Drive/MaxSpeed");
        assertTrue(maxSpeed.robotReads());
        assertEquals("double", maxSpeed.type());
    }

    @Test
    void advantageKitAndRawTopicsUseTheirFullPaths() {
        assertFalse(scan().get("/AdvantageKit/RealOutputs/Vision/HasTarget").robotReads());
        assertEquals("double", scan().get("/Tuning/kP").type());
    }

    @Test
    void namesDropTheTablePrefix() {
        assertEquals("Shooter RPM", scan().get("/SmartDashboard/Shooter/RPM").name());
        assertEquals("Tuning kP", scan().get("/Tuning/kP").name());
    }
}
