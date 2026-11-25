package emberfx.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Locale;

/**
 * Pop-up messages in the bottom-right corner, newest at the bottom.
 *
 * Robot code sends them through Elastic's notification topic, so robot code written for Elastic
 * (ElasticLib's Elastic.sendNotification) works unchanged. The value is JSON:
 * {"level": "INFO" | "WARNING" | "ERROR", "title": ..., "description": ..., "displayTime": ms (0 = until closed),
 * "width": px, "height": px (-1 = automatic)}.
 */
final class Notifications extends VBox {
    /** The topic ElasticLib publishes notifications to. */
    static final String TOPIC = "/Elastic/RobotNotifications";
    private static final int MAX_SHOWN = 4;
    private static final double DEFAULT_WIDTH = 340;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(Notifications.class.getName());

    private static final String ERROR_ICON = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM9 9l6 6M15 9l-6 6",
        WARNING_ICON = "M12 3.5L22 20H2zM12 10v4M12 17h.01",
        INFO_ICON = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM12 11v5M12 8h.01";

    /** One parsed notification. */
    record Message(String level, String title, String description, long displayMillis, double width) {}

    Notifications() {
        super(8);
        getStyleClass().add("notifications");
        setAlignment(Pos.BOTTOM_RIGHT);
        setPickOnBounds(false); // clicks between toasts reach the dashboard underneath
        setPadding(new Insets(16));
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    /** Show one notification from its JSON text. Bad JSON is logged and ignored. */
    void showJson(String json) {
        try {
            show(parse(json));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring a robot notification that isn't valid JSON: " + json, e);
        }
    }

    void show(Message m) {
        SVGPath icon = Style.icon(switch (m.level()) {
            case "ERROR" -> ERROR_ICON;
            case "WARNING" -> WARNING_ICON;
            default -> INFO_ICON;
        });
        icon.getStyleClass().add("notification-icon-" + m.level().toLowerCase(Locale.ROOT));

        Label title = Style.styled(new Label(m.title()), "card-title");
        Label description = Style.styled(new Label(m.description()), "muted");
        title.setWrapText(true);
        description.setWrapText(true);
        description.setManaged(!m.description().isEmpty());
        description.setVisible(!m.description().isEmpty());
        Button close = new Button("x");
        close.getStyleClass().setAll("notification-close");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, icon, title, spacer, close);
        top.setAlignment(Pos.TOP_LEFT);
        VBox toast = Style.styled(new VBox(4, top, description), "notification");
        toast.setPrefWidth(m.width());
        toast.setMaxWidth(m.width());
        description.setPadding(new Insets(0, 0, 0, 26)); // line up with the title, past the icon

        close.setOnAction(e -> getChildren().remove(toast));
        getChildren().add(toast);
        if (getChildren().size() > MAX_SHOWN) getChildren().remove(0);
        if (m.displayMillis() > 0) {
            PauseTransition timer = new PauseTransition(Duration.millis(m.displayMillis()));
            timer.setOnFinished(e -> getChildren().remove(toast));
            timer.play();
        }
    }

    /** Elastic's JSON, with its defaults for missing fields. */
    static Message parse(String json) throws IOException {
        JsonNode n = JSON.readTree(json);
        if (n == null || !n.isObject()) throw new IOException("not a JSON object");
        String level = n.path("level").asText("INFO").toUpperCase(Locale.ROOT);
        double width = n.path("width").asDouble(-1);
        return new Message(level.equals("ERROR") || level.equals("WARNING") ? level : "INFO",
            n.path("title").asText(""), n.path("description").asText(""),
            n.path("displayTime").asLong(3000), width > 0 ? width : DEFAULT_WIDTH);
    }
}
