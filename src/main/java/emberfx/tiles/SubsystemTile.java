package emberfx.tiles;

import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * What a WPILib subsystem is doing: the command running on it (green dot), its default command
 * running (grey dot), or "idle" (hollow dot). Robot code that feeds it:
 * {@code SmartDashboard.putData("Intake", intakeSubsystem);}  (a SubsystemBase)
 */
public final class SubsystemTile extends Tile {
    enum State { ACTIVE, DEFAULT, IDLE }

    private final StringSubscriber command, defaultCommand;
    private final BooleanSubscriber hasCommand, hasDefault;
    private final Circle dot = new Circle(7);
    private final Label name = Style.styled(new Label(), "value"), detail = Style.styled(new Label(), "muted");
    private String shown = "";
    private long stamp = -1;

    public SubsystemTile(TileConfig config) {
        super(config);
        command = track(NT.instance().getStringTopic(config.topic() + "/.command").subscribe("none"));
        defaultCommand = track(NT.instance().getStringTopic(config.topic() + "/.default").subscribe("none"));
        hasCommand = track(NT.subBool(config.topic() + "/.hasCommand", false));
        hasDefault = track(NT.subBool(config.topic() + "/.hasDefault", false));
        name.setStyle("-fx-font-size: 20px;");
        name.setMinWidth(0);
        HBox row = new HBox(10, dot, name);
        row.setAlignment(Pos.CENTER_LEFT);
        setCenter(new VBox(4, row, detail));
    }

    /** The state, given what the robot reports. */
    static State state(boolean hasCommand, String command, boolean hasDefault, String defaultCommand) {
        if (!hasCommand) return State.IDLE;
        return hasDefault && command.equals(defaultCommand) ? State.DEFAULT : State.ACTIVE;
    }

    @Override
    public void update() {
        long now = changeStamp(command, defaultCommand, hasCommand, hasDefault);
        if (now == stamp) return;
        stamp = now;
        boolean hc = hasCommand.get(), hd = hasDefault.get();
        String cmd = command.get(), def = defaultCommand.get();
        String key = hc + cmd + "|" + hd + def;
        if (key.equals(shown)) return;
        shown = key;
        State s = state(hc, cmd, hd, def);
        name.setText(s == State.IDLE ? "idle" : cmd);
        detail.setText(hd ? "Default: " + def : "No default command");
        switch (s) {
            case ACTIVE -> paint(Style.GREEN, Style.GREEN);
            case DEFAULT -> paint(Style.GREY, Style.GREY);
            default -> paint("transparent", Style.GREY);
        }
        setHeaderValue(s == State.ACTIVE ? "running" : s == State.DEFAULT ? "default" : "");
    }

    private void paint(String fill, String stroke) {
        dot.setStyle("-fx-fill: " + fill + "; -fx-stroke: " + stroke + "; -fx-stroke-width: 1.5;");
    }
}
