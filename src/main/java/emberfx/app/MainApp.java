package emberfx.app;

import emberfx.NT;
import emberfx.config.Config;
import emberfx.ui.DashboardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Window;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/** The JavaFX application: loads config and modules, starts logging, and opens the window. */
public final class MainApp extends Application {
    private static final double WIDTH = 1100, HEIGHT = 700;

    @Override
    public void start(Stage stage) {
        List<String> problems = new ArrayList<>();
        if (Config.loadError() != null) problems.add(Config.loadError());
        SessionLog.install(Config.team() > 0 ? "frc" + Config.team() : "emberfx");

        DashboardView view = new DashboardView(ModuleLoader.load(problems), problems);
        Scene scene = new Scene(view.root(), WIDTH, HEIGHT);
        // Every window gets the dashboard look, including dialogs opened by the dashboard and by modules.
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) {
                for (Window w : c.getAddedSubList()) {
                    Scene s = w.getScene();
                    if (s != null && !s.getStylesheets().contains(DashboardView.STYLESHEET)) s.getStylesheets().add(DashboardView.STYLESHEET);
                }
            }
        });
        scene.getStylesheets().add(DashboardView.STYLESHEET);
        stage.setTitle(Config.title());
        stage.setScene(scene);
        stage.show();

        if (!problems.isEmpty()) {
            Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, String.join("\n\n", problems)).show());
        }
    }

    @Override
    public void stop() {
        SessionLog.shutdown(); // finish writing the log file
        NT.stopClient();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
