package emberfx.ui;

import emberfx.NT;
import emberfx.config.Config;
import emberfx.topics.FoundTopic;
import emberfx.topics.LiveTopics;
import emberfx.topics.TopicCheck;
import emberfx.topics.Topics;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * "Check topics": what the robot code mentions versus what the robot publishes.
 * Lists missing, unexpected and type-mismatched topics, plus the ones the dashboard is meant to write.
 * Scanning runs off the UI thread; Refresh scans again.
 */
public final class TopicCheckDialog {
    private static final System.Logger LOG = System.getLogger(TopicCheckDialog.class.getName());

    private final VBox body = new VBox(10);
    private final Button refresh = new Button("Refresh");
    private final Label status = Style.styled(new Label(), "muted");

    private TopicCheckDialog() {}

    public static void show() {
        TopicCheckDialog c = new TopicCheckDialog();
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Check topics");
        d.setHeaderText("Topic check");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Label intro = Style.styled(new Label("Compares the topic names written in your robot code with what the robot is "
            + "publishing now. Names built from constants are not seen in the code."), "muted");
        intro.setWrapText(true);
        intro.setMaxWidth(640);
        c.refresh.setOnAction(e -> c.run());
        d.getDialogPane().setContent(new VBox(10, intro, new HBox(10, c.refresh, c.status), c.body));
        d.getDialogPane().setPrefWidth(680);
        c.run();
        d.showAndWait();
    }

    private void run() {
        boolean linked = !Config.robotProject().isEmpty(), connected = NT.isConnected();
        body.getChildren().clear();
        if (!linked || !connected) {
            status.setText("");
            String why = !linked && !connected ? "No robot project is linked and the robot is not connected."
                : !linked ? "No robot project is linked. Use Menu > Link robot project."
                : "The robot is not connected, so there is nothing to compare with.";
            body.getChildren().add(Style.styled(new Label(why), "muted"));
            return;
        }
        refresh.setDisable(true);
        status.setText("Scanning...");
        Thread t = new Thread(() -> {
            TopicCheck.Result r = null;
            try {
                r = TopicCheck.compare(Topics.fromProject(), LiveTopics.list());
            } catch (RuntimeException e) { // r stays null and the dialog says the check failed
                LOG.log(System.Logger.Level.WARNING, "Topic check failed", e);
            }
            TopicCheck.Result result = r;
            Platform.runLater(() -> showResult(result));
        }, "topic-check");
        t.setDaemon(true);
        t.start();
    }

    private void showResult(TopicCheck.Result r) {
        refresh.setDisable(false);
        status.setText("");
        body.getChildren().clear();
        if (r == null) {
            body.getChildren().add(Style.styled(new Label("The check failed. Try Refresh."), "muted"));
            return;
        }
        body.getChildren().addAll(
            section("Missing", "In your code but not on the robot.", r.missing(), t -> row(t, type(t))),
            section("Unexpected", "On the robot but not in your code.", r.unexpected(), t -> row(t, type(t))),
            section("Type mismatch", "Code and robot disagree on the type.", r.mismatched(),
                m -> row(m.code(), "code " + type(m.code()) + ", robot " + type(m.live()))));
        if (!r.waiting().isEmpty()) {
            body.getChildren().add(section("Waiting for the dashboard", "The robot reads these; a tile writes them.",
                r.waiting(), t -> row(t, type(t))));
        }
        if (body.getScene() != null) body.getScene().getWindow().sizeToScene(); // the dialog was sized for "Scanning..."
    }

    private static <T> VBox section(String title, String hint, List<T> items, Function<T, HBox> rows) {
        Label head = Style.styled(new Label(title + " (" + items.size() + ")"), "panel-title");
        Label sub = Style.styled(new Label(hint), "muted");
        ListView<T> list = new ListView<>();
        list.getItems().setAll(items);
        list.setPlaceholder(Style.styled(new Label("None"), "muted"));
        list.setPrefHeight(items.isEmpty() ? 60 : Math.min(150, 20 + items.size() * 46));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : rows.apply(item));
            }
        });
        return new VBox(4, head, sub, list);
    }

    /** Path over a muted line with the type(s) and where the code mentions it. */
    private static HBox row(FoundTopic t, String types) {
        String where = t.source().equals("robot") ? "" : "  " + t.source();
        Label detail = Style.styled(new Label(types + where), "cell-detail");
        HBox row = new HBox(6, new VBox(1, new Label(t.path()), detail));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String type(FoundTopic t) {
        return t.type().isEmpty() ? "?" : t.type();
    }
}
