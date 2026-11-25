package emberfx.tiles;

import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.IntegerSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The match clock, big, turning yellow then red near the end, with what the FMS says under it:
 * the mode, the alliance and station, the match number, and the game-specific message.
 *
 * The topic is the time left, which the robot publishes with
 * SmartDashboard.putNumber("Match Time", DriverStation.getMatchTime()). The rest comes from
 * /FMSInfo, which WPILib publishes on its own.
 */
public final class MatchTimerTile extends Tile {
    private static final String NORMAL = Style.WHITE, CAUTION = "#f59e0b", DANGER = Style.RED;
    private static final String[] MATCH_TYPES = {"", "P", "Q", "E"}; // none, practice, qualification, elimination

    private final DoubleSubscriber time;
    private final IntegerSubscriber control, station, matchNumber, matchType;
    private final BooleanSubscriber red;
    private final StringSubscriber gameData;
    private final Label clock = new Label("--:--");
    private final Label mode = Style.styled(new Label(), "muted");
    private final Label alliance = Style.styled(new Label(), "badge");
    private final Label match = Style.styled(new Label(), "muted");
    private final Label game = new Label();
    private final String clockStyle;
    private final HBox info; // mode, alliance, match, game data: shown only when the robot publishes them

    public MatchTimerTile(TileConfig config) {
        super(config);
        String fms = config.option("fmsTable").trim();
        time = track(NT.subDouble(config.topic(), -1));
        control = track(NT.instance().getIntegerTopic(fms + "/FMSControlData").subscribe(0));
        station = track(NT.instance().getIntegerTopic(fms + "/StationNumber").subscribe(0));
        matchNumber = track(NT.instance().getIntegerTopic(fms + "/MatchNumber").subscribe(0));
        matchType = track(NT.instance().getIntegerTopic(fms + "/MatchType").subscribe(0));
        red = track(NT.subBool(fms + "/IsRedAlliance", false));
        gameData = track(NT.instance().getStringTopic(fms + "/GameSpecificMessage").subscribe(""));

        clockStyle = "-fx-font-family: Consolas; -fx-font-weight: bold; -fx-font-size: "
            + Math.max(12, config.optionDouble("fontSize")) + "px; -fx-text-fill: ";
        info = new HBox(8, mode, alliance, match, game);
        info.setAlignment(Pos.CENTER);
        VBox box = new VBox(4, clock, info);
        box.setAlignment(Pos.CENTER);
        setCenter(box);
    }

    @Override
    public void update() {
        double t = time.get();
        clock.setText(clock(t));
        double caution = config.optionDouble("caution"), danger = config.optionDouble("danger");
        clock.setStyle(clockStyle + (t >= 0 && t <= danger ? DANGER : t >= 0 && t <= caution ? CAUTION : NORMAL) + ";");

        info.setVisible(control.exists());
        int bits = (int) control.get();
        mode.setText(mode(bits));
        boolean onRed = red.get();
        alliance.setText((onRed ? "Red " : "Blue ") + station.get());
        alliance.setStyle("-fx-text-fill: " + (onRed ? "#fca5a5" : "#93c5fd") + "; -fx-border-color: " + (onRed ? "#dc2626" : "#2563eb") + ";");
        int type = (int) matchType.get();
        match.setText(matchNumber.get() > 0 && type > 0 && type < MATCH_TYPES.length ? MATCH_TYPES[type] + matchNumber.get() : "");
        String data = gameData.get();
        game.setText(data.isEmpty() ? "" : "Game data: " + data);
    }

    /** Time left as m:ss, or --:-- outside a match (the Driver Station reports -1 then). */
    static String clock(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) return "--:--";
        int s = (int) Math.ceil(seconds);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    /** The robot's mode from the FMSControlData bits: enabled 1, auto 2, test 4, e-stop 8. */
    static String mode(int bits) {
        if ((bits & 8) != 0) return "E-Stopped";
        if ((bits & 1) == 0) return "Disabled";
        if ((bits & 2) != 0) return "Auto";
        if ((bits & 4) != 0) return "Test";
        return "Teleop";
    }
}
