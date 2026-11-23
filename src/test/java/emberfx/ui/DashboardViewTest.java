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
}
