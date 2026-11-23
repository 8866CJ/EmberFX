package emberfx.tiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GaugeAndTimerTest {
    @Test
    void gaugeLabelsAreShort() {
        assertEquals("0", RadialGaugeTile.tickLabel(0));
        assertEquals("1250", RadialGaugeTile.tickLabel(1250));
        assertEquals("2.5", RadialGaugeTile.tickLabel(2.5));
        assertEquals("-0.75", RadialGaugeTile.tickLabel(-0.75));
        assertEquals("0", RadialGaugeTile.tickLabel(-0.001));
    }

    @Test
    void linearGaugeLightsSegmentsUpToTheValue() {
        assertEquals(0, LinearGaugeTile.litCells(Double.NaN, 0, 10, 11));
        assertEquals(0, LinearGaugeTile.litCells(-1, 0, 10, 11));
        assertEquals(1, LinearGaugeTile.litCells(0, 0, 10, 11));
        assertEquals(6, LinearGaugeTile.litCells(5, 0, 10, 11));
        assertEquals(11, LinearGaugeTile.litCells(10, 0, 10, 11));
        assertEquals(11, LinearGaugeTile.litCells(99, 0, 10, 11));
    }

    @Test
    void matchClockAndMode() {
        assertEquals("--:--", MatchTimerTile.clock(-1));
        assertEquals("2:15", MatchTimerTile.clock(134.2));
        assertEquals("0:05", MatchTimerTile.clock(5));
        assertEquals("Disabled", MatchTimerTile.mode(0));
        assertEquals("Auto", MatchTimerTile.mode(1 | 2));
        assertEquals("Teleop", MatchTimerTile.mode(1));
        assertEquals("E-Stopped", MatchTimerTile.mode(1 | 8));
    }
}
