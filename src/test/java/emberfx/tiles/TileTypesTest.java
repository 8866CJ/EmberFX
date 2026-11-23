package emberfx.tiles;

import emberfx.topics.FoundTopic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileTypesTest {
    private static String suggest(String type, boolean robotReads) {
        return TileTypes.suggest(new FoundTopic("/a", type, "robot", robotReads)).id();
    }

    @Test
    void valuesTheRobotPublishesGetADisplay() {
        assertEquals("boolean", suggest("boolean", false));
        assertEquals("text", suggest("double", false));
        assertEquals("text", suggest("", false));
    }

    @Test
    void valuesTheRobotReadsGetAControl() {
        assertEquals("toggle", suggest("boolean", true));
        assertEquals("input", suggest("double", true));
        assertEquals("input", suggest("string", true));
    }

    @Test
    void sendablesGetTheirOwnTile() {
        assertEquals("chooser", suggest("String Chooser", false));
        assertEquals("button", suggest("Command", false));
        assertEquals("camera", suggest("Camera", false));
        assertEquals("field", suggest("Field2d", false));
    }
}
