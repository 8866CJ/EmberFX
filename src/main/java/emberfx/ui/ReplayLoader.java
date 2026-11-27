package emberfx.ui;

import emberfx.app.SessionLog;
import emberfx.config.Config;
import emberfx.replay.HootConverter;
import emberfx.replay.LogIndex;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;

/**
 * Menu > Open log: pick .wpilog and/or .hoot files (several at once, e.g. the roboRIO and CANivore
 * hoot files of one match), convert the hoot files with CTRE's owlet, load everything off the UI
 * thread with a progress window and Cancel, then start the replay.
 */
final class ReplayLoader {
    private static final System.Logger LOG = System.getLogger(ReplayLoader.class.getName());

    private ReplayLoader() {}

    static void open(Window owner, DashboardView dashboard) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open logs to replay");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Robot logs (*.wpilog, *.hoot)", "*.wpilog", "*.hoot"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
        if (Files.isDirectory(SessionLog.DIR)) chooser.setInitialDirectory(SessionLog.DIR.toFile());
        List<File> picked = chooser.showOpenMultipleDialog(owner);
        if (picked == null || picked.isEmpty()) return;

        List<Path> files = picked.stream().map(File::toPath).toList();
        List<Path> owlets = List.of();
        if (files.stream().anyMatch(ReplayLoader::isHoot)) {
            owlets = findOwlets(owner);
            if (owlets.isEmpty()) return; // the user cancelled picking owlet
        }
        load(files, owlets, dashboard);
    }

    /** owlet from the saved path or the usual places; asks the user to pick it when none is found. */
    private static List<Path> findOwlets(Window owner) {
        String saved = Config.root().path("owletPath").asText("");
        List<Path> found = HootConverter.findOwlets(saved.isEmpty() ? null : Path.of(saved));
        if (!found.isEmpty()) return found;
        if (!Forms.confirm(".hoot files are converted with CTRE's free owlet tool, and it wasn't found.\n"
            + "Installing AdvantageScope or Phoenix Tuner X provides it. Pick owlet.exe now?")) return List.of();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pick owlet.exe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("owlet", "owlet*.exe"));
        File exe = chooser.showOpenDialog(owner);
        if (exe == null) return List.of();
        Config.root().put("owletPath", exe.getAbsolutePath());
        Config.save();
        return HootConverter.findOwlets(exe.toPath());
    }

    /** Convert and load on a background thread, showing progress; Cancel stops it. */
    private static void load(List<Path> files, List<Path> owlets, DashboardView dashboard) {
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(380);
        Label step = Style.styled(new Label("Starting..."), "muted");
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Opening logs");
        d.setHeaderText(null);
        d.getDialogPane().setContent(new VBox(10, step, bar));
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        DoubleConsumer progress = throttled(bar);
        Thread worker = new Thread(() -> {
            try {
                long hoots = files.stream().filter(ReplayLoader::isHoot).count();
                double convertShare = hoots == 0 ? 0 : 0.5; // half the bar for converting, half for loading
                List<Path> wpilogs = new ArrayList<>();
                int done = 0;
                for (Path f : files) {
                    if (!isHoot(f)) {
                        wpilogs.add(f);
                        continue;
                    }
                    int i = done++;
                    say(step, "Converting " + f.getFileName() + " with owlet...");
                    wpilogs.add(HootConverter.toWpilog(f, owlets,
                        p -> progress.accept(convertShare * (i + p) / hoots)));
                }
                say(step, "Loading " + wpilogs.size() + (wpilogs.size() == 1 ? " log..." : " logs..."));
                LogIndex index = LogIndex.load(wpilogs, p -> progress.accept(convertShare + (1 - convertShare) * p));
                Platform.runLater(() -> {
                    d.close();
                    dashboard.startReplay(index);
                });
            } catch (InterruptedException e) {
                LOG.log(System.Logger.Level.INFO, "Opening logs was cancelled");
            } catch (Exception e) { // shown to the user, not dropped
                LOG.log(System.Logger.Level.WARNING, "Couldn't open logs " + files, e);
                Platform.runLater(() -> {
                    d.close();
                    Forms.error("Couldn't open the logs: " + e.getMessage());
                });
            }
        }, "replay-loader");
        worker.setDaemon(true);
        d.setOnCloseRequest(e -> worker.interrupt()); // Cancel or the window's X
        worker.start();
        d.show();
    }

    private static boolean isHoot(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hoot");
    }

    private static void say(Label label, String text) {
        Platform.runLater(() -> label.setText(text));
    }

    /** Progress updates for the bar, passed to the UI thread only when they move a whole percent. */
    private static DoubleConsumer throttled(ProgressBar bar) {
        AtomicInteger shown = new AtomicInteger(-1);
        return p -> {
            int percent = (int) (p * 100);
            if (shown.getAndSet(percent) != percent) Platform.runLater(() -> bar.setProgress(percent / 100.0));
        };
    }
}
