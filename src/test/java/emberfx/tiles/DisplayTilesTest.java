package emberfx.tiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayTilesTest {
    @Test
    void headingWrapsToOneTurn() {
        assertEquals(0, GyroTile.normalize(0));
        assertEquals(350, GyroTile.normalize(-10));
        assertEquals(10, GyroTile.normalize(370));
        assertEquals(0, GyroTile.normalize(-720));
    }

    @Test
    void channelCountIsOnePastTheHighestPublished() {
        double n = Double.NaN;
        assertEquals(0, PowerDistributionTile.channelCount(new double[] {n, n, n}, 24));
        assertEquals(2, PowerDistributionTile.channelCount(new double[] {1, 0, n, n}, 24));
        assertEquals(2, PowerDistributionTile.channelCount(new double[] {1, 2, 3}, 2));
    }

    @Test
    void gridAlwaysHoldsEveryChannel() {
        for (int n : new int[] {1, 16, 24}) {
            for (double[] size : new double[][] {{260, 240}, {800, 100}, {60, 600}, {0, 0}}) {
                int cols = PowerDistributionTile.columns(n, size[0], size[1]);
                assertTrue(cols >= 1 && cols <= n, "cols " + cols + " for " + n);
            }
        }
    }

    @Test
    void barFractionIsClamped() {
        assertEquals(0.5, DisplayStyle.fraction(1, 2));
        assertEquals(-1, DisplayStyle.fraction(-9, 2));
        assertEquals(0, DisplayStyle.fraction(Double.NaN, 2));
        assertEquals(0, DisplayStyle.fraction(1, 0));
    }
}
