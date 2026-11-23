package team.example;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import frcdash.DashModule;
import frcdash.ModuleContext;
import frcdash.NT;
import frcdash.Setting;
import frcdash.ui.Style;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Example team module with its own tab: shooter tuning.
 * Set an RPM, shoot, record distance/RPM pairs, and copy them as a Java table for your robot code.
 *
 * While tuning is on, the robot should use the dashboard's RPM, and ignore it whenever the
 * enabled topic doesn't exist (the dashboard closed or disconnected).
 */
public final class ShooterTuningModule implements DashModule {
    @Override public String id() { return "shooter-tuning"; }

    @Override public String name() { return "Shooter tuning tab (example)"; }

    @Override
    public List<Setting> settings() {
        return List.of(
            Setting.writes("enabledTopic", "Tuning on/off (boolean)", "/Dashboard/TuneEnabled"),
            Setting.writes("rpmTopic", "Tuning RPM (double)", "/Dashboard/TuneRPM"),
            Setting.reads("robotTuningTopic", "Robot is using tuning RPM (boolean)", "/SmartDashboard/Shooter/Tuning"),
            Setting.reads("hasTargetTopic", "Vision sees the target (boolean)", "/SmartDashboard/Vision/HasTarget"),
            Setting.reads("readyTopic", "Shooter ready (boolean)", "/SmartDashboard/Shooter/Ready"),
            Setting.reads("distanceTopic", "Distance to target, m", "/SmartDashboard/Vision/DistanceM"),
            Setting.reads("actualRpmTopic", "Actual shooter RPM", "/SmartDashboard/Shooter/RPM"),
            Setting.number("maxRpm", "Slider max RPM", 5600),
            Setting.text("tableName", "Table name when copying", "SHOT_TABLE"));
    }

    @Override
    public void start(ModuleContext ctx) {
        Tab tab = new Tab(ctx);
        ctx.addTab("Shooter Tuning", tab.root, tab::update);
        ctx.addHeader(tab.pill, () -> tab.pill.setVisible(tab.robotTuningSub.get()));
    }

    private static final String DIM = "#64748b"; // distance text while no target is seen

    private record Point(double distance, double rpm) {}

    /** The tab's controls. Kept in one class so the whole example reads top to bottom. */
    private static final class Tab {
        final BooleanPublisher enabledPub;
        final DoublePublisher rpmPub;
        final BooleanSubscriber robotTuningSub, hasTargetSub, readySub;
        final DoubleSubscriber distanceSub, actualRpmSub;
        final double maxRpm;
        final String tableName;

        final Label pill = new Label("TUNING MODE");
        final ToggleButton enableButton = new ToggleButton("Tuning mode: OFF");
        final Label robotLabel = new Label();
        final TextField rpmField = new TextField("3500");
        final Slider rpmSlider;
        final Label distanceLabel = new Label();
        final Label actualLabel = new Label();
        final Label visionLabel = new Label();
        final Label readyLabel = new Label();
        final Label statusLabel = new Label("Drive to a spot, shoot, adjust RPM until it scores, then Record.");
        final ObservableList<Point> points = FXCollections.observableArrayList();
        final Path pointsFile;
        final HBox root;

