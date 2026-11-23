package frcdash.ui;

import frcdash.topics.FoundTopic;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * A search box over a list of topics. Used by the Browse dialog and the Topics side panel.
 * Items can be dragged onto a tab; the tab reads the topic from dragged().
 */
public final class TopicList extends VBox {
    private static FoundTopic dragged; // the topic being dragged right now, or null

    private final ObservableList<FoundTopic> all = FXCollections.observableArrayList();
    private final FilteredList<FoundTopic> shown = new FilteredList<>(all);
    private final TextField search = new TextField();
    private final ListView<FoundTopic> list = new ListView<>(shown);

    public TopicList(boolean compact) {
        super(6);
        search.setPromptText("Search topics, e.g. shooter");
        search.textProperty().addListener((obs, o, text) -> {
            String q = text.trim().toLowerCase();
            shown.setPredicate(t -> q.isEmpty() || t.path().toLowerCase().contains(q) || t.type().toLowerCase().contains(q));
        });
        list.setCellFactory(v -> new ListCell<>() {
            {
                setOnDragDetected(e -> {
                    if (getItem() == null) return;
                    dragged = getItem();
                    Dragboard db = startDragAndDrop(TransferMode.COPY);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(getItem().path());
                    db.setContent(content);
                    e.consume();
                });
                setOnDragDone(e -> dragged = null);
            }

            @Override protected void updateItem(FoundTopic t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : compact ? compactText(t) : fullText(t));
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(search, list);
    }

    /** The topic being dragged from any TopicList, or null. */
    public static FoundTopic dragged() { return dragged; }

    public void setTopics(List<FoundTopic> topics) { all.setAll(topics); }

    public FoundTopic selected() { return list.getSelectionModel().getSelectedItem(); }

    /** Run when an item is double-clicked. */
    public void onChoose(Consumer<FoundTopic> action) {
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && selected() != null) action.accept(selected());
        });
    }

    public void focusSearch() { search.requestFocus(); }

    public ListView<FoundTopic> listView() { return list; }

    private static String fullText(FoundTopic t) {
        return t.path() + "    " + type(t) + "    " + t.source() + (t.robotReads() ? "  (robot reads this)" : "");
    }

    private static String compactText(FoundTopic t) {
        return t.path() + "\n    " + type(t) + (t.robotReads() ? ", robot reads" : "") + (t.source().startsWith("robot") ? "" : ", in code only");
    }

    private static String type(FoundTopic t) { return t.type().isEmpty() ? "?" : t.type(); }
}
