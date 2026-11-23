package emberfx.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutsTest {
    private static final String MINE = """
        {"tabs": [{"name": "Driver", "tiles": [1]}, {"name": "Pit"}]}""";
    private static final String ROBOT = """
        {"tabs": [{"name": "Driver", "tiles": [2]}, {"name": "Auto"}]}""";

    @Test
    void mergeReplacesTabsWithTheSameNameAndKeepsTheRest() throws Exception {
        ArrayNode tabs = Layouts.parse(MINE);
        Layouts.merge(tabs, Layouts.parse(ROBOT), false);
        assertEquals(List.of("Driver", "Pit", "Auto"), names(tabs));
        assertEquals(2, tabs.get(0).path("tiles").get(0).asInt(), "the robot's Driver tab replaced mine");
    }

    @Test
    void replaceAllKeepsOnlyTheRobotsTabs() throws Exception {
        ArrayNode tabs = Layouts.parse(MINE);
        Layouts.merge(tabs, Layouts.parse(ROBOT), true);
        assertEquals(List.of("Driver", "Auto"), names(tabs));
    }

    private static List<String> names(ArrayNode tabs) {
        List<String> names = new ArrayList<>();
        tabs.forEach(tab -> names.add(tab.path("name").asText()));
        return names;
    }
}
