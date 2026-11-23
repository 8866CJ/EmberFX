package frcdash.ui;

import frcdash.topics.FoundTopic;
import frcdash.topics.Topics;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Side panel listing every topic. Drag one onto a tab to add a matching tile there,
 * or double-click to add it to the first free spot.
 */
final class TopicsPanel extends VBox {
    private final TopicList list = new TopicList(true);
    private final Label summary = new Label();

    TopicsPanel(Consumer<FoundTopic> add) {
        super(8);
        Label title = new Label("Topics");
        title.getStyleClass().add("panel-title");
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        summary.setWrapText(true);
        summary.getStyleClass().add("muted");
        Label hint = new Label("Drag a topic onto the tab, or double-click it.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");

        list.onChoose(add);
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(header, summary, list, hint);
        setPadding(new Insets(10));
        setPrefWidth(300);
        getStyleClass().add("card");
        BorderPane.setMargin(this, new Insets(0, 0, 0, 10)); // space between the tab and the panel
    }

    void refresh() {
        var topics = Topics.all();
        list.setTopics(topics);
        summary.setText(TopicBrowser.summary(topics));
    }
}
