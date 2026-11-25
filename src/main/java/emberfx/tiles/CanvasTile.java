package emberfx.tiles;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Base for tiles that draw themselves (gauges, field, swerve): a canvas that fills the tile.
 * It is redrawn when {@link #needsRedraw} says something changed, or the tile was resized.
 * Subclasses read their subscribers and paint in {@link #draw}.
 */
public abstract class CanvasTile extends Tile {
    /** Fonts that match the rest of the dashboard. */
    protected static final String SANS = "Segoe UI", MONO = "Consolas";

    private final Canvas canvas = new Canvas();
    private double drawnW = -1, drawnH = -1; // size at the last redraw

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
        double w = canvas.getWidth(), h = canvas.getHeight();
        boolean changed = needsRedraw(); // always asked, so subclasses can keep their change markers current
        if (!changed && w == drawnW && h == drawnH) return;
        drawnW = w;
        drawnH = h;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        if (w > 0 && h > 0) draw(g, w, h);
    }

    /**
     * Called once per frame: return false when nothing drawn has changed, to skip the redraw.
     * Resizes always redraw. The default redraws every frame; {@link #changeStamp} makes this cheap.
     */
    protected boolean needsRedraw() { return true; }

    /** Paint the tile into a w x h area that has just been cleared. */
    protected abstract void draw(GraphicsContext g, double w, double h);


    /** Width of text in a font, for lining things up. */
    protected static double textWidth(String s, Font font) {
        Text t = new Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }
}
