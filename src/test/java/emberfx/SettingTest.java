package emberfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingTest {
    @Test
    void wholeNumbersHaveNoDecimalPoint() {
        assertEquals("8", Setting.number("key", "Label", 8).defaultValue());
        assertEquals("2.5", Setting.number("key", "Label", 2.5).defaultValue());
    }

    @Test
    void choiceDefaultsToTheFirstChoice() {
        assertEquals("Pulse", Setting.choice("mode", "Mode", "Pulse", "Hold").defaultValue());
    }
}
