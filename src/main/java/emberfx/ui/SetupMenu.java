package emberfx.ui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import emberfx.DashModule;
import emberfx.ModuleContext;
import emberfx.NT;
import emberfx.Setting;
import emberfx.config.Config;
import emberfx.config.Layouts;
import emberfx.topics.Topics;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Setup menu and everything it does: settings, robot project, layouts, Driver Station, config files. */
final class SetupMenu {
    private static final System.Logger LOG = System.getLogger(SetupMenu.class.getName());
    private static final String REPLACE_SAME_NAME = "Replace tabs with the same name, keep the rest";
    private static final String REPLACE_ALL = "Replace all my tabs";
    private static final String WEB_SERVER_LINE =
        "WebServer.start(" + Layouts.ROBOT_PORT + ", Filesystem.getDeployDirectory().getPath());";

    private final DashboardView dashboard;
    private final List<DashModule> modules;
    private final ContextMenu menu = new ContextMenu();

    SetupMenu(DashboardView dashboard, List<DashModule> modules) {
        this.dashboard = dashboard;
        this.modules = modules;

        MenuItem addTile = Forms.item("Add tile...", this::addTile);
        CheckMenuItem lock = new CheckMenuItem("Lock layout");
        lock.setSelected(Config.locked());
        lock.setOnAction(e -> {
            Config.root().put("locked", lock.isSelected());
            Config.save();
            dashboard.refreshTabs();
        });
        addTile.disableProperty().bind(lock.selectedProperty());

        menu.getItems().addAll(
            addTile,
            Forms.item("Link robot project...", this::linkProject),
            lock,
            new SeparatorMenuItem(),
            Forms.item("Load layout from robot...", this::loadLayoutFromRobot),
            Forms.item("Save layout to robot project", this::saveLayoutToProject),
            Forms.item("Use as Driver Station dashboard...", this::registerWithDriverStation),
            new SeparatorMenuItem(),
            Forms.item("Settings...", this::showSettings),
            Forms.item("Load config...", this::loadConfig),
            Forms.item("Save config as...", this::saveConfigAs),
            Forms.item("Open data folder", () -> openFolder(Config.DIR)));

        Menu moduleSettings = new Menu("Module settings");
        for (DashModule m : modules) {
            if (DashboardView.isEnabled(m) && !m.settings().isEmpty()) {
                moduleSettings.getItems().add(Forms.item(m.name(), () -> showModuleSettings(m)));
            }
        }
        if (!moduleSettings.getItems().isEmpty()) menu.getItems().add(moduleSettings);
    }

    void show(Node anchor) { menu.show(anchor, Side.BOTTOM, 0, 4); }

    /** For team modules (ModuleContext.addMenuItem). */
    void add(MenuItem item) { menu.getItems().add(item); }

    private Window window() { return dashboard.root().getScene().getWindow(); }

    // ---- tiles and robot project ----

    private void addTile() {
        TabPage page = dashboard.selectedPage();
        if (page != null) page.addNew(Double.NaN, Double.NaN);
        else Forms.info("Switch to one of your own tabs to add a tile.");
    }

    private void linkProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Pick your robot code folder (the one with build.gradle)");
        String current = Config.robotProject();
        if (!current.isEmpty() && Files.isDirectory(Path.of(current))) chooser.setInitialDirectory(new File(current));
        File dir = chooser.showDialog(window());
        if (dir == null) return;

