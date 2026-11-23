package frcdash;

import com.fasterxml.jackson.databind.node.ArrayNode;
import edu.wpi.first.networktables.NetworkTableValue;
import frcdash.tiles.CameraTile;
import frcdash.tiles.TextTile;
import frcdash.tiles.TileTypes;
import frcdash.topics.FoundTopic;
import frcdash.topics.ProjectScanner;
import frcdash.ui.DashboardView;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashTest {
    @Test
    void robotHostFromTeam() {
        assertEquals("10.12.34.2", DashboardView.robotHost(1234));
        assertEquals("10.2.54.2", DashboardView.robotHost(254));
        assertEquals("10.0.1.2", DashboardView.robotHost(1));
        assertEquals("", DashboardView.robotHost(0));
    }

    @Test
    void textTileFormatting() {
        assertEquals("12.35 V", TextTile.text(NetworkTableValue.makeDouble(12.345), "%.2f V", "T", "F"));
        assertEquals("7", TextTile.text(NetworkTableValue.makeInteger(7), "%.0f", "T", "F"));
        assertEquals("1.5", TextTile.text(NetworkTableValue.makeDouble(1.5), "%d", "T", "F")); // bad format falls back
        assertEquals("Seen", TextTile.text(NetworkTableValue.makeBoolean(true), "%.2f", "Seen", "Not seen"));
        assertEquals("--", TextTile.text(NetworkTableValue.makeString(" "), "%.2f", "T", "F"));
        assertEquals("--", TextTile.text(NetworkTableValue.makeRaw(new byte[0]), "%.2f", "T", "F"));
    }

    @Test
    void projectScannerFindsTopics() {
        String code = """
            SmartDashboard.putNumber("Shooter/RPM", rpm);
            SmartDashboard.putData("Auto", chooser);
            double speed = SmartDashboard.getNumber("Drive/MaxSpeed", 4.5);
            SmartDashboard.putBoolean("Drive/MaxSpeed", false); // written too; still counts as read
            Logger.recordOutput("Vision/HasTarget", seen);
            var p = inst.getDoubleTopic("/Tuning/kP").subscribe(0);
            SmartDashboard.putNumber(KEY, 1); // constant key: can't be found
            """;
        Map<String, FoundTopic> found = new LinkedHashMap<>();
        ProjectScanner.scanText(code, "Robot.java", found);

        assertEquals(5, found.size());
        assertEquals(new FoundTopic("/SmartDashboard/Shooter/RPM", "double", "Robot.java:1", false), found.get("/SmartDashboard/Shooter/RPM"));
        assertEquals("", found.get("/SmartDashboard/Auto").type());
        FoundTopic maxSpeed = found.get("/SmartDashboard/Drive/MaxSpeed");
        assertTrue(maxSpeed.robotReads());
        assertEquals("double", maxSpeed.type());
        assertFalse(found.get("/AdvantageKit/RealOutputs/Vision/HasTarget").robotReads());
        assertEquals("double", found.get("/Tuning/kP").type());
        assertEquals("Shooter RPM", found.get("/SmartDashboard/Shooter/RPM").name());
        assertEquals("Tuning kP", found.get("/Tuning/kP").name());
    }

    @Test
    void tileSuggestions() {
        assertEquals("boolean", TileTypes.suggest(new FoundTopic("/a", "boolean", "robot", false)).id());
        assertEquals("toggle", TileTypes.suggest(new FoundTopic("/a", "boolean", "code", true)).id());
        assertEquals("input", TileTypes.suggest(new FoundTopic("/a", "double", "code", true)).id());
        assertEquals("chooser", TileTypes.suggest(new FoundTopic("/a", "String Chooser", "robot", false)).id());
        assertEquals("button", TileTypes.suggest(new FoundTopic("/a", "Command", "robot", false)).id());
        assertEquals("text", TileTypes.suggest(new FoundTopic("/a", "double", "robot", false)).id());
        assertEquals("text", TileTypes.suggest(new FoundTopic("/a", "", "code", false)).id());
    }

    @Test
    void mjpegFramesAreSplitOnJpegMarkers() throws Exception {
        byte[] stream = {
            '-', '-', 'b', 'o', 'u', 'n', 'd', '\r', '\n',          // multipart header noise
            (byte) 0xFF, (byte) 0xD8, 1, 2, (byte) 0xFF, (byte) 0xD9, // frame 1
            'x', 'y',
            (byte) 0xFF, (byte) 0xD8, 3, (byte) 0xFF, (byte) 0xD9,    // frame 2
            (byte) 0xFF, (byte) 0xD8, 4};                             // cut off mid-frame
        var in = new java.io.ByteArrayInputStream(stream);
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, (byte) 0xFF, (byte) 0xD9}, CameraTile.nextJpeg(in));
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xD8, 3, (byte) 0xFF, (byte) 0xD9}, CameraTile.nextJpeg(in));
        assertNull(CameraTile.nextJpeg(in));
    }

    @Test
    void layoutMergeReplacesTabsByName() throws Exception {
        ArrayNode current = Layouts.parse("{\"tabs\": [{\"name\": \"Driver\", \"tiles\": [1]}, {\"name\": \"Pit\"}]}");
        ArrayNode robot = Layouts.parse("{\"tabs\": [{\"name\": \"Driver\", \"tiles\": [2]}, {\"name\": \"Auto\"}]}");

        Layouts.merge(current, robot, false);
        assertEquals("[Driver, Pit, Auto]", names(current));
        assertEquals(2, current.get(0).path("tiles").get(0).asInt());

        Layouts.merge(current, robot, true);
        assertEquals("[Driver, Auto]", names(current));
    }

    private static String names(ArrayNode tabs) {
        java.util.List<String> names = new java.util.ArrayList<>();
        tabs.forEach(t -> names.add(t.path("name").asText()));
        return names.toString();
    }

    @Test
    void settingNumbersPrintClean() {
        assertEquals("8", Setting.number("k", "l", 8).defaultValue());
        assertEquals("2.5", Setting.number("k", "l", 2.5).defaultValue());
    }
}
