package frcdash.tiles;

import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.NetworkTableValue;
import frcdash.NT;
import frcdash.ui.Style;
import javafx.scene.control.Label;

import java.util.Arrays;
import java.util.IllegalFormatException;

/** Shows one topic of any type: numbers through a format, booleans as two texts, strings as-is. */
public final class TextTile extends Tile {
    private static final double MIN_FONT_SIZE = 8;

    private final GenericSubscriber sub;
    private final Label value;

    public TextTile(TileConfig config) {
        super(config);
        sub = track(NT.instance().getTopic(config.topic()).genericSubscribe());
        value = Style.value("--", Math.max(MIN_FONT_SIZE, config.optionDouble("fontSize")));
        setCenter(value);
    }

    @Override
    public void update() {
        value.setText(text(sub.get(), config.option("format"), config.option("trueText"), config.option("falseText")));
    }

    /** How a value is shown. Public for tests and for other tiles that show text. */
    public static String text(NetworkTableValue v, String format, String trueText, String falseText) {
        return switch (v.getType()) {
            case kBoolean -> v.getBoolean() ? trueText : falseText;
            case kDouble, kFloat, kInteger -> number(((Number) v.getValue()).doubleValue(), format);
            case kString -> v.getString().isBlank() ? "--" : v.getString();
            case kDoubleArray -> Arrays.toString(v.getDoubleArray());
            case kFloatArray -> Arrays.toString(v.getFloatArray());
            case kIntegerArray -> Arrays.toString(v.getIntegerArray());
            case kBooleanArray -> Arrays.toString(v.getBooleanArray());
            case kStringArray -> String.join(", ", v.getStringArray());
            default -> "--"; // not published yet, or raw bytes
        };
    }

    public static String number(double d, String format) {
        try {
            return String.format(format, d);
        } catch (IllegalFormatException e) { // e.g. "%d" typed for a double
            return String.valueOf(d);
        }
    }
}
