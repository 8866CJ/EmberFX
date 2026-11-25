package emberfx.tiles;

import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * Tune a WPILib PIDController live: one number field each for p, i, d, izone and setpoint.
 * Enter or leaving a field sends that one value; otherwise the field follows the robot, and turns
 * amber while your text differs from it. Robot code that feeds it:
 * {@code SmartDashboard.putData("Arm PID", armPidController);}  (topic /SmartDashboard/Arm PID)
 */
public final class PidTile extends Tile {
    static final String[] KEYS = {"p", "i", "d", "izone", "setpoint"};

    private final DoubleSubscriber[] subs = new DoubleSubscriber[KEYS.length];
    private final SyncedField[] fields = new SyncedField[KEYS.length];
    private long stamp = -1;

    public PidTile(TileConfig config) {
        super(config);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        ColumnConstraints label = new ColumnConstraints(), field = new ColumnConstraints();
        field.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(label, field);
        for (int i = 0; i < KEYS.length; i++) {
            String topic = config.topic() + "/" + KEYS[i];
            subs[i] = track(NT.subDouble(topic, Double.NaN));
            LazyPublisher<DoublePublisher> pub = track(new LazyPublisher<>(() -> NT.pubDouble(topic)));
            fields[i] = new SyncedField(true, text -> pub.get().set(Double.parseDouble(text)));
            fields[i].setMaxWidth(Double.MAX_VALUE);
            Label name = new Label(KEYS[i]);
            name.getStyleClass().add("muted");
            grid.addRow(i, name, fields[i]);
        }
        setCenter(grid);
    }

    @Override
    public void update() {
        long now = changeStamp(subs);
        if (now == stamp) return;
        stamp = now;
        for (int i = 0; i < KEYS.length; i++) {
            double v = subs[i].get();
            fields[i].show(Double.isNaN(v) ? null : NumText.format(v));
        }
    }
}
