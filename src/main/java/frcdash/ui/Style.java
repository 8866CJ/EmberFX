package frcdash.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared look, so module tiles match the built-in ones. */
public final class Style {
    public static final String GREEN = "#22c55e", RED = "#ef4444", BLUE = "#3b82f6", WHITE = "#f8fafc",
        GREY = "#94a3b8", YELLOW = "#facc15";

    public static final String CARD = """
        -fx-background-color: #0f172a;
        -fx-background-radius: 16;
        -fx-border-color: #1f2a44;
        -fx-border-radius: 16;
    """;

    private Style() {}

    /** Rounded tile with its content centered. */
    public static StackPane card(VBox content) {
        content.setAlignment(Pos.CENTER);
        StackPane tile = new StackPane(content);
        tile.setPadding(new Insets(10));
        tile.setStyle(CARD);
        return tile;
    }

    /** Small grey heading. */
    public static Label title(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + GREY + "; -fx-font-weight: 700;");
        return l;
    }

    /** Big bold value text. */
    public static String text(String color, int size) {
        return String.format("-fx-text-fill: %s; -fx-font-weight: 900; -fx-font-size: %d;", color, size);
    }
}
