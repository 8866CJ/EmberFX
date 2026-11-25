package emberfx.tiles;

import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.NetworkTableType;
import emberfx.NT;
import emberfx.structs.StructFormat;
import emberfx.structs.StructSubscriber;
import emberfx.structs.StructValue;
import emberfx.ui.Style;
import javafx.scene.control.Label;

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;

/** Shows one topic of any type: numbers through a format, booleans as two texts, strings as-is. */
public final class TextTile extends Tile {
    private static final double MIN_FONT_SIZE = 8;

    private final GenericSubscriber sub;
    private final StructSubscriber structSub; // the same topic, decoded, for struct topics such as Pose2d
    private long shownChange = -1;
    private final Label value;

    public TextTile(TileConfig config) {
        super(config);
        sub = track(NT.instance().getTopic(config.topic()).genericSubscribe());
        structSub = track(NT.subStruct(config.topic()));
        value = Style.value("--", Math.max(MIN_FONT_SIZE, config.optionDouble("fontSize")));
        setCenter(value);
    }

    @Override
    public void update() {
        long change = sub.getLastChange();
        if (change == shownChange && change != 0) return; // formatting every frame is wasted work
        NetworkTableValue v = sub.get();
        if (v.getType() == NetworkTableType.kRaw) {
            StructValue s = structSub.get();
            List<StructValue> list = s == null ? structSub.getArray() : null;
            if (s == null && list == null) return; // schema not here yet: try again next frame
            value.setText(s != null ? StructFormat.toText(s) : StructFormat.toText(list));
        } else {
            value.setText(text(v, config.option("format"), config.option("trueText"), config.option("falseText")));
        }
        shownChange = change;
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
