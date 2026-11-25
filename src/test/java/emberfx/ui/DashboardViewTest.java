package emberfx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardViewTest {
    @Test
    void robotHostIsTenTeAmTwo() {
        assertEquals("10.12.34.2", DashboardView.robotHost(1234));
        assertEquals("10.2.54.2", DashboardView.robotHost(254));
        assertEquals("10.0.1.2", DashboardView.robotHost(1));
    }

    @Test
    void noTeamMeansNoRobotHost() {
        assertEquals("", DashboardView.robotHost(0));
    }

    @Test
    void robotSelectsTabsByNameThenIndex() {
        var names = java.util.List.of("Auto", "Teleop", "0");
        assertEquals(1, DashboardView.tabIndex("teleop", names));
        assertEquals(2, DashboardView.tabIndex("0", names)); // a tab named "0" wins over index 0
        assertEquals(1, DashboardView.tabIndex(" 1 ", names));
        assertEquals(-1, DashboardView.tabIndex("5", names));
        assertEquals(-1, DashboardView.tabIndex("Endgame", names));
    }
}
