package emberfx.tiles;

import edu.wpi.first.networktables.BooleanSubscriber;
import emberfx.NT;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

/** A box that fills with one color when true and another when false. */
public final class BooleanTile extends Tile {
    private final BooleanSubscriber sub;
    private final Label box = new Label();

    public BooleanTile(TileConfig config) {
        super(config);
        sub = track(NT.subBool(config.topic(), false));
        box.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        box.getStyleClass().add("boolean-box");
        box.setAlignment(Pos.CENTER);
        setCenter(box);
    }

    @Override
    public void update() {
        boolean on = sub.get();
        box.setText(config.option(on ? "trueText" : "falseText"));
        box.setStyle("-fx-background-color: " + config.option(on ? "trueColor" : "falseColor") + ";"); // colors are tile options
    }
}