        Tab(ModuleContext ctx) {
            enabledPub = NT.pubBool(ctx.get("enabledTopic"));
            rpmPub = NT.pubDouble(ctx.get("rpmTopic"));
            robotTuningSub = NT.subBool(ctx.get("robotTuningTopic"), false);
            hasTargetSub = NT.subBool(ctx.get("hasTargetTopic"), false);
            readySub = NT.subBool(ctx.get("readyTopic"), false);
            distanceSub = NT.subDouble(ctx.get("distanceTopic"), 0.0);
            actualRpmSub = NT.subDouble(ctx.get("actualRpmTopic"), 0.0);
            maxRpm = ctx.getDouble("maxRpm");
            tableName = ctx.get("tableName");
            rpmSlider = new Slider(0, maxRpm, Math.min(3500, maxRpm));
            pointsFile = ctx.dataDir().resolve("shot-points.csv");

            pill.setPadding(new Insets(6, 10, 6, 10));
            pill.setStyle("-fx-background-color: #713f12; -fx-border-color: #facc15; -fx-border-radius: 999;"
                + " -fx-background-radius: 999; -fx-text-fill: #fde68a; -fx-font-weight: 800;");
            pill.setVisible(false);

            // Safe values first: tuning off until the driver turns it on.
            enabledPub.set(false);
            rpmPub.set(rpmSlider.getValue());
            loadPoints();

            enableButton.setOnAction(e -> {
                enabledPub.set(enableButton.isSelected());
                enableButton.setText(enableButton.isSelected() ? "Tuning mode: ON" : "Tuning mode: OFF");
            });

            rpmSlider.setMajorTickUnit(1000);
            rpmSlider.setShowTickLabels(true);
            rpmSlider.valueProperty().addListener((obs, o, v) -> {
                double rpm = Math.round(v.doubleValue() / 10.0) * 10.0;
                if (!rpmField.isFocused()) rpmField.setText(String.format("%.0f", rpm));
                rpmPub.set(rpm);
            });
            rpmField.setOnAction(e -> {
                try {
                    rpmSlider.setValue(Math.max(0, Math.min(maxRpm, Double.parseDouble(rpmField.getText().trim()))));
                } catch (NumberFormatException ex) {
                    statusLabel.setText("RPM must be a number.");
                }
            });
            rpmField.setPrefWidth(90);
            Button minus = new Button("-100");
            Button plus = new Button("+100");
            minus.setOnAction(e -> rpmSlider.setValue(rpmSlider.getValue() - 100));
            plus.setOnAction(e -> rpmSlider.setValue(rpmSlider.getValue() + 100));
            HBox rpmRow = new HBox(8, Style.title("RPM"), rpmField, minus, plus);
            HBox.setHgrow(rpmSlider, Priority.ALWAYS);

            VBox left = card(new VBox(12, enableButton, robotLabel, rpmRow, rpmSlider,
                distanceLabel, actualLabel, visionLabel, readyLabel));
            left.setPrefWidth(420);

            ListView<Point> list = new ListView<>(points);
            list.setCellFactory(v -> new ListCell<>() {
                @Override protected void updateItem(Point p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(empty || p == null ? null : String.format("%.2f m  ->  %.0f RPM", p.distance(), p.rpm()));
                }
            });
            VBox.setVgrow(list, Priority.ALWAYS);

            Button record = new Button("Record point");
            Button delete = new Button("Delete selected");
            Button clear = new Button("Clear all");
            Button copy = new Button("Copy " + tableName);
            copy.setMnemonicParsing(false); // show "_" in names like SHOT_TABLE
            record.setOnAction(e -> recordPoint());
            delete.setOnAction(e -> {
                Point p = list.getSelectionModel().getSelectedItem();
                if (p != null) { points.remove(p); savePoints(); }
            });
            clear.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete all " + points.size() + " recorded points?");
                confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> { points.clear(); savePoints(); });
            });
            copy.setOnAction(e -> copyTable());

            VBox right = card(new VBox(10, Style.title("Recorded points"), list, new HBox(8, record, delete, clear, copy), statusLabel));
            HBox.setHgrow(right, Priority.ALWAYS);
            statusLabel.setWrapText(true);
            statusLabel.getStyleClass().add("muted");

            root = new HBox(16, left, right);
            root.setPadding(new Insets(0, 10, 10, 10));
        }

        void update() {
            boolean robotTuning = robotTuningSub.get();
            robotLabel.setText(robotTuning ? "Robot is using your RPM" : "Robot is using its own table");
            robotLabel.setStyle(Style.text(robotTuning ? Style.YELLOW : Style.GREY, 14));
            boolean seen = hasTargetSub.get();
            distanceLabel.setText(String.format("Distance: %.2f m", distanceSub.get()));
            distanceLabel.setStyle(Style.text(seen ? Style.WHITE : DIM, 24));
            actualLabel.setText(String.format("Actual: %.0f RPM", actualRpmSub.get()));
            actualLabel.setStyle(Style.text(Style.WHITE, 18));
            visionLabel.setText(seen ? "Target seen" : "No target (can't record)");
            visionLabel.setStyle(Style.text(seen ? Style.GREEN : Style.RED, 14));
            boolean ready = readySub.get();
            readyLabel.setText(ready ? "READY" : "NOT READY");
            readyLabel.setStyle(Style.text(ready ? Style.GREEN : Style.RED, 18));
        }

        void recordPoint() {
            if (!hasTargetSub.get()) {
                statusLabel.setText("No target in view, so there's no distance to record.");
                return;
            }
            Point p = new Point(Math.round(distanceSub.get() * 100) / 100.0, Math.round(rpmSlider.getValue() / 10.0) * 10.0);
            points.add(p);
            points.sort(Comparator.comparingDouble(Point::distance));
            savePoints();
            statusLabel.setText(String.format("Recorded %.2f m -> %.0f RPM (%d points).", p.distance(), p.rpm(), points.size()));
        }

        void copyTable() {
            if (points.isEmpty()) {
                statusLabel.setText("Record at least one point first.");
                return;
            }
            StringBuilder sb = new StringBuilder("  public static final double[][] " + tableName + " = {\n");
            for (Point p : points) sb.append(String.format("    {%.2f, %.0f},%n", p.distance(), p.rpm()));
            sb.append("  };\n");
            ClipboardContent cc = new ClipboardContent();
            cc.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(cc);
            statusLabel.setText("Copied. Paste it over " + tableName + " in your robot code.");
        }

        // Saved after every change so a crash or closed window doesn't lose tuning data.
        void savePoints() {
            try {
                Files.write(pointsFile, points.stream().map(p -> p.distance() + "," + p.rpm()).toList());
            } catch (IOException e) {
                statusLabel.setText("Couldn't save points: " + e.getMessage());
            }
        }

        void loadPoints() {
            if (!Files.exists(pointsFile)) return;
            try {
                for (String line : Files.readAllLines(pointsFile)) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) points.add(new Point(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
                }
            } catch (IOException | NumberFormatException e) {
                statusLabel.setText("Couldn't read saved points: " + e.getMessage());
            }
        }

        /** A rounded panel using the dashboard's "card" style class (from dashboard.css). */
        static VBox card(VBox content) {
            content.setPadding(new Insets(14));
            content.getStyleClass().add("card");
            return content;
        }
    }
}
