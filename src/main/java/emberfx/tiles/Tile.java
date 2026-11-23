package emberfx.tiles;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every tile: a rounded card with a title on top and the tile's content in the center.
 *
 * To make a new kind of tile, extend this, build your controls in the constructor, put them in
 * setCenter(...), and refresh them in update(). Wrap every NetworkTables subscriber and publisher
 * in track(...) so they are closed when the tile is removed. See TextTile for the smallest example.
 */
public abstract class Tile extends BorderPane {
    private static final System.Logger LOG = System.getLogger(Tile.class.getName());

    protected final TileConfig config;
    private final List<AutoCloseable> handles = new ArrayList<>();

    protected Tile(TileConfig config) {
        this.config = config;
        Label title = new Label(config.title());
        title.getStyleClass().add("card-title");
        setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 6, 0));
        setPadding(new Insets(10, 12, 12, 12));
        getStyleClass().add("card");
    }

    /** This tile's saved settings. */
    public final TileConfig config() { return config; }

    /** Called every frame while the tile's tab is showing. Read subscribers and refresh the controls here. */
    public abstract void update();

    /** Remember a subscriber or publisher so it's closed with the tile. Returns it for chaining. */
    protected <T extends AutoCloseable> T track(T handle) {
        handles.add(handle);
        return handle;
    }

    /** Release NetworkTables handles. Called when the tile is removed, edited or its tab closes. */
    public void close() {
        for (AutoCloseable h : handles) {
            try {
                h.close();
            } catch (Exception e) { // keep closing the rest
                LOG.log(System.Logger.Level.DEBUG, "Couldn't close " + h, e);
            }
        }
        handles.clear();
    }

    /** Shown in place of a tile whose type no longer exists (e.g. a team module was removed). Keeps its config. */
    static Tile missing(TileConfig config, String typeId) {
        return new Tile(config) {
            {
                Label l = new Label("Unknown tile type \"" + typeId + "\".\nIs its module installed?");
                l.setWrapText(true);
                l.getStyleClass().add("warning-text");
                setCenter(l);
            }

            @Override public void update() {}
        };
    }
}
