package emberfx.tiles;

import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.function.Consumer;

/**
 * A text field that mirrors a robot value and sends only what the user commits (Enter or focus loss).
 * While the user is editing it never overwrites their text; it gets an amber border when the text
 * differs from what the robot reports. Escape drops the edit.
 */
final class SyncedField extends TextField {
    /** Border for a field whose text differs from the robot's value. */
    static final String DIFFERS = "-fx-border-color: #d97706;";

    private final boolean numeric;
    private final Consumer<String> onCommit;
    private String robot; // null until the robot has a value
    private boolean dirty, setting, marked;

    /** @param numeric compare and validate as numbers; commits that don't parse are dropped */
    SyncedField(boolean numeric, Consumer<String> onCommit) {
        this.numeric = numeric;
        this.onCommit = onCommit;
        textProperty().addListener((o, a, b) -> {
            if (!setting) dirty = true;
            mark();
        });
        setOnAction(e -> commit());
        focusedProperty().addListener((o, was, now) -> {
            if (!now) commit();
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dirty = false;
                put(robot == null ? "" : robot);
            }
        });
    }

    /** Call every frame with the robot's current value as text, or null when it has none. Cheap when unchanged. */
    void show(String robotText) {
        robot = robotText;
        if (robot != null && !dirty && !isFocused()) put(robot);
        mark();
    }

    private void put(String text) {
        if (text.equals(getText())) return;
        setting = true;
        setText(text);
        setting = false;
    }

    private void commit() {
        if (!dirty) return;
        dirty = false;
        String text = getText().trim();
        if (numeric && Double.isNaN(NumText.parse(text))) put(robot == null ? "" : robot);
        else onCommit.accept(text);
        mark();
    }

    private void mark() {
        boolean differs = robot != null && !NumText.same(getText(), robot, numeric);
        if (differs == marked) return;
        marked = differs;
        setStyle(differs ? DIFFERS : "");
    }
}
