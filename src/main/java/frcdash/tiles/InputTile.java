package frcdash.tiles;

import edu.wpi.first.networktables.GenericPublisher;
import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.Topic;
import frcdash.NT;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Type a value and press Enter or Send. Shows the robot's current value under the box. */
public final class InputTile extends Tile {
    public static final String AUTO = "Same as the robot (number if new)";
    public static final String NUMBER = "Number";
    public static final String TEXT = "Text";

    private final Topic topic;
    private final GenericSubscriber sub;
    private final TextField field = new TextField();
    private final Label current = new Label();
    private GenericPublisher pub; // created on first send
    private String error;         // shown instead of the robot's value until the next good send

    public InputTile(TileConfig config) {
        super(config);
        topic = NT.instance().getTopic(config.topic());
        sub = track(topic.genericSubscribe());
        Button send = new Button("Send");
        field.setOnAction(e -> send());
        send.setOnAction(e -> send());
        HBox.setHgrow(field, Priority.ALWAYS);
        current.getStyleClass().add("muted");
        setCenter(new VBox(6, new HBox(6, field, send), current));
    }

    /** The NetworkTables type to publish: the robot's if it has one, else what the option says. */
    private String valueType() {
        String kind = config.option("kind");
        if (kind.equals(TEXT)) return "string";
        if (kind.equals(NUMBER)) return "double";
        String robot = topic.getTypeString();
        return robot.equals("string") || robot.equals("int") ? robot : "double";
    }

    private void send() {
        String type = valueType(), text = field.getText().trim();
        try {
            if (pub == null) pub = track(topic.genericPublish(type));
            switch (type) {
                case "string" -> pub.setString(text);
                case "int" -> pub.setInteger(Long.parseLong(text));
                default -> pub.setDouble(Double.parseDouble(text));
            }
            field.clear();
            error = null;
        } catch (NumberFormatException e) {
            error = "Not a number: " + text;
        }
    }

    @Override
    public void update() {
        current.setText(error != null ? error : "Robot: " + TextTile.text(sub.get(), "%s", "true", "false"));
    }
}
