package emberfx.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import emberfx.config.Config;
import emberfx.tiles.Tile;
import emberfx.tiles.TileConfig;
import emberfx.tiles.TileType;
import emberfx.tiles.TileTypes;
import emberfx.topics.FoundTopic;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One user tab: a canvas of tiles that can be dragged (snapping to the grid), resized from their
 * edges, edited, duplicated, moved to another tab or removed. Everything is saved to the tab's
 * entry in config.json: {"name": ..., "tiles": [...]}.
 */
public final class TabPage {
    private static final double RESIZE_EDGE = 10, MIN_W = 120, MIN_H = 80;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode json;
    private final DashboardView window;
    private final Pane canvas = new Pane();
    private final StackPane root;
    private final List<Tile> tiles = new ArrayList<>();
    private final Label hint = new Label();

    TabPage(ObjectNode json, DashboardView window) {
        this.json = json;
        this.window = window;

        ScrollPane scroll = new ScrollPane(canvas);
        scroll.getStyleClass().add("tab-scroll");
        // Let the canvas fill the window (so right-click works anywhere) but grow past it to scroll.
        canvas.minWidthProperty().bind(Bindings.createDoubleBinding(() -> scroll.getViewportBounds().getWidth(), scroll.viewportBoundsProperty()));
        canvas.minHeightProperty().bind(Bindings.createDoubleBinding(() -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        canvas.setOnContextMenuRequested(e -> {
            if (e.getTarget() != canvas || Config.locked()) return;
            ObjectNode copied = copiedTile();
            MenuItem paste = Forms.item("Paste tile here", () -> paste(copied, e.getX(), e.getY()));
            paste.setDisable(copied == null);
            new ContextMenu(Forms.item("Add tile here...", () -> addNew(null, e.getX(), e.getY())), paste)
                .show(canvas, e.getScreenX(), e.getScreenY());
        });

        // Topics dragged from the Topics panel become tiles where they're dropped.
        // Tile types dragged from the Tiles panel open the Add tile dialog for that spot.
        canvas.setOnDragOver(e -> {
            if ((TopicList.dragged() != null || SidePanel.dragged() != null) && !Config.locked()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        canvas.setOnDragDropped(e -> {
            FoundTopic t = TopicList.dragged();
            TileType type = SidePanel.dragged();
            if (t != null) addTopic(t, e.getX(), e.getY());
            e.setDropCompleted(t != null || type != null);
            e.consume();
            // After the drop finishes, so the dialog doesn't hold up the drag.
            if (type != null) Platform.runLater(() -> addNew(type, e.getX(), e.getY()));
        });

        hint.getStyleClass().add("empty-hint");
        hint.setMouseTransparent(true);
        hint.visibleProperty().bind(Bindings.isEmpty(canvas.getChildren()));
        root = new StackPane(scroll, hint);

        for (JsonNode n : tilesJson()) if (n instanceof ObjectNode o) show(o);
    }

    public StackPane root() { return root; }

    public String name() { return json.path("name").asText("Tab"); }

    void rename(String name) { json.put("name", name); }

    ObjectNode json() { return json; }

    private ArrayNode tilesJson() { return Config.array(json, "tiles"); }

    /** Refresh every tile. A tile that throws is stopped and reported once, the rest keep going. */
    void update() {
        for (Tile t : List.copyOf(tiles)) {
            try {
                t.update();
            } catch (RuntimeException e) {
                tiles.remove(t);
                window.warn("The \"" + t.config().title() + "\" tile stopped updating: " + e);
            }
        }
    }

    void close() { tiles.forEach(Tile::close); }

    /** In edit mode the canvas shows the snap grid as dots, and the empty-tab hint says how to add tiles. */
    void setEditing(boolean editing) {
        canvas.setBackground(editing ? dots() : null);
        hint.setText(editing
            ? "Drag a tile or a topic here from the panel on the left,\nor right-click to add a tile."
            : "This tab is empty.\nClick the pencil on the left (or press Ctrl+E) to add tiles.");
    }

    /** Small dots on the snap grid, spaced at least 16 px apart. */
    private static Background dots() {
        int grid = Config.gridSize(), gap = grid * (int) Math.ceil(16.0 / grid);
        WritableImage img = new WritableImage(gap, gap);
        Color dot = Color.web("#334155");
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) img.getPixelWriter().setColor(x, y, dot);
        return new Background(new BackgroundFill(new ImagePattern(img, 0, 0, gap, gap, false), null, null));
    }

    // ---- adding, editing, removing ----

    /** Ask for a new tile (of the given type, or any when null) and put it at (x, y), or the first free spot when x is NaN. */
    void addNew(TileType type, double x, double y) {
        ObjectNode t = tilesJson().addObject();
        if (type != null) t.put("type", type.id());
        if (!TileDialog.edit(t, true)) {
            removeJson(t);
            return;
        }
        place(t, TileTypes.find(t.path("type").asText()), x, y);
    }

    /** Add a tile for a topic right away, with the tile type that suits it. Same placement as addNew. */
    void addTopic(FoundTopic topic, double x, double y) {
        TileType type = TileTypes.suggest(topic);
        ObjectNode t = tilesJson().addObject().put("type", type.id()).put("topic", topic.path()).put("title", topic.name());
        place(t, type, x, y);
    }

    private void place(ObjectNode t, TileType type, double x, double y) {
        double[] spot = Double.isNaN(x) ? freeSpot(type.width(), type.height()) : new double[] {snap(x), snap(y)};
        new TileConfig(t, type).setBounds(spot[0], spot[1], type.width(), type.height());
        show(t);
        Config.save();
    }

    private void edit(Tile tile) {
        if (!TileDialog.edit(tile.config().json(), false)) return;
        replace(tile);
        Config.save();
    }

    private void duplicate(Tile tile) {
        ObjectNode copy = tile.config().json().deepCopy();
        double step = Math.max(20, Config.gridSize() * 2);
        copy.put("x", copy.path("x").asDouble() + step).put("y", copy.path("y").asDouble() + step);
        tilesJson().add(copy);
        show(copy);
        Config.save();
    }

    private void remove(Tile tile) {
        if (!Forms.confirm("Remove the \"" + tile.config().title() + "\" tile?")) return;
        removeJson(tile.config().json());
        hide(tile);
        Config.save();
    }

    /** Put a tile's settings on the clipboard as JSON, so it can be pasted on any tab, or sent to another computer. */
    private static void copy(Tile tile) {
        ClipboardContent content = new ClipboardContent();
        content.putString(tile.config().json().toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** The tile on the clipboard, or null when the clipboard holds something else. */
    private static ObjectNode copiedTile() {
        String text = Clipboard.getSystemClipboard().getString();
        return text == null ? null : tileFromJson(text);
    }

    /** A tile's settings from copied text: a JSON object with at least a type and a topic. Null otherwise. */
    static ObjectNode tileFromJson(String text) {
        try {
            JsonNode n = JSON.readTree(text);
            return n instanceof ObjectNode o && o.path("type").isTextual() && o.path("topic").isTextual() ? o : null;
        } catch (IOException e) { // not JSON: the clipboard holds ordinary text
            return null;
        }
    }

    private void paste(ObjectNode copied, double x, double y) {
        ObjectNode t = copied.deepCopy();
        tilesJson().add(t);
        TileType type = TileTypes.find(t.path("type").asText());
        double w = t.path("w").asDouble(type == null ? MIN_W : type.width()), h = t.path("h").asDouble(type == null ? MIN_H : type.height());
        t.put("x", snap(x)).put("y", snap(y)).put("w", w).put("h", h);
        show(t);
        Config.save();
    }

    private void moveTo(Tile tile, TabPage other) {
        ObjectNode t = tile.config().json();
        removeJson(t);
        hide(tile);
        other.tilesJson().add(t);
        other.show(t);
        Config.save();
    }

    /** Rebuild a tile after its config changed. */
    private void replace(Tile tile) {
        hide(tile);
        show(tile.config().json());
    }

    private void show(ObjectNode t) {
        Tile tile = TileTypes.create(t);
        TileConfig c = tile.config();
        tile.setLayoutX(c.x());
        tile.setLayoutY(c.y());
        tile.setPrefSize(c.width(), c.height());
        tile.setMinSize(MIN_W, MIN_H);
        makeDraggable(tile);
        tile.setOnContextMenuRequested(e -> {
            if (Config.locked()) return;
            menuFor(tile).show(tile, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        tiles.add(tile);
        canvas.getChildren().add(tile);
    }

    private void hide(Tile tile) {
        tile.close();
        tiles.remove(tile);
        canvas.getChildren().remove(tile);
    }

    private void removeJson(ObjectNode t) {
        ArrayNode a = tilesJson();
        for (int i = 0; i < a.size(); i++) if (a.get(i) == t) { a.remove(i); return; }
    }

    private ContextMenu menuFor(Tile tile) {
        Menu move = new Menu("Move to tab");
        for (TabPage other : window.tabs()) if (other != this) move.getItems().add(Forms.item(other.name(), () -> moveTo(tile, other)));
        move.setDisable(move.getItems().isEmpty());
        return new ContextMenu(
            Forms.item("Edit...", () -> edit(tile)),
            Forms.item("Duplicate", () -> duplicate(tile)),
            Forms.item("Copy", () -> copy(tile)),
            move,
            Forms.item("Remove", () -> remove(tile)));
    }

    // ---- layout ----

    private static double snap(double v) {
        int g = Config.gridSize();
        return Math.max(0, Math.round(v / g) * g);
    }

    /** First spot, scanning rows from the top left, where a w x h tile overlaps nothing. */
    private double[] freeSpot(double w, double h) {
        double step = Math.max(10, Config.gridSize());
        double maxX = Math.max(canvas.getWidth(), 1000) - w;
        for (double y = 0; y < 5000; y += step) {
            for (double x = 0; x <= maxX; x += step) {
                boolean free = true;
                for (Tile t : tiles) {
                    if (x < t.getLayoutX() + t.getPrefWidth() && t.getLayoutX() < x + w
                        && y < t.getLayoutY() + t.getPrefHeight() && t.getLayoutY() < y + h) { free = false; break; }
                }
                if (free) return new double[] {x, y};
            }
        }
        return new double[] {0, 0};
    }

    // Drag to move, drag within RESIZE_EDGE px of an edge to resize. Saved on release.
    // Presses on a control inside the tile (button, slider, text box) go to the control instead.
    private void makeDraggable(Tile tile) {
        tile.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY || Config.locked() || onControl(e, tile)) {
                tile.setUserData(null);
                return;
            }
            tile.setUserData(new double[] {
                resizeMask(tile, e.getX(), e.getY()), e.getSceneX(), e.getSceneY(),
                tile.getLayoutX(), tile.getLayoutY(), tile.getPrefWidth(), tile.getPrefHeight()
            });
        });
        tile.setOnMouseDragged(e -> {
            if (!(tile.getUserData() instanceof double[] d)) return;
            int mask = (int) d[0];
            double dx = e.getSceneX() - d[1], dy = e.getSceneY() - d[2];
            if (mask == 0) {
                tile.setLayoutX(snap(d[3] + dx));
                tile.setLayoutY(snap(d[4] + dy));
                return;
            }
            if ((mask & 1) != 0) {
                double left = Math.min(snap(d[3] + dx), d[3] + d[5] - MIN_W);
                tile.setPrefWidth(d[3] + d[5] - left);
                tile.setLayoutX(left);
            }
            if ((mask & 2) != 0) tile.setPrefWidth(Math.max(MIN_W, snap(d[3] + d[5] + dx) - d[3]));
            if ((mask & 4) != 0) {
                double top = Math.min(snap(d[4] + dy), d[4] + d[6] - MIN_H);
                tile.setPrefHeight(d[4] + d[6] - top);
                tile.setLayoutY(top);
            }
            if ((mask & 8) != 0) tile.setPrefHeight(Math.max(MIN_H, snap(d[4] + d[6] + dy) - d[4]));
        });
        tile.setOnMouseReleased(e -> {
            if (!(tile.getUserData() instanceof double[])) return;
            tile.setUserData(null);
            tile.config().setBounds(tile.getLayoutX(), tile.getLayoutY(), tile.getPrefWidth(), tile.getPrefHeight());
            Config.save();
        });
        tile.setOnMouseMoved(e -> tile.setCursor(Config.locked() || onControl(e, tile) ? Cursor.DEFAULT
            : cursorFor(resizeMask(tile, e.getX(), e.getY()))));
        tile.setOnMouseExited(e -> tile.setCursor(Cursor.DEFAULT));
    }

    private static boolean onControl(MouseEvent e, Node tile) {
        for (Node n = e.getPickResult().getIntersectedNode(); n != null && n != tile; n = n.getParent()) {
            if (n instanceof ButtonBase || n instanceof Slider || n instanceof TextInputControl || n instanceof ComboBoxBase) return true;
        }
        return false;
    }

    private static int resizeMask(Tile tile, double x, double y) {
        int mask = 0;
        if (x <= RESIZE_EDGE) mask |= 1;
        if (x >= tile.getWidth() - RESIZE_EDGE) mask |= 2;
        if (y <= RESIZE_EDGE) mask |= 4;
        if (y >= tile.getHeight() - RESIZE_EDGE) mask |= 8;
        return mask;
    }

    private static Cursor cursorFor(int mask) {
        boolean l = (mask & 1) != 0, r = (mask & 2) != 0, t = (mask & 4) != 0, b = (mask & 8) != 0;
        if (l && t) return Cursor.NW_RESIZE;
        if (r && t) return Cursor.NE_RESIZE;
        if (l && b) return Cursor.SW_RESIZE;
        if (r && b) return Cursor.SE_RESIZE;
        if (l || r) return Cursor.H_RESIZE;
        if (t || b) return Cursor.V_RESIZE;
        return Cursor.MOVE;
    }
}
