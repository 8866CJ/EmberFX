package emberfx.topics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicCheckTest {
    private static FoundTopic code(String path, String type, boolean reads) {
        return new FoundTopic(path, type, "Robot.java:1", reads);
    }

    private static FoundTopic live(String path, String type) {
        return new FoundTopic(path, type, "robot", false);
    }

    @Test
    void sortsIntoMissingUnexpectedAndWaiting() {
        var r = TopicCheck.compare(
            List.of(code("/SmartDashboard/A", "double", false), code("/SmartDashboard/B", "double", false),
                code("/SmartDashboard/Tune", "double", true)),
            List.of(live("/SmartDashboard/A", "double"), live("/SmartDashboard/Extra", "string")));
        assertEquals(List.of("/SmartDashboard/B"), r.missing().stream().map(FoundTopic::path).toList());
        assertEquals(List.of("/SmartDashboard/Extra"), r.unexpected().stream().map(FoundTopic::path).toList());
        assertEquals(List.of("/SmartDashboard/Tune"), r.waiting().stream().map(FoundTopic::path).toList());
        assertTrue(r.mismatched().isEmpty());
    }

    @Test
    void ignoresInternalsAsUnexpected() {
        var r = TopicCheck.compare(List.of(), List.of(
            live("/FMSInfo/FMSControlData", "int"), live("/LiveWindow/Ungrouped", "string"),
            live("/CameraPublisher/Cam", "Camera"), live("/AdvantageKit/Timestamp", "int"),
            live("/SmartDashboard/Auto/.schema", ""), live("$meta", ""), live("/Shuffleboard/.metadata/x", ""),
            live("/AdvantageKit/RealOutputs/Mine", "double")));
        assertEquals(List.of("/AdvantageKit/RealOutputs/Mine"), r.unexpected().stream().map(FoundTopic::path).toList());
    }

    @Test
    void flagsTypeMismatchButNotNumberKinds() {
        var r = TopicCheck.compare(
            List.of(code("/SmartDashboard/Flag", "boolean", false), code("/SmartDashboard/N", "double", false),
                code("/SmartDashboard/Arr", "double[]", false), code("/SmartDashboard/Auto", "", false)),
            List.of(live("/SmartDashboard/Flag", "string"), live("/SmartDashboard/N", "int"),
                live("/SmartDashboard/Arr", "double"), live("/SmartDashboard/Auto", "String Chooser")));
        assertEquals(List.of("/SmartDashboard/Flag", "/SmartDashboard/Arr"),
            r.mismatched().stream().map(m -> m.code().path()).toList());
    }

    @Test
    void normalizesPaths() {
        assertEquals("/A/B", TopicCheck.normalizePath("A/B"));
        assertEquals("/SmartDashboard/Foo", TopicCheck.normalizePath("/SmartDashboard//Foo/"));
        var r = TopicCheck.compare(List.of(code("/SmartDashboard//Foo", "double", false)),
            List.of(live("/SmartDashboard/Foo", "double")));
        assertTrue(r.missing().isEmpty() && r.unexpected().isEmpty());
    }

    @Test
    void worksWithScannerPaths() {
        var found = new LinkedHashMap<String, FoundTopic>();
        ProjectScanner.scanText("Logger.recordOutput(\"Vision/Has\", x); SmartDashboard.putNumber(\"RPM\", 1);",
            "R.java", found);
        var r = TopicCheck.compare(new ArrayList<>(found.values()),
            List.of(live("/AdvantageKit/RealOutputs/Vision/Has", "boolean"), live("/SmartDashboard/RPM", "double")));
        assertTrue(r.missing().isEmpty() && r.unexpected().isEmpty() && r.mismatched().isEmpty());
    }
}
