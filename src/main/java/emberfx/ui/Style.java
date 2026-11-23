package emberfx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The dashboard's look for code: colors and small builders, so module tiles match the built-in ones.
 * Fixed styling lives in dashboard.css; use these for colors that change while the dashboard runs.
 */
public final class Style {
    public static final String GREEN = "#22c55e", RED = "#ef4444", BLUE = "#3b82f6", WHITE = "#f8fafc",
        GREY = "#94a3b8", YELLOW = "#facc15";

    /**
     * The card look as inline CSS, for nodes outside the dashboard's stylesheet.
     * Inside the dashboard, prefer {@code node.getStyleClass().add("card")}.
     */
    public static final String CARD = """
        -fx-background-color: #0f172a;
        -fx-background-radius: 16;
        -fx-border-color: #1f2a44;
        -fx-border-radius: 16;
    """;

    private Style() {}

    /** Rounded card with its content centered. */
    public static StackPane card(VBox content) {
        content.setAlignment(Pos.CENTER);
        StackPane card = new StackPane(content);
        card.setPadding(new Insets(10));
        card.getStyleClass().add("card");
        return card;
    }

    /** Small grey heading. */
    public static Label title(String text) {
        return styled(new Label(text), "card-title");
    }

    /** Big bold value text of the given size. */
    public static Label value(String text, double size) {
        Label label = styled(new Label(text), "value");
        label.setStyle("-fx-font-size: " + size + "px;");
        return label;
    }

    /** Inline CSS for big bold text in a color that changes at runtime. */
    public static String text(String color, int size) {
        return String.format("-fx-text-fill: %s; -fx-font-weight: 900; -fx-font-size: %d;", color, size);
    }

    /** Add style classes from dashboard.css to a node. Returns the node for chaining. */
    public static <T extends Node> T styled(T node, String... styleClasses) {
        node.getStyleClass().addAll(styleClasses);
        return node;
    }
}
