package emberfx.tiles;

import edu.wpi.first.networktables.NetworkTableValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlTilesTest {
    @Test
    void numbersAreShortAndPlain() {
        assertEquals("0.1", NumText.format(0.1));
        assertEquals("3", NumText.format(3.0));
        assertEquals("0.0001", NumText.format(1e-4));
        assertEquals("0", NumText.format(-0.0));
        assertEquals("", NumText.format(Double.NaN));
    }

    @Test
    void parseRejectsJunk() {
        assertEquals(1.5, NumText.parse(" 1.5 "));
        assertTrue(Double.isNaN(NumText.parse("abc")));
        assertTrue(Double.isNaN(NumText.parse("")));
        assertTrue(Double.isNaN(NumText.parse("Infinity")));
    }

    @Test
    void numericCompareIgnoresFormatting() {
        assertTrue(NumText.same("0.10", "0.1", true));
        assertFalse(NumText.same("0.2", "0.1", true));
        assertFalse(NumText.same("0.10", "0.1", false));
    }

    @Test
    void preferenceTextParsesByType() {
        assertEquals(2.5, PreferencesTile.parse("double", "2.5"));
        assertEquals(3L, PreferencesTile.parse("int", "2.6"));
        assertEquals("hi", PreferencesTile.parse("string", "hi"));
        assertNull(PreferencesTile.parse("double", "x"));
        assertNull(PreferencesTile.parse("double[]", "1"));
    }

    @Test
    void schedulerIdsAcceptIntegersAndDoubles() {
        assertArrayEquals(new long[] {5, 7}, SchedulerTile.ids(NetworkTableValue.makeIntegerArray(new long[] {5, 7})));
        assertArrayEquals(new long[] {5, 7}, SchedulerTile.ids(NetworkTableValue.makeDoubleArray(new double[] {5, 7})));
        assertArrayEquals(new long[0], SchedulerTile.ids(NetworkTableValue.makeString("x")));
    }

    @Test
    void subsystemState() {
        assertEquals(SubsystemTile.State.IDLE, SubsystemTile.state(false, "none", true, "Hold"));
        assertEquals(SubsystemTile.State.DEFAULT, SubsystemTile.state(true, "Hold", true, "Hold"));
        assertEquals(SubsystemTile.State.ACTIVE, SubsystemTile.state(true, "Shoot", true, "Hold"));
        assertEquals(SubsystemTile.State.ACTIVE, SubsystemTile.state(true, "Shoot", false, "none"));
    }
}
