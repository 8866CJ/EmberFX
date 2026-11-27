package emberfx.ui;

import emberfx.NT;
import emberfx.app.SessionLog;
import emberfx.config.Config;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The bar above the page: the tab's name (with an Editing badge in edit mode), team module items,
 * the REC label while a log file is being written, the robot address picker and the connection pill.
 */
final class HeaderBar extends HBox {
    private static final String LOCALHOST = "127.0.0.1";
    private static final String CUSTOM = "Custom...";
    private static final PseudoClass CONNECTED = PseudoClass.getPseudoClass("connected");
    private static final char RECORD_DOT = (char) 0x25CF;

    private final Label recording = Style.styled(new Label(), "recording");
    private final Label status = Style.styled(new Label("Disconnected"), "status-pill");

    private final Label pageTitle = Style.styled(new Label(), "page-title");
    private final Label editing = Style.styled(new Label("Editing"), "badge");
    private static final PseudoClass REPLAY = PseudoClass.getPseudoClass("replay");
    private final ComboBox<String> target;
    private final TextField customHost;
    private Runnable connect;
    private boolean replaying;

    HeaderBar(Node moduleItems) {
        getStyleClass().add("toolbar-bar");
        recording.setTooltip(new Tooltip("Logs save to " + SessionLog.DIR));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField customHost = new TextField();
        target = robotPicker(customHost);
        this.customHost = customHost;
        getChildren().addAll(pageTitle, editing, spacer, moduleItems, recording, customHost, target, status);

        NT.onConnectionChange(connected -> Platform.runLater(() -> {
            if (replaying) return; // the pill says Replay until the replay ends
            status.setText(connected ? "Connected" : "Disconnected");
            status.pseudoClassStateChanged(CONNECTED, connected);
        }));
    }

    /** Show which tab is up and whether the layout can be edited. */
    void setPage(String name, boolean editable) {
        pageTitle.setText(name);
        editing.setVisible(editable);
    }

    /**
     * Replay mode on (with the log's name) or off (null). While on, the robot picker is disabled and the
     * pill says Replay; turning it off reconnects to the robot picked in the picker.
     */
    void setReplay(String source) {
        replaying = source != null;
        target.setDisable(replaying);
        customHost.setDisable(replaying);
        status.pseudoClassStateChanged(REPLAY, replaying);
        status.pseudoClassStateChanged(CONNECTED, false);
        status.setText(replaying ? "Replay" : "Disconnected");
        status.setTooltip(replaying ? new Tooltip("Replaying " + source) : null);
        if (!replaying) connect.run();
    }

    /** Refresh the REC label. Called every frame. */
    void update() {
        String file = SessionLog.currentFile();
        recording.setText(file == null ? "" : RECORD_DOT + " REC " + file);
    }

    /**
     * The robot address picker: the team's roboRIO, the local simulator, or a typed address.
     * Choosing an entry connects right away; a custom address connects when Enter is pressed.
     */
    private ComboBox<String> robotPicker(TextField customHost) {
        String robot = DashboardView.robotHost(Config.team());
        ComboBox<String> target = new ComboBox<>(FXCollections.observableArrayList(
            robot.isEmpty() ? "Robot (set team in Settings)" : "Physical Robot (" + robot + ")", "Localhost (" + LOCALHOST + ")", CUSTOM));
        target.setPrefWidth(230);
        customHost.setPromptText("Enter host, ex: " + (robot.isEmpty() ? "10.0.0.2" : robot));
        customHost.setPrefWidth(200);
        customHost.managedProperty().bind(customHost.visibleProperty());
        customHost.setVisible(false);

        connect = () -> {
            int i = target.getSelectionModel().getSelectedIndex();
            String host = i == 0 ? robot : i == 1 ? LOCALHOST : customHost.getText().trim();
            if (!host.isEmpty()) NT.startClient(host);
        };
        target.valueProperty().addListener((obs, oldValue, newValue) -> {
            customHost.setVisible(CUSTOM.equals(newValue));
            if (customHost.isVisible()) customHost.requestFocus(); // type the address, then Enter connects
            else connect.run();
        });
        customHost.setOnAction(e -> connect.run());
        target.getSelectionModel().select(0);
        return target;
    }
}
