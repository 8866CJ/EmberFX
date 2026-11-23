package frcdash.tiles;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import frcdash.NT;
import javafx.scene.control.ToggleButton;

/** Turns a boolean on the robot on and off. Shows the robot's current value. */
public final class ToggleTile extends Tile {
    private final ToggleButton toggle = new ToggleButton();
    private final BooleanSubscriber sub;
    private BooleanPublisher pub; // created on first click

    public ToggleTile(TileConfig config) {
        super(config);
        sub = track(NT.subBool(config.topic(), false));
        toggle.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        toggle.getStyleClass().addAll("tile-control", "toggle-tile");
        toggle.setMnemonicParsing(false);
        toggle.setOnAction(e -> {
            if (pub == null) pub = track(NT.pubBool(config.topic()));
            pub.set(!sub.get());
        });
        setCenter(toggle);
    }

    @Override
    public void update() {
        boolean on = sub.get();
        toggle.setSelected(on);
        toggle.setText(config.option(on ? "onText" : "offText"));
    }
}