        Config.root().put("robotProject", dir.getAbsolutePath());
        Config.save();
        int found = Topics.fromProject().size();
        Forms.info(found == 0
            ? "Linked, but no topics were found in the code. Topic names must be written as text in quotes, "
                + "e.g. SmartDashboard.putNumber(\"Speed\", speed)."
            : "Linked. Found " + found + " topics in your code. They show up in the topic browser when you add a tile.");
    }

    // ---- layouts ----

    private void loadLayoutFromRobot() {
        var connections = NT.instance().getConnections();
        if (connections.length == 0) {
            Forms.error("Connect to the robot first.");
            return;
        }
        String host = connections[0].remote_ip;
        List<Forms.Field> fields = List.of(
            Forms.text("File on the robot", Layouts.DEFAULT_FILE),
            new Forms.Field("Tabs", REPLACE_SAME_NAME, List.of(REPLACE_SAME_NAME, REPLACE_ALL), false));
        String header = "Downloads the layout from http://" + host + ":" + Layouts.ROBOT_PORT + "/.\n"
            + "The robot needs " + WEB_SERVER_LINE;
        Forms.show("Load layout from robot", header, fields).ifPresent(values -> {
            try {
                Layouts.merge(Config.tabs(), Layouts.fetchFromRobot(host, values.get(0).trim()), values.get(1).equals(REPLACE_ALL));
                Config.save();
                dashboard.reloadTabs();
            } catch (IOException e) {
                Forms.error("Couldn't load the layout: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void saveLayoutToProject() {
        if (Config.robotProject().isEmpty()) {
            Forms.error("Link your robot project first (Setup > Link robot project).");
            return;
        }
        try {
            Path file = Layouts.saveToProject(Path.of(Config.robotProject()));
            Forms.info("Saved " + file + ".\n\nIt deploys with your robot code. To load it from the robot on any laptop, "
                + "add this to robotInit():\n  " + WEB_SERVER_LINE);
        } catch (IOException e) {
            Forms.error("Couldn't save the layout: " + e.getMessage());
        }
    }

    // ---- Driver Station ----

    /** Runs register-ds.ps1 from the install folder, which edits the Driver Station's settings safely. */
    private void registerWithDriverStation() {
        String exe = System.getProperty("jpackage.app-path");
        Path script = Config.appDir().resolve("register-ds.ps1");
        if (exe == null || !Files.exists(script)) {
            Forms.error("This only works in the installed app. From source, run scripts\\register-ds.bat.");
            return;
        }
        if (!Forms.confirm("Make this dashboard the one the Driver Station opens?\n"
            + "Close the Driver Station first; it rewrites its settings when it closes.")) return;
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), "-Exe", exe).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0) Forms.info(output);
            else Forms.error(output);
        } catch (IOException e) {
            Forms.error("Couldn't run " + script + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- settings ----

    private void showSettings() {
        List<Forms.Field> fields = new ArrayList<>(List.of(
            Forms.text("Team number", Integer.toString(Config.team())),
            Forms.text("Window title", Config.title()),
            Forms.text("Grid size (px, 1 = no snapping)", Integer.toString(Config.gridSize())),
            Forms.text("Robot project folder", Config.robotProject())));
        int firstModule = fields.size();
        for (DashModule m : modules) fields.add(Forms.check("Module: " + m.name(), DashboardView.isEnabled(m)));
        String header = "Team number sets the robot address (10.TE.AM.2) and log file names.\n"
            + (modules.isEmpty() ? "No team modules installed (see MODULES.md)." : "Tick the team modules to use.")
            + "\nTeam, title and modules apply when you reopen the dashboard.";

        Forms.show("Settings", header, fields).ifPresent(values -> {
            int team, grid;
            try {
                team = Integer.parseInt(values.get(0).trim());
                grid = Integer.parseInt(values.get(2).trim());
            } catch (NumberFormatException e) {
                Forms.error("Team number and grid size must be whole numbers.");
                return;
            }
            Config.root().put("team", team).put("title", values.get(1).trim()).put("gridSize", Math.max(1, grid))
                .put("robotProject", values.get(3).trim());
            for (int i = 0; i < modules.size(); i++) {
                Config.module(modules.get(i).id()).put("enabled", Boolean.parseBoolean(values.get(firstModule + i)));
            }
            Config.save();
        });
    }

    private void showModuleSettings(DashModule module) {
        List<Setting> settings = module.settings();
        List<Forms.Field> fields = new ArrayList<>();
        for (Setting s : settings) fields.add(Forms.of(s, ModuleContext.value(module, s)));

        Forms.show(module.name(), "Changes apply when you reopen the dashboard.", fields).ifPresent(values -> {
            for (int i = 0; i < settings.size(); i++) {
                if (settings.get(i).kind() == Setting.Kind.NUMBER && !isNumberOrBlank(values.get(i))) {
                    Forms.error(settings.get(i).label() + " must be a number.");
                    return;
                }
            }
            ObjectNode saved = Config.obj(Config.module(module.id()), "settings");
            for (int i = 0; i < settings.size(); i++) saved.put(settings.get(i).key(), values.get(i).trim());
            Config.save();
        });
    }

    private static boolean isNumberOrBlank(String text) {
        if (text.isBlank()) return true;
        try {
            Double.parseDouble(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---- config files ----

    private void loadConfig() {
        File file = jsonChooser("Load a dashboard config").showOpenDialog(window());
        if (file == null || !Forms.confirm("Replace your current settings and layout with " + file.getName()
            + "?\nThe current config is backed up to config.json.bak.")) return;
        try {
            Config.replaceWith(file.toPath());
            Forms.info("Loaded. Close and reopen the dashboard to apply.");
        } catch (IOException e) {
            Forms.error("Couldn't load " + file.getName() + ": " + e.getMessage());
        }
    }

    private void saveConfigAs() {
        FileChooser chooser = jsonChooser("Save the dashboard config");
        chooser.setInitialFileName(Layouts.DEFAULT_FILE);
        File file = chooser.showSaveDialog(window());
        if (file == null) return;
        try {
            Config.exportTo(file.toPath());
        } catch (IOException e) {
            Forms.error("Couldn't save " + file.getName() + ": " + e.getMessage());
        }
    }

    private static FileChooser jsonChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dashboard config (*.json)", "*.json"));
        return chooser;
    }

    private static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            new ProcessBuilder("explorer.exe", dir.toString()).start();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Couldn't open " + dir, e);
        }
    }
}
