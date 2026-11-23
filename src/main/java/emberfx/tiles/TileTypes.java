package emberfx.tiles;

import com.fasterxml.jackson.databind.node.ObjectNode;
import emberfx.Setting;
import emberfx.topics.FoundTopic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every tile type the Add tile dialog offers: the built-ins below, plus any that team modules register.
 * To add a built-in tile, write the class and add one line to BUILT_IN.
 */
public final class TileTypes {
    private static final Set<String> NUMBERS = Set.of("double", "float", "int");

    private static final List<TileType> BUILT_IN = List.of(
        new TileType("text", "Text", "Shows any value as text. Numbers go through a format like %.2f V.",
            List.of(Setting.text("format", "Number format", "%.2f"),
                Setting.text("trueText", "Text when true", "TRUE"),
                Setting.text("falseText", "Text when false", "FALSE"),
                Setting.number("fontSize", "Font size", 28)),
            220, 120, Set.of("double", "float", "int", "string", "double[]", "float[]", "int[]", "string[]", "boolean[]"),
            TextTile::new),
        new TileType("boolean", "Boolean box", "Fills green when true and red when false.",
            List.of(Setting.text("trueText", "Text when true", ""),
                Setting.text("falseText", "Text when false", ""),
                Setting.text("trueColor", "Color when true", "#22c55e"),
                Setting.text("falseColor", "Color when false", "#ef4444")),
            180, 120, Set.of("boolean"), BooleanTile::new),
        new TileType("bar", "Number bar", "A bar that fills between a min and a max, like battery voltage.",
            List.of(Setting.number("min", "Min", 0), Setting.number("max", "Max", 100),
                Setting.text("format", "Number format", "%.1f")),
            240, 110, Set.of(), NumberBarTile::new),
        new TileType("graph", "Graph", "Plots a number over the last few seconds.",
            List.of(Setting.number("seconds", "Seconds shown", 10),
                Setting.optionalNumber("min", "Y min (blank = auto)"),
                Setting.optionalNumber("max", "Y max (blank = auto)")),
            380, 240, Set.of(), GraphTile::new),
        new TileType("button", "Button", "Sends true when pressed. On a robot Command, it runs the command.",
            List.of(Setting.text("text", "Button text", "Press"),
                Setting.choice("mode", "When pressed", ButtonTile.PULSE, ButtonTile.HOLD)),
            180, 110, Set.of("Command"), ButtonTile::new),
        new TileType("toggle", "Toggle", "Turns a true/false value on the robot on and off.",
            List.of(Setting.text("onText", "Text when on", "ON"), Setting.text("offText", "Text when off", "OFF")),
            180, 110, Set.of(), ToggleTile::new),
        new TileType("slider", "Slider", "Sends a number to the robot as you drag. Shows the robot's value otherwise.",
            List.of(Setting.number("min", "Min", -1), Setting.number("max", "Max", 1),
                Setting.number("step", "Step", 0.05), Setting.text("format", "Number format", "%.2f")),
            280, 120, Set.of(), SliderTile::new),
        new TileType("input", "Input", "Type a number or text and send it to the robot.",
            List.of(Setting.choice("kind", "Value type", InputTile.AUTO, InputTile.NUMBER, InputTile.TEXT)),
            260, 110, Set.of(), InputTile::new),
        new TileType("chooser", "Chooser", "Dropdown for a SendableChooser, like auto selection.",
            List.of(), 260, 110, Set.of("String Chooser"), ChooserTile::new),
        new TileType("camera", "Camera", "Live video from CameraServer, or any MJPEG stream URL (e.g. PhotonVision).",
            List.of(Setting.text("url", "Stream URL (blank = from the topic)", ""),
                Setting.text("resolution", "Resolution, e.g. 320x240 (blank = camera's)", ""),
                Setting.number("fps", "Max FPS (0 = camera's)", 15),
                Setting.number("compression", "JPEG quality 1-100 (0 = camera's)", 0)),
            400, 320, Set.of("Camera"), CameraTile::new),
        new TileType("field", "Field", "Robot position on the field from a WPILib Field2d.",
            List.of(Setting.number("length", "Field length (m)", 17.5),
                Setting.number("width", "Field width (m)", 8.0),
                Setting.number("robotSize", "Robot size (m)", 0.9),
                Setting.text("image", "Field image file (optional)", "")),
            480, 260, Set.of("Field2d"), FieldTile::new));

    private static final List<TileType> all = new ArrayList<>(BUILT_IN);

    private TileTypes() {}

    public static List<TileType> all() { return all; }

    /** Add a tile type from a team module. */
    public static void register(TileType type) {
        if (find(type.id()) != null) throw new IllegalArgumentException("A tile type with id \"" + type.id() + "\" already exists");
        all.add(type);
    }

    public static TileType find(String id) {
        for (TileType t : all) if (t.id().equals(id)) return t;
        return null;
    }

    /**
     * The best tile for a topic. Values the robot reads get a control (toggle, input);
     * everything else gets the first type that lists the topic's data type, or Text.
     */
    public static TileType suggest(FoundTopic topic) {
        String type = topic.type();
        if (topic.robotReads()) {
            if (type.equals("boolean")) return find("toggle");
            if (NUMBERS.contains(type) || type.equals("string")) return find("input");
        }
        for (TileType t : all) if (t.dataTypes().contains(type)) return t;
        return find("text");
    }

    /** Make the tile for a saved config entry. Never throws: unknown types get a placeholder. */
    public static Tile create(ObjectNode json) {
        String id = json.path("type").asText("text");
        TileType type = find(id);
        TileConfig config = new TileConfig(json, type);
        return type == null ? Tile.missing(config, id) : type.factory().apply(config);
    }
}
