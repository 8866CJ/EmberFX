package emberfx.tiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import emberfx.Setting;
import emberfx.config.Config;

/**
 * One tile's saved settings, a view over its entry in config.json:
 * {"type", "title", "topic", "x", "y", "w", "h", "options": {...}}.
 * Changes are written straight into the config; call Config.save() to keep them.
 */
public final class TileConfig {
    private final ObjectNode json;
    private final TileType type;

    public TileConfig(ObjectNode json, TileType type) {
        this.json = json;
        this.type = type;
    }

    public ObjectNode json() { return json; }

    public TileType type() { return type; }

    public String title() { return json.path("title").asText(""); }

    public String topic() { return json.path("topic").asText("").trim(); }

    public double x() { return json.path("x").asDouble(0); }

    public double y() { return json.path("y").asDouble(0); }

    public double width() { return json.path("w").asDouble(type == null ? 200 : type.width()); }

    public double height() { return json.path("h").asDouble(type == null ? 120 : type.height()); }

    public void setBounds(double x, double y, double w, double h) {
        json.put("x", x).put("y", y).put("w", w).put("h", h);
    }

    /** An option's value, or its default from the tile type. */
    public String option(String key) {
        JsonNode v = json.path("options").get(key);
        if (v != null && !v.isNull()) return v.asText();
        if (type != null) for (Setting s : type.options()) if (s.key().equals(key)) return s.defaultValue();
        return "";
    }

    /** NaN when blank or not a number. */
    public double optionDouble(String key) {
        try {
            return Double.parseDouble(option(key).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public boolean optionBool(String key) { return Boolean.parseBoolean(option(key).trim()); }

    public void setOption(String key, String value) { Config.obj(json, "options").put(key, value); }
}
