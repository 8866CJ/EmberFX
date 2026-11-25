package emberfx.tiles;

import edu.wpi.first.networktables.NetworkTableValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTileTest {
    @Test
    void numbersUseTheFormat() {
        assertEquals("12.35 V", TextTile.text(NetworkTableValue.makeDouble(12.345), "%.2f V", "T", "F"));
        assertEquals("7", TextTile.text(NetworkTableValue.makeInteger(7), "%.0f", "T", "F"));
    }

    @Test
    void aBadFormatFallsBackToThePlainNumber() {
        assertEquals("1.5", TextTile.text(NetworkTableValue.makeDouble(1.5), "%d", "T", "F"));
    }

    @Test
    void booleansUseTheirTexts() {
        assertEquals("Seen", TextTile.text(NetworkTableValue.makeBoolean(true), "%.2f", "Seen", "Not seen"));
        assertEquals("Not seen", TextTile.text(NetworkTableValue.makeBoolean(false), "%.2f", "Seen", "Not seen"));
    }

    @Test
    void blankAndUnshowableValuesShowDashes() {
        assertEquals("--", TextTile.text(NetworkTableValue.makeString(" "), "%.2f", "T", "F"));
        assertEquals("--", TextTile.text(NetworkTableValue.makeRaw(new byte[0]), "%.2f", "T", "F"));
    }

    @Test
    void missingNumbersShowDashes() {
        assertEquals("--", TextTile.number(Double.NaN, "%.1f"));
    }
}
