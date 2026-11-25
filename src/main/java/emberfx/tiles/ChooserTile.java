package emberfx.tiles;

import edu.wpi.first.networktables.StringArraySubscriber;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.networktables.StringSubscriber;
import emberfx.NT;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;

/**
 * Dropdown for a robot SendableChooser (SmartDashboard.putData("Auto", chooser)).
 * Reads <topic>/options and <topic>/active, and writes the pick to <topic>/selected.
 */
public final class ChooserTile extends Tile {
    private final ComboBox<String> box = new ComboBox<>();
    private final Label active = new Label();
    private final StringArraySubscriber options;
    private final StringSubscriber activeSub;
    private final LazyPublisher<StringPublisher> selected =
        track(new LazyPublisher<>(() -> NT.instance().getStringTopic(config.topic() + "/selected").publish()));
    private String[] shown = new String[0];
    private boolean following;

    public ChooserTile(TileConfig config) {
        super(config);
        options = track(NT.instance().getStringArrayTopic(config.topic() + "/options").subscribe(new String[0]));
        activeSub = track(NT.instance().getStringTopic(config.topic() + "/active").subscribe(""));
        box.setMaxWidth(Double.MAX_VALUE);
        box.valueProperty().addListener((obs, o, v) -> {
            if (following || v == null) return;
            selected.get().set(v);
        });
        active.getStyleClass().add("muted");
        setCenter(new VBox(6, box, active));
    }

    @Override
    public void update() {
        String[] now = options.get();
        String robotPick = activeSub.get();
        following = true;
        if (!Arrays.equals(now, shown)) {
            shown = now;
            box.getItems().setAll(List.of(now));
        }
        if (!box.isShowing() && !robotPick.isEmpty() && !robotPick.equals(box.getValue())) box.setValue(robotPick);
        following = false;
        active.setText(robotPick.isEmpty() ? "No chooser at this topic yet" : "Robot is using: " + robotPick);
    }
}
