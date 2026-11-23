package emberfx.tiles;

import emberfx.ui.Style;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of true/false checks, like "Subsystems ready". Point it at a table of booleans
 * (e.g. /SmartDashboard/Ready, holding Drivetrain, Intake, Shooter) or at one boolean.
 */
public final class ChecklistTile extends Tile {
    static final String CHECKED = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM8 12.5l3 3 5-6";
    static final String UNCHECKED = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z";
    private static final String GREEN = "-fx-stroke: #22c55e;";

    private final TopicGroup group;
    private final VBox rows = new VBox(6);
    private final List<SVGPath> icons = new ArrayList<>();

    public ChecklistTile(TileConfig config) {
        super(config);
        group = new TopicGroup(config.topic(), this::track);
        setCenter(rows);
    }

    @Override
    public void update() {
        List<TopicGroup.Entry> entries = group.entries();
        if (group.refresh()) {
            rows.getChildren().clear();
            icons.clear();
            for (TopicGroup.Entry e : entries) {
                SVGPath icon = Style.icon(UNCHECKED);
                icons.add(icon);
                HBox row = new HBox(6, icon, new Label(e.name()));
                row.setAlignment(Pos.CENTER_LEFT);
                rows.getChildren().add(row);
            }
        }
        for (int i = 0; i < icons.size(); i++) {
            boolean on = TopicGroup.bool(entries.get(i));
            SVGPath icon = icons.get(i);
            icon.setContent(on ? CHECKED : UNCHECKED);
            icon.setStyle(on ? GREEN : "");
        }
    }
}
