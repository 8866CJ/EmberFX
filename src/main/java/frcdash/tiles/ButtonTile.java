package frcdash.tiles;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import frcdash.NT;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
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

    private final Button button = new Button();
    private final StringSubscriber sendableType;
    private final BooleanSubscriber running;
    private BooleanPublisher pub; // created on first press, so nothing is sent until the driver uses it

    public ButtonTile(TileConfig config) {
        super(config);
        sendableType = track(NT.instance().getStringTopic(config.topic() + "/.type").subscribe(""));
        running = track(NT.subBool(config.topic() + "/running", false));
        button.setText(config.option("text"));
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setMnemonicParsing(false);
        button.setOnMousePressed(e -> {
            send(true);
            if (!hold() && !isCommand()) {
                PauseTransition pulse = new PauseTransition(Duration.millis(200));
                pulse.setOnFinished(f -> send(false));
                pulse.play();
            }
        });
        button.setOnMouseReleased(e -> { if (hold()) send(false); });
        setCenter(button);
    }

    private boolean hold() { return HOLD.equals(config.option("mode")); }

    private boolean isCommand() { return "Command".equals(sendableType.get()); }

    private void send(boolean value) {
        if (pub == null) pub = track(NT.pubBool(isCommand() ? config.topic() + "/running" : config.topic()));
        pub.set(value);
    }

    @Override
    public void update() {
        boolean lit = isCommand() && running.get();
        button.setStyle("-fx-font-size: 18; -fx-font-weight: 800; -fx-background-radius: 10;"
            + (lit ? "-fx-background-color: #1d4ed8; -fx-text-fill: white;" : ""));
    }
}
