package emberfx.ui;

import emberfx.Setting;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Small dialogs and form rows shared by every settings screen. */
public final class Forms {
    /** One row of a form: a text box, a checkbox, or a dropdown when choices is not empty. */
    public record Field(String label, String value, List<String> choices, boolean check) {}

    private Forms() {}

    public static Field text(String label, String value) { return new Field(label, value, List.of(), false); }

    public static Field check(String label, boolean value) { return new Field(label, Boolean.toString(value), List.of(), true); }

    /** The row for a Setting, labeled so users can tell which topics are sent to the robot. */
    public static Field of(Setting s, String value) {
        return switch (s.kind()) {
            case BOOL -> check(s.label(), Boolean.parseBoolean(value));
            case CHOICE -> new Field(s.label(), value, s.choices(), false);
            case TOPIC_READ -> text(s.label() + "  (reads)", value);
            case TOPIC_WRITE -> text(s.label() + "  (sends to robot)", value);
            default -> text(s.label(), value);
        };
    }

    /** Add rows to a grid from the given row on. Returns one getter per field, in order. */
    public static List<Supplier<String>> addTo(GridPane grid, int row, List<Field> fields) {
        List<Supplier<String>> values = new ArrayList<>();
        for (Field f : fields) {
            if (f.check()) {
                CheckBox cb = new CheckBox(f.label());
                cb.setSelected(Boolean.parseBoolean(f.value()));
                grid.add(cb, 0, row++, 2, 1);
                values.add(() -> Boolean.toString(cb.isSelected()));
            } else if (!f.choices().isEmpty()) {
                ComboBox<String> box = new ComboBox<>();
                box.getItems().setAll(f.choices());
                box.setValue(f.choices().contains(f.value()) ? f.value() : f.choices().get(0));
                grid.add(new Label(f.label()), 0, row);
                grid.add(box, 1, row++);
                values.add(box::getValue);
            } else {
                TextField tf = new TextField(f.value());
                tf.setPrefColumnCount(30);
                grid.add(new Label(f.label()), 0, row);
                grid.add(tf, 1, row++);
                values.add(tf::getText);
            }
        }
        return values;
    }

    public static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }

    /** Values in field order (checkboxes as "true"/"false"), or empty if cancelled. */
    public static Optional<List<String>> show(String title, String header, List<Field> fields) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(header);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = grid();
        List<Supplier<String>> values = addTo(grid, 0, fields);
        d.getDialogPane().setContent(grid);
        return d.showAndWait().filter(b -> b == ButtonType.OK).map(b -> values.stream().map(Supplier::get).toList());
    }

    /** Ask for one line of text, e.g. a tab name. */
    public static Optional<String> ask(String title, String prompt, String initial) {
        TextInputDialog d = new TextInputDialog(initial);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(prompt);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    public static void info(String text) { new Alert(Alert.AlertType.INFORMATION, text).showAndWait(); }

    public static void error(String text) { new Alert(Alert.AlertType.ERROR, text).showAndWait(); }

    public static boolean confirm(String text) {
        return new Alert(Alert.AlertType.CONFIRMATION, text).showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    public static MenuItem item(String text, Runnable action) {
        MenuItem m = new MenuItem(text);
        m.setOnAction(e -> action.run());
        return m;
    }
}
