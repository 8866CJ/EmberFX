package emberfx.tiles;

import edu.wpi.first.networktables.GenericPublisher;
import edu.wpi.first.networktables.GenericSubscriber;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.StringArraySubscriber;
import edu.wpi.first.networktables.Topic;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;

/**
 * The commands the robot's CommandScheduler is running, each with a small cancel button.
 * Cancel writes that command's id (its hashCode) to the scheduler's Cancel array; the robot
 * cancels it and clears the array. Robot code that feeds it:
 * {@code SmartDashboard.putData("Scheduler", CommandScheduler.getInstance());}
 */
public final class SchedulerTile extends Tile {
    private static final String CANCEL_ICON = "M6 6l12 12M18 6L6 18";
    private static final String CANCEL_BUTTON = "-fx-padding: 2 6;";

    private final StringArraySubscriber names;
    private final GenericSubscriber ids;
    private final Topic cancelTopic;
    private final VBox rows = new VBox(4);
    private GenericPublisher cancel; // created on first cancel
    private String[] shownNames;
    private long[] shownIds;
    private long stamp = -1;

    public SchedulerTile(TileConfig config) {
        super(config);
        names = track(NT.instance().getStringArrayTopic(config.topic() + "/Names").subscribe(new String[0]));
        ids = track(NT.instance().getTopic(config.topic() + "/Ids").genericSubscribe());
        cancelTopic = NT.instance().getTopic(config.topic() + "/Cancel");
        setCenter(rows);
    }

    /** The ids as longs, whether the robot sends an integer array (current WPILib) or doubles (older). */
    static long[] ids(NetworkTableValue v) {
        if (v.isIntegerArray()) return v.getIntegerArray();
        if (!v.isDoubleArray()) return new long[0];
        double[] d = v.getDoubleArray();
        long[] out = new long[d.length];
        for (int i = 0; i < d.length; i++) out[i] = (long) d[i];
        return out;
    }

    @Override
    public void update() {
        long now = changeStamp(names, ids);
        if (now == stamp) return;
        stamp = now;
        String[] n = names.get();
        long[] id = ids(ids.get());
        if (Arrays.equals(n, shownNames) && Arrays.equals(id, shownIds)) return;
        shownNames = n;
        shownIds = id;
        rows.getChildren().clear();
        for (int i = 0; i < n.length; i++) rows.getChildren().add(row(n[i], i < id.length ? id[i] : null));
        if (n.length == 0) rows.getChildren().add(Style.styled(new Label("No commands running"), "muted"));
        setHeaderValue(n.length == 0 ? "" : String.valueOf(n.length));
    }

    private HBox row(String name, Long id) {
        Label label = new Label(name);
        label.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(6, label, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        if (id != null) {
            Button x = new Button();
            x.setGraphic(Style.icon(CANCEL_ICON));
            x.setStyle(CANCEL_BUTTON);
            x.setOnAction(e -> cancel(id));
            row.getChildren().add(x);
        }
        return row;
    }

    private void cancel(long id) {
        boolean doubles = cancelTopic.getTypeString().equals("double[]");
        if (cancel == null) cancel = track(cancelTopic.genericPublish(doubles ? "double[]" : "int[]"));
        if (doubles) cancel.setDoubleArray(new double[] {id});
        else cancel.setIntegerArray(new long[] {id});
    }
}
