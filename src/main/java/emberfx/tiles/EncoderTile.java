package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.ui.Style;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.control.Label;

import java.util.Arrays;

/**
 * Speed, distance and distance per tick from a WPILib {@code Encoder} sendable ("Encoder" or
 * "Quadrature Encoder", keys Speed, Distance, Distance per Tick):
 *
 * <pre>
 * SmartDashboard.putData("Left Encoder", leftEncoder);
 * </pre>
 */
public final class EncoderTile extends Tile {
    private static final String[] NAMES = {"Speed", "Distance", "Distance per Tick"};

    private final DoubleSubscriber[] subs;
    private final Label[] values = new Label[NAMES.length];
    private final double[] last = new double[NAMES.length];
    private final String[] units;

    public EncoderTile(TileConfig config) {
        super(config);
        subs = DisplayStyle.subscribe(config, this::track, NAMES);
        Arrays.fill(last, Double.NEGATIVE_INFINITY); // so the first update always writes
        units = new String[] {config.option("speedUnit"), config.option("distanceUnit"), config.option("distanceUnit")};
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        grid.setPadding(new Insets(2, 0, 0, 0));
        for (int i = 0; i < NAMES.length; i++) {
            Label name = Style.styled(new Label(NAMES[i]), "muted");
            values[i] = Style.value("--", 20);
            grid.add(name, 0, i);
            grid.add(values[i], 1, i);
            GridPane.setHgrow(values[i], Priority.ALWAYS);
        }
        setCenter(grid);
    }

    @Override
    public void update() {
        for (int i = 0; i < NAMES.length; i++) {
            double v = subs[i].get();
            if (Double.compare(v, last[i]) == 0) continue; // text is rebuilt only when the value changes
            last[i] = v;
            values[i].setText(Double.isNaN(v) ? "--" // distance per tick is tiny (0.0005), so it skips the number format
                : (i == 2 ? NumText.format(v) : TextTile.number(v, config.option("format"))) + unit(i));
        }
    }

    private String unit(int i) {
        return units[i].isBlank() ? "" : " " + units[i];
    }
}
