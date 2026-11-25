package emberfx.tiles;

import edu.wpi.first.networktables.StringArraySubscriber;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * The active alerts in a WPILib Alert group (new Alert("Alerts", "Gyro not found", AlertType.kError)
 * shows up under /SmartDashboard/Alerts): errors, then warnings, then infos, each with its icon.
 */
public final class AlertsTile extends Tile {
    private static final String ERROR = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM9 9l6 6M15 9l-6 6",
        WARNING = "M12 3.5L22 20H2zM12 10v4M12 17h.01",
        INFO = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM12 11v5M12 8h.01";

    private final StringArraySubscriber errors, warnings, infos;
    private final VBox rows = new VBox(6);
    private long stamp = -1;

    public AlertsTile(TileConfig config) {
        super(config);
        errors = track(NT.instance().getStringArrayTopic(config.topic() + "/errors").subscribe(new String[0]));
        warnings = track(NT.instance().getStringArrayTopic(config.topic() + "/warnings").subscribe(new String[0]));
        infos = track(NT.instance().getStringArrayTopic(config.topic() + "/infos").subscribe(new String[0]));
        setCenter(rows);
    }

    @Override
    public void update() {
        long change = changeStamp(errors, warnings, infos) + (errors.exists() ? 1 : 0); // appearing counts as a change
        if (change == stamp) return;
        stamp = change;
        String[][] now = {errors.get(), warnings.get(), infos.get()};
        rows.getChildren().clear();
        add(now[0], ERROR, "#dc2626");
        add(now[1], WARNING, "#d97706");
        add(now[2], INFO, "#0284c7");
        int count = rows.getChildren().size();
        if (count == 0) rows.getChildren().add(Style.styled(new Label(errors.exists() ? "No alerts" : "No alerts at this topic yet"), "muted"));
        setHeaderValue(count == 0 ? "" : String.valueOf(count));
    }

    private void add(String[] items, String iconPath, String color) {
        for (String text : items) {
            SVGPath icon = Style.icon(iconPath);
            icon.setStyle("-fx-stroke: " + color + ";");
            Label label = new Label(text);
            label.setWrapText(true);
            HBox row = new HBox(6, icon, label);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
    }
}
