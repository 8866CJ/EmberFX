package frcdash.app;

import frcdash.NT;
import frcdash.config.Config;
import frcdash.ui.DashboardView;
import javafx.application.Application;
import javafx.application.Platform;
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
        SessionLog.install(Config.team() > 0 ? "frc" + Config.team() : "frcdash");

        DashboardView view = new DashboardView(ModuleLoader.load(problems), problems);
        Scene scene = new Scene(view.root(), WIDTH, HEIGHT);
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
