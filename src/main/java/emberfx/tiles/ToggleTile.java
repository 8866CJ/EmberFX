package emberfx.tiles;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import emberfx.NT;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;

/** Turns a boolean on the robot on and off: a wide switch whose thumb slides right and turns green when on. */
public final class ToggleTile extends Tile {
    private final ToggleButton toggle = new ToggleButton();
    private final Label thumb = new Label();
    private final BooleanSubscriber sub;
    private BooleanPublisher pub; // created on first click

    public ToggleTile(TileConfig config) {
        super(config);
        sub = track(NT.subBool(config.topic(), false));
        toggle.getStyleClass().setAll("switch"); // drop the normal button look
        thumb.getStyleClass().add("switch-thumb");
        toggle.setGraphic(thumb);
        toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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
        toggle.setAlignment(on ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        thumb.setText(config.option(on ? "onText" : "offText"));
    }
}
