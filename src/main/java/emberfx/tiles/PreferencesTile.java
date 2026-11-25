package emberfx.tiles;

import edu.wpi.first.networktables.GenericPublisher;
import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.Topic;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Every key in the robot's WPILib Preferences with an editor for its type: a checkbox for booleans,
 * a number field for numbers, a text field for strings. Values are sent only when you commit (Enter,
 * leaving the field, or ticking the box). New keys appear within a second. Robot code that feeds it:
 * {@code Preferences.initDouble("Arm kP", 0.1);}  (topic /Preferences, type RobotPreferences)
 */
public final class PreferencesTile extends Tile {
    private static final long RESCAN_MS = 1000;

    private final String path;
    private final GridPane grid = new GridPane();
    private final Map<String, GenericSubscriber> subs = new HashMap<>();
    // One per key and type, kept across row rebuilds so a new key appearing doesn't leave old publishers open.
    private final Map<String, LazyPublisher<GenericPublisher>> pubs = new HashMap<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private long stamp = -1;
    private String layout = "";
    private long lastScan;

    public PreferencesTile(TileConfig config) {
        super(config);
        String t = config.topic().isEmpty() ? "/Preferences" : config.topic();
        path = t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
        grid.setHgap(8);
        grid.setVgap(4);
        ColumnConstraints label = new ColumnConstraints(), editor = new ColumnConstraints();
        editor.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(label, editor);
        ScrollPane scroll = Style.styled(new ScrollPane(grid), "plain-scroll");
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    /**
     * Field text turned into the value to publish for an NT type: a Double for "double"/"float",
     * a Long for "int", the text for "string". Null when it isn't valid for the type.
     */
    static Object parse(String type, String text) {
        switch (type) {
            case "string":
                return text;
            case "double":
            case "float": {
                double v = NumText.parse(text);
                return Double.isNaN(v) ? null : (Object) v;
            }
            case "int": {
                double v = NumText.parse(text);
                return Double.isNaN(v) ? null : (Object) Math.round(v);
            }
            default:
                return null;
        }
    }

    @Override
    public void update() {
        long now = System.currentTimeMillis();
        if (now - lastScan >= RESCAN_MS) {
            lastScan = now;
            scan();
        }
        long values = refreshers.size(); // the row count too, so rebuilt rows get filled in
        for (GenericSubscriber s : subs.values()) values += s.getLastChange();
        if (values == stamp) return;
        stamp = values;
        for (Runnable r : refreshers) r.run();
    }

    private void scan() {
        TreeMap<String, String> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // name -> NT type
        for (Topic t : NT.instance().getTopics(path + "/")) {
            String name = t.getName().substring(path.length() + 1);
            if (name.isEmpty() || name.startsWith(".") || name.contains("/")) continue;
            found.put(name, t.getTypeString());
            if (!subs.containsKey(name)) subs.put(name, track(t.genericSubscribe()));
        }
        String key = found.toString();
        if (key.equals(layout)) return;
        layout = key;
        refreshers.clear();
        grid.getChildren().clear();
        int i = 0;
        for (Map.Entry<String, String> e : found.entrySet()) {
            Label name = new Label(e.getKey());
            name.getStyleClass().add("muted");
            grid.add(name, 0, i);
            grid.add(editor(e.getKey(), e.getValue()), 1, i++);
        }
        setHeaderValue(found.isEmpty() ? "" : String.valueOf(found.size()));
    }

    /** The editor for one key; registers the refresher that keeps it in step with the robot. */
    private Node editor(String name, String type) {
        GenericSubscriber sub = subs.get(name);
        Topic topic = NT.instance().getTopic(path + "/" + name);
        LazyPublisher<GenericPublisher> lazy = pubs.computeIfAbsent(name + "|" + type,
            k -> track(new LazyPublisher<>(() -> topic.genericPublish(type))));
        Consumer<Object> send = value -> {
            if (value == null) return;
            GenericPublisher pub = lazy.get();
            if (value instanceof Boolean b) pub.setBoolean(b);
            else if (value instanceof Long l) pub.setInteger(l);
            else if (value instanceof Double d) pub.setDouble(d);
            else pub.setString(value.toString());
        };
        if (type.equals("boolean")) {
            CheckBox box = new CheckBox();
            box.setOnAction(e -> send.accept(box.isSelected()));
            refreshers.add(() -> {
                NetworkTableValue v = sub.get();
                if (v.isBoolean() && v.getBoolean() != box.isSelected()) box.setSelected(v.getBoolean());
            });
            return box;
        }
        if (parse(type, "0") != null) { // string, double, float or int
            boolean text = type.equals("string");
            SyncedField field = new SyncedField(!text, s -> send.accept(parse(type, s)));
            field.setMaxWidth(Double.MAX_VALUE);
            refreshers.add(() -> {
                NetworkTableValue v = sub.get();
                if (text) field.show(v.isString() ? v.getString() : null);
                else field.show(v.getValue() instanceof Number n ? NumText.format(n.doubleValue()) : null);
            });
            return field;
        }
        Label other = new Label(type + " (read only)");
        other.getStyleClass().add("muted");
        return other;
    }
}
