package emberfx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayBarTest {
    @Test
    void clockShowsMinutesSecondsAndTenths() {
        assertEquals("0:00.0", ReplayBar.clock(0));
        assertEquals("2:17.4", ReplayBar.clock(137_450_000));
        assertEquals("7:04.2", ReplayBar.clock(424_223_000));
        assertEquals("0:00.0", ReplayBar.clock(-5));
    }
}
