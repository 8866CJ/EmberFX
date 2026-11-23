package emberfx.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

/** A bar filled between min and max, with the number under it. */
public final class NumberBarTile extends Tile {
    private final DoubleSubscriber sub;
    private final ProgressBar bar = new ProgressBar(0);
    private final Label value = Style.value("--", 20);

    public NumberBarTile(TileConfig config) {
        super(config);
        sub = track(NT.subDouble(config.topic(), 0.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(22);
        setCenter(new VBox(8, bar, value));
    }

    @Override
    public void update() {
        double v = sub.get(), min = config.optionDouble("min"), max = config.optionDouble("max");
        bar.setProgress(max > min ? Math.max(0, Math.min(1, (v - min) / (max - min))) : 0);
        value.setText(TextTile.number(v, config.option("format")));
    }
}
