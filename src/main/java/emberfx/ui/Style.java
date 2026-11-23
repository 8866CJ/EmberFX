package emberfx.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

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
        -fx-background-color: #1e293b;
        -fx-background-radius: 8;
        -fx-border-color: rgba(148, 163, 184, 0.2);
        -fx-border-radius: 8;
    """;

    /** 24x24 stroked icon paths for {@link #icon}. */
    public static final String ICON_MENU = "M4 6h16M4 12h16M4 18h16",
        ICON_PLUS = "M12 5v14M5 12h14",
        ICON_EDIT = "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z",
        ICON_SETTINGS = "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6";

    private Style() {}

    /** Rounded card with its content centered. */
    public static StackPane card(VBox content) {
        content.setAlignment(Pos.CENTER);
        StackPane card = new StackPane(content);
        card.setPadding(new Insets(10));
        card.getStyleClass().add("card");
        return card;
    }

    /** An 18px outline icon from a 24x24 SVG path, e.g. {@link #ICON_PLUS}. Styled by the "icon" class. */
    public static SVGPath icon(String path) {
        SVGPath icon = styled(new SVGPath(), "icon");
        icon.setContent(path);
        icon.setScaleX(0.75);
        icon.setScaleY(0.75);
        return icon;
    }

    /** Bold tile heading. */
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
