package emberfx.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationsTest {
    @Test
    void readsElasticLibJson() throws IOException {
        Notifications.Message m = Notifications.parse(
            "{\"level\":\"ERROR\",\"title\":\"Auto failed\",\"description\":\"No path\",\"displayTime\":5000,\"width\":400,\"height\":-1}");
        assertEquals("ERROR", m.level());
        assertEquals("Auto failed", m.title());
        assertEquals("No path", m.description());
        assertEquals(5000, m.displayMillis());
        assertEquals(400, m.width());
    }

    @Test
    void fillsInDefaults() throws IOException {
        Notifications.Message m = Notifications.parse("{\"level\":\"debug\",\"title\":\"Hi\",\"width\":-1}");
        assertEquals("INFO", m.level());
        assertEquals("", m.description());
        assertEquals(3000, m.displayMillis());
        assertEquals(340, m.width());
    }

    @Test
    void rejectsNonObjects() {
        assertThrows(IOException.class, () -> Notifications.parse("[1,2]"));
        assertThrows(IOException.class, () -> Notifications.parse("not json"));
    }
}
