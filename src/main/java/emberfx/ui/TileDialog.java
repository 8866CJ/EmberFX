package emberfx.ui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import emberfx.Setting;
import emberfx.tiles.TileConfig;
import emberfx.tiles.TileType;
import emberfx.tiles.TileTypes;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Add tile / Edit tile dialog: topic (typed or browsed), tile type, title, and the type's options.
 * Picking a topic fills in a matching tile type and title, unless you already chose them.
 */
public final class TileDialog {
    /** Tile types that work without a topic: the readiness check finds its own, Preferences defaults to /Preferences, a camera can use a URL. */
    private static final Set<String> TOPIC_OPTIONAL = Set.of("readiness", "preferences", "camera");

    private final ObjectNode json;
    private final TextField topic = new TextField();
    private final TextField title = new TextField();
    private final ComboBox<TileType> type = new ComboBox<>();
    private final Label description = new Label();
    private final GridPane options = Forms.grid();
    private List<Supplier<String>> optionValues = new ArrayList<>();
    private boolean typeChosen, titleChosen; // set once the user picks these, so browsing won't overwrite them

    private TileDialog(ObjectNode json) {
        this.json = json;
    }

    /** Edit a tile's config in place. Returns true if the user pressed OK. */
    public static boolean edit(ObjectNode json, boolean isNew) {
        return new TileDialog(json).show(isNew);
    }

    private boolean show(boolean isNew) {
        TileType current = TileTypes.find(json.path("type").asText("text"));
        type.getItems().setAll(TileTypes.all());
        type.setCellFactory(v -> typeCell());
        type.setButtonCell(typeCell());
        type.setValue(current != null ? current : TileTypes.find("text"));
        topic.setText(json.path("topic").asText(""));
        title.setText(json.path("title").asText(""));
        typeChosen = !isNew || json.has("type"); // a type dragged from the Tiles panel counts as chosen
        titleChosen = !isNew;
        showOptions();
        type.valueProperty().addListener((obs, o, n) -> { typeChosen = true; showOptions(); });
        title.textProperty().addListener((obs, o, n) -> titleChosen = true);

        Button browse = new Button("Browse...");
        browse.setOnAction(e -> pickTopic());
        HBox.setHgrow(topic, Priority.ALWAYS);
        topic.setPromptText("/SmartDashboard/MyValue");
        topic.setPrefColumnCount(28);

        GridPane main = Forms.grid();
        main.addRow(0, new Label("Topic"), new HBox(6, topic, browse));
        main.addRow(1, new Label("Tile type"), type);
        main.add(description, 1, 2);
        main.addRow(3, new Label("Title"), title);
        description.setWrapText(true);
        description.setMaxWidth(420);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(isNew ? "Add tile" : "Edit tile");
        d.setHeaderText(null);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().setContent(new VBox(14, main, options));
        // Most tiles start from a topic, so a new tile opens the browser first. Cancelling it cancels the tile.
        if (isNew && !TOPIC_OPTIONAL.contains(type.getValue().id()) && !pickTopic()) return false;

        while (true) {
            if (d.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return false;
            String problem = problem();
            if (problem == null) break;
            Forms.error(problem);
        }
        save();
        return true;
    }

    /** Open the topic browser and fill in the picked topic. Returns false when the user cancelled it. */
    private boolean pickTopic() {
        return TopicBrowser.pick().map(t -> {
            if (t == TopicBrowser.TYPE_IT) { // straight to the form, to type it
                topic.requestFocus();
                return true;
            }
            topic.setText(t.path());
            if (!titleChosen) { title.setText(t.name()); titleChosen = false; }
            if (!typeChosen) { type.setValue(TileTypes.suggest(t)); typeChosen = false; }
            return true;
        }).orElse(false);
    }

    /** What's wrong with the form, or null if it can be saved. */
    private String problem() {
        if (topic.getText().isBlank() && !TOPIC_OPTIONAL.contains(type.getValue().id())) return "Pick or type a topic.";
        List<Setting> opts = type.getValue().options();
        for (int i = 0; i < opts.size(); i++) {
            String v = optionValues.get(i).get().trim();
            if (opts.get(i).kind() != Setting.Kind.NUMBER || v.isEmpty()) continue;
            try {
                Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return opts.get(i).label() + " must be a number.";
            }
        }
        return null;
    }

    private void save() {
        TileType t = type.getValue();
        json.put("type", t.id()).put("topic", topic.getText().trim())
            .put("title", !title.getText().isBlank() ? title.getText().trim()
                : topic.getText().isBlank() ? t.name() : lastPart(topic.getText()));
        ObjectNode opts = json.putObject("options");
        for (int i = 0; i < t.options().size(); i++) opts.put(t.options().get(i).key(), optionValues.get(i).get().trim());
    }

    /** Rebuild the option rows for the chosen tile type, keeping saved values where they apply. */
    private void showOptions() {
        TileType t = type.getValue();
        description.setText(t.description());
        TileConfig saved = new TileConfig(json, t);
        List<Forms.Field> fields = new ArrayList<>();
        for (Setting s : t.options()) fields.add(Forms.of(s, saved.option(s.key())));
        options.getChildren().clear();
        optionValues = Forms.addTo(options, 0, fields);
        if (options.getScene() != null) options.getScene().getWindow().sizeToScene();
    }

    private static ListCell<TileType> typeCell() {
        return new ListCell<>() {
            @Override protected void updateItem(TileType t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : t.name());
            }
        };
    }

    private static String lastPart(String path) {
        String p = path.trim().replaceAll("/+$", ""); // a table typed with a trailing slash
        return p.substring(p.lastIndexOf('/') + 1);
    }
}
