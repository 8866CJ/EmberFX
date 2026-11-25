package emberfx.tiles;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import emberfx.NT;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

/**
 * Sends true when pressed.
 * PULSE sends true, then false 200 ms later (for "zero gyro" style actions).
 * HOLD sends true while held down and false on release.
 * If the topic is a robot Command (SmartDashboard.putData(command)), the button runs it instead,
 * and lights up while the command is running.
 */
public final class ButtonTile extends Tile {
    public static final String PULSE = "Pulse (200 ms)";
    public static final String HOLD = "Hold";
    private static final PseudoClass RUNNING = PseudoClass.getPseudoClass("running");

    private final Button button = new Button();
    private final StringSubscriber sendableType;
    private final BooleanSubscriber running;
    // Created on first press, so nothing is sent until the driver uses it.
    private final LazyPublisher<BooleanPublisher> pub =
        track(new LazyPublisher<>(() -> NT.pubBool(isCommand() ? config.topic() + "/running" : config.topic())));

    public ButtonTile(TileConfig config) {
        super(config);
        sendableType = track(NT.instance().getStringTopic(config.topic() + "/.type").subscribe(""));
        running = track(NT.subBool(config.topic() + "/running", false));
        button.setText(config.option("text"));
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.getStyleClass().add("tile-control");
        button.setMnemonicParsing(false);
        button.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return; // right-click is for the tile menu, not the robot
            send(true);
            if (!hold() && !isCommand()) {
                PauseTransition pulse = new PauseTransition(Duration.millis(200));
                pulse.setOnFinished(f -> send(false));
                pulse.play();
            }
        });
        button.setOnMouseReleased(e -> { if (e.getButton() == MouseButton.PRIMARY && hold()) send(false); });
        setCenter(button);
    }

    private boolean hold() { return HOLD.equals(config.option("mode")); }

    private boolean isCommand() { return "Command".equals(sendableType.get()); }

    private void send(boolean value) {
        pub.get().set(value);
    }

    @Override
    public void update() {
        button.pseudoClassStateChanged(RUNNING, isCommand() && running.get());
    }
}
