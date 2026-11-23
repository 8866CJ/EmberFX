package emberfx.tiles;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

import static emberfx.tiles.RadialGaugeTile.LABEL;
import static emberfx.tiles.RadialGaugeTile.LIT;
import static emberfx.tiles.RadialGaugeTile.UNLIT;
import static emberfx.tiles.RadialGaugeTile.VALUE;
import static emberfx.tiles.RadialGaugeTile.WARN;

/**
 * Segmented bars that fill between min and max. Point it at one number for a single bar, or at a
 * table (e.g. /SmartDashboard/Motor Temps) for one labelled bar per number in it. Bars run across
 * or up, and turn red above the warning value.
 */
public final class LinearGaugeTile extends CanvasTile {
    static final String ACROSS = "Across", UP = "Up";
    private static final double CELL = 3, PITCH = 6; // segment width, and segment + gap
    private static final int SCALE_LABELS = 5;

    private final TopicGroup group;

    public LinearGaugeTile(TileConfig config) {
        super(config);
        group = new TopicGroup(config.topic(), this::track);
    }

    @Override
    public void update() {
        group.refresh();
        List<TopicGroup.Entry> e = group.entries();
        setHeaderValue(e.size() == 1 ? TextTile.number(TopicGroup.number(e.get(0)), config.option("format")) : "");
        super.update();
    }

    @Override
    protected void draw(GraphicsContext g, double w, double h) {
        List<TopicGroup.Entry> entries = group.entries();
        if (entries.isEmpty()) return;
        double min = config.optionDouble("min"), max = config.optionDouble("max");
        if (!(max > min)) { min = 0; max = 1; }
        boolean single = entries.size() == 1;
        Font sans = Font.font(SANS, 13), mono = Font.font(MONO, FontWeight.BOLD, 13), small = Font.font(MONO, 10);
        g.setTextBaseline(VPos.CENTER);

        if (!UP.equals(config.option("direction"))) {
            double labelW = 0;
            if (!single) for (TopicGroup.Entry en : entries) labelW = Math.max(labelW, textWidth(en.name(), sans) + 10);
            double valueW = single ? 0 : 48, scaleH = 14;
            double rowH = (h - scaleH) / entries.size(), barH = Math.min(16, rowH - 6);
            double x0 = labelW, barW = w - labelW - valueW;
            int cells = cellCount(barW);
            for (int i = 0; i < entries.size(); i++) {
                TopicGroup.Entry en = entries.get(i);
                double v = TopicGroup.number(en), cy = rowH * i + rowH / 2;
                if (!single) {
                    g.setFill(VALUE);
                    g.setFont(sans);
                    g.setTextAlign(TextAlignment.LEFT);
                    g.fillText(en.name(), 0, cy);
                    g.setFont(mono);
                    g.setTextAlign(TextAlignment.RIGHT);
                    g.fillText(TextTile.number(v, config.option("format")), w, cy);
                }
                int lit = litCells(v, min, max, cells);
                g.setFill(color(v));
                for (int c = 0; c < cells; c++) {
                    if (c == lit) g.setFill(UNLIT);
                    g.fillRect(x0 + c * PITCH, cy - barH / 2, CELL, barH);
                }
            }
            g.setFill(LABEL);
            g.setFont(small);
            for (int k = 0; k < SCALE_LABELS; k++) {
                double frac = k / (double) (SCALE_LABELS - 1), x = x0 + frac * (cells - 1) * PITCH + CELL / 2;
                g.setTextAlign(k == 0 ? TextAlignment.LEFT : k == SCALE_LABELS - 1 ? TextAlignment.RIGHT : TextAlignment.CENTER);
                g.fillText(RadialGaugeTile.tickLabel(min + (max - min) * frac), x, h - scaleH / 2);
            }
        } else {
            double labelH = single ? 0 : 20, valueH = single ? 0 : 20, scaleW = single ? 40 : 0;
            double colW = (w - scaleW) / entries.size(), barW = Math.min(24, colW - 10);
            double barH = h - labelH - valueH;
            int cells = cellCount(barH);
            for (int i = 0; i < entries.size(); i++) {
                TopicGroup.Entry en = entries.get(i);
                double v = TopicGroup.number(en), cx = colW * i + colW / 2;
                if (!single) {
                    g.setTextAlign(TextAlignment.CENTER);
                    g.setFill(VALUE);
                    g.setFont(sans);
                    g.fillText(en.name(), cx, labelH / 2);
                    g.setFont(mono);
                    g.fillText(TextTile.number(v, config.option("format")), cx, h - valueH / 2);
                }
                int lit = litCells(v, min, max, cells);
                g.setFill(color(v));
                for (int c = 0; c < cells; c++) {
                    if (c == lit) g.setFill(UNLIT);
                    g.fillRect(cx - barW / 2, labelH + barH - c * PITCH - CELL, barW, CELL);
                }
            }
            if (single) {
                g.setFill(LABEL);
                g.setFont(small);
                g.setTextAlign(TextAlignment.LEFT);
                for (int k = 0; k < SCALE_LABELS; k++) {
                    double frac = k / (double) (SCALE_LABELS - 1);
                    double y = barH - frac * (cells - 1) * PITCH - CELL / 2;
                    g.fillText(RadialGaugeTile.tickLabel(min + (max - min) * frac), w - scaleW + 6, Math.max(6, Math.min(h - 6, y)));
                }
            }
        }
    }

    private Color color(double v) {
        double warn = config.optionDouble("warnAbove");
        return !Double.isNaN(warn) && v > warn ? WARN : LIT;
    }

    private static int cellCount(double length) { return Math.max(2, (int) Math.floor((length + PITCH - CELL) / PITCH)); }

    /** How many of the bar's segments light up for a value: at least one at min, all of them at max. */
    static int litCells(double v, double min, double max, int cells) {
        if (Double.isNaN(v) || v < min) return 0;
        double index = (Math.min(v, max) - min) / (max - min) * (cells - 1);
        return (int) Math.floor(index + 1e-9) + 1;
    }
}
