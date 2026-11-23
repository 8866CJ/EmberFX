package frcdash;

import frcdash.ui.DashboardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

public class MainApp extends Application {
    public static final Path MODULES_DIR = Config.DIR.resolve("modules");

    @Override
    public void start(Stage stage) {
        List<String> errors = new ArrayList<>();
        if (Config.loadError != null) errors.add(Config.loadError);
        NT.setLogPrefix(Config.team() > 0 ? "frc" + Config.team() : "frcdash");

        DashboardView view = new DashboardView(loadModules(errors), errors);
        stage.setTitle(Config.title());
        stage.setScene(new Scene(view.root(), 1100, 700));
        stage.show();
        if (!errors.isEmpty()) Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, String.join("\n\n", errors)).show());
    }

    @Override
    public void stop() {
        NT.shutdown(); // finish writing the log file
    }

    /** Every team module (DashModule) found in ~/.frcdash/modules/*.jar. */
    static List<DashModule> loadModules(List<String> errors) {
        List<DashModule> list = new ArrayList<>();

        List<URL> jars = new ArrayList<>();
        try {
            Files.createDirectories(MODULES_DIR);
            try (Stream<Path> files = Files.list(MODULES_DIR)) {
                for (Path p : files.filter(p -> p.toString().toLowerCase().endsWith(".jar")).sorted().toList()) jars.add(p.toUri().toURL());
            }
        } catch (IOException e) {
            errors.add("Couldn't read " + MODULES_DIR + ": " + e.getMessage());
        }
        if (!jars.isEmpty()) {
            ClassLoader cl = new URLClassLoader(jars.toArray(URL[]::new), MainApp.class.getClassLoader());
            Iterator<DashModule> it = ServiceLoader.load(DashModule.class, cl).iterator();
            while (true) {
                try {
                    if (!it.hasNext()) break;
                    list.add(it.next());
                } catch (ServiceConfigurationError | LinkageError e) {
                    errors.add("A module in " + MODULES_DIR + " couldn't load: " + e);
                }
            }
        }

        Set<String> ids = new HashSet<>();
        list.removeIf(m -> {
            if (ids.add(m.id())) return false;
            errors.add("Two modules use the id \"" + m.id() + "\"; skipped " + m.getClass().getName());
            return true;
        });
        return list;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
