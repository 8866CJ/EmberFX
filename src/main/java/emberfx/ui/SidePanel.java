package emberfx.ui;

import emberfx.tiles.TileType;
import emberfx.tiles.TileTypes;
import emberfx.topics.FoundTopic;
import emberfx.topics.Topics;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * The panel left of the tab while editing. "Tiles" lists every tile type and "Topics" every topic;
 * drag either onto the tab to add a tile there, or double-click to add it to the first free spot.
 */
final class SidePanel extends BorderPane {
    private static TileType dragged; // the tile type being dragged right now, or null

    private final TopicList topicList = new TopicList(true);
    private final Label summary = Style.styled(new Label(), "muted");
    private final ToggleButton topicsTab = tab("Topics");

    SidePanel(Consumer<TileType> addTile, Consumer<FoundTopic> addTopic) {
        getStyleClass().add("side-panel");
        setMinWidth(220);
        setPrefWidth(300);

        VBox tiles = body(heading("Tiles", null), hint("Drag a tile onto the tab, or double-click it."), gallery(addTile));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        summary.setWrapText(true);
        topicList.onChoose(addTopic);
        VBox topics = body(heading("Topics", refresh), summary, topicList, hint("Drag a topic onto the tab, or double-click it."));

        ToggleButton tilesTab = tab("Tiles");
        ToggleGroup group = new ToggleGroup();
        tilesTab.setToggleGroup(group);
        topicsTab.setToggleGroup(group);
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) old.setSelected(true); // one is always selected
            else setCenter(now == tilesTab ? tiles : topics);
            if (now == topicsTab) refresh();
        });
        topicsTab.setSelected(true);
        HBox tabs = Style.styled(new HBox(tilesTab, topicsTab), "side-tabs");
        setBottom(tabs);
    }

    /** The tile type being dragged from the Tiles list, or null. */
    static TileType dragged() { return dragged; }

    void refresh() {
        var topics = Topics.all();
        topicList.setTopics(topics);
        summary.setText(TopicBrowser.summary(topics));
    }

    private static ListView<TileType> gallery(Consumer<TileType> addTile) {
        ListView<TileType> list = new ListView<>();
        list.getItems().setAll(TileTypes.all());
        list.setCellFactory(v -> new ListCell<>() {
            private final Label name = Style.styled(new Label(), "card-title");
            private final Label description = Style.styled(new Label(), "cell-detail");
            private final VBox box = new VBox(2, name, description);

            {
                description.setWrapText(true);
                description.maxWidthProperty().bind(list.widthProperty().subtract(40));
                setOnDragDetected(e -> {
                    if (getItem() == null) return;
                    dragged = getItem();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(getItem().id());
                    startDragAndDrop(TransferMode.COPY).setContent(content);
                    e.consume();
                });
                setOnDragDone(e -> dragged = null);
            }

            @Override protected void updateItem(TileType t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setGraphic(null);
                    return;
                }
                name.setText(t.name());
                description.setText(t.description());
                setGraphic(box);
            }
        });
        list.setOnMouseClicked(e -> {
            TileType t = list.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && t != null) addTile.accept(t);
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        return list;
    }

    private static VBox body(javafx.scene.Node... children) {
        return Style.styled(new VBox(children), "side-panel-body");
    }

    private static HBox heading(String text, Button action) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, Style.styled(new Label(text), "panel-title"), spacer);
        if (action != null) row.getChildren().add(action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label hint(String text) {
        Label l = Style.styled(new Label(text), "muted");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE); // wrap instead of being squeezed by the list
        return l;
    }

    private static ToggleButton tab(String text) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().setAll("side-tab");
        return b;
    }
}
