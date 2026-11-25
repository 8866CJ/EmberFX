package emberfx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TabPageTest {
    @Test
    void pastesOnlyCopiedTiles() {
        assertEquals("gauge", TabPage.tileFromJson("{\"type\":\"gauge\",\"topic\":\"/SmartDashboard/RPM\",\"w\":200}").path("type").asText());
        assertNull(TabPage.tileFromJson("hello"));
        assertNull(TabPage.tileFromJson("{\"type\":\"gauge\"}"));   // no topic
        assertNull(TabPage.tileFromJson("[\"type\"]"));
    }
}
