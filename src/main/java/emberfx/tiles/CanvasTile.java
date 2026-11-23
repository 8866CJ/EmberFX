package emberfx.tiles;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Base for tiles that draw themselves (gauges, field, swerve): a canvas that fills the tile and is
 * cleared and redrawn every frame. Subclasses read their subscribers and paint in {@link #draw}.
 */
public abstract class CanvasTile extends Tile {
    /** Fonts that match the rest of the dashboard. */
    protected static final String SANS = "Segoe UI", MONO = "Consolas";

    private final Canvas canvas = new Canvas();

    protected CanvasTile(TileConfig config) {
        super(config);
        Pane holder = new Pane(canvas);
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());
        holder.setMinSize(0, 0);
        setCenter(holder);
    }

    @Override
    public void update() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        g.clearRect(0, 0, w, h);
        if (w > 0 && h > 0) draw(g, w, h);
    }

    /** Paint the tile into a w x h area that has just been cleared. */
    protected abstract void draw(GraphicsContext g, double w, double h);

    /** Width of text in a font, for lining things up. */
    protected static double textWidth(String s, Font font) {
        Text t = new Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }
}
