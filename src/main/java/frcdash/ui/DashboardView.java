package frcdash.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import frcdash.Config;
import frcdash.DashModule;
import frcdash.Layouts;
import frcdash.ModuleContext;
import frcdash.NT;
import frcdash.Setting;
import frcdash.topics.Topics;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The window: header (title, log status, robot connection, Setup menu), the tab row,
 * and the pages. User tabs are TabPages; team modules can add their own fixed tabs.
 */
public final class DashboardView {
    /** A tab added by a team module. It can't be renamed, deleted or hold tiles. */
    private record ModuleTab(String name, Parent content, Runnable update) {}

    private final BorderPane root = new BorderPane();
    private final Label connPill = new Label("DISCONNECTED");
    private final Label recLabel = new Label();
    private final Label lockLabel = new Label("Layout locked");
    private final StackPane pages = new StackPane();
    private final HBox tabRow = new HBox(8);
    private final HBox headerItems = new HBox(10);
    private final Button setupButton = new Button("Setup");
    private final ContextMenu setup = new ContextMenu();
    private final List<TabPage> tabs = new ArrayList<>();
    private final ToggleButton topicsButton = new ToggleButton("Topics");
    private final TopicsPanel topicsPanel = new TopicsPanel(t -> {
        if (this.selected instanceof TabPage p && !Config.locked()) p.addTopic(t, Double.NaN, Double.NaN);
    });
    private final List<ModuleTab> moduleTabs = new ArrayList<>();
    private final List<Runnable> headerUpdates = new ArrayList<>();
    private final Set<Runnable> failed = new HashSet<>(); // module updates that threw; skipped from then on
    private final List<DashModule> modules;
    private Object selected; // the TabPage or ModuleTab showing

    public DashboardView(List<DashModule> modules, List<String> errors) {
        this.modules = modules;
        root.setPadding(new Insets(14));
        root.setStyle("""
            -fx-base: #0f172a;
            -fx-background-color: #0b1220;
            -fx-control-inner-background: #0f172a;
            -fx-text-fill: #e5e7eb;
        """);

        for (JsonNode n : Config.tabs()) if (n instanceof ObjectNode o) tabs.add(newPage(o));
        selected = tabs.get(0);
        buildSetupMenu();
        setupButton.setOnAction(e -> setup.show(setupButton, Side.BOTTOM, 0, 4));

        for (DashModule m : modules) {
            if (!enabled(m)) continue;
            try {
                m.start(new ModuleContext(m, this));
            } catch (RuntimeException | LinkageError e) { // a broken team module must not take down the dashboard
                errors.add(m.name() + " didn't start: " + e);
            }
        }

        lockLabel.setStyle("-fx-text-fill: " + Style.GREY + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerItems.setAlignment(Pos.CENTER_LEFT);
        topicsButton.setTooltip(new Tooltip("Show every topic; drag one onto the tab to add it"));
        topicsButton.setOnAction(e -> showTopicsPanel(topicsButton.isSelected()));
        HBox tabBar = new HBox(8, tabRow, lockLabel, spacer, headerItems, topicsButton);
        tabBar.setAlignment(Pos.CENTER_LEFT);
        tabBar.setPadding(new Insets(0, 10, 8, 10));
        tabRow.setAlignment(Pos.CENTER_LEFT);
        root.setTop(new VBox(2, header(), tabBar));
        root.setCenter(pages);
        refreshTabs();

        // Ctrl+1 .. Ctrl+9 switch tabs.
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isShortcutDown() || e.getText().isEmpty() || !Character.isDigit(e.getText().charAt(0))) return;
            int i = e.getText().charAt(0) - '1';
            List<Object> all = allTabs();
            if (i >= 0 && i < all.size()) { selected = all.get(i); refreshTabs(); e.consume(); }
        });

        NT.onConnectionChange(connected -> Platform.runLater(() -> {
            connPill.setText(connected ? "CONNECTED" : "DISCONNECTED");
            connPill.setStyle(pillStyle(connected));
            pages.setOpacity(connected ? 1.0 : 0.6); // dim stale values while disconnected
            if (topicsButton.isSelected()) topicsPanel.refresh();
        }));
        pages.setOpacity(0.6);

        new AnimationTimer() {
            @Override public void handle(long now) {
                if (selected instanceof TabPage p) p.update();
                if (selected instanceof ModuleTab m && m.update() != null) runSafely(m.update());
                headerUpdates.forEach(DashboardView.this::runSafely);
                String file = NT.currentLogFile();
                recLabel.setText(file == null ? "" : (char) 0x25CF + " REC " + file);
            }
        }.start();
    }

    public Parent root() { return root; }

    /** The user's tabs, in order. */
    List<TabPage> tabs() { return tabs; }

    static boolean enabled(DashModule m) {
        return Config.module(m.id()).path("enabled").asBoolean(m.enabledByDefault());
    }

    /** 10.TE.AM.2, or "" when no team is set. */
    public static String robotHost(int team) {
        return team > 0 ? "10." + team / 100 + "." + team % 100 + ".2" : "";
    }

    /** Show a warning without blocking (safe to call from the frame loop). */
    void warn(String text) {
        Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, text).show());
    }

    private void runSafely(Runnable r) {
        if (failed.contains(r)) return;
        try {
            r.run();
        } catch (RuntimeException e) {
            failed.add(r);
            warn("A module stopped updating: " + e);
        }
    }

    // ---- what ModuleContext calls ----

    public void addModuleTab(String name, Parent content, Runnable update) {
        moduleTabs.add(new ModuleTab(name, content, update));
        refreshTabs();
    }

    public void addHeader(Node node, Runnable update) {
        headerItems.getChildren().add(node);
        if (update != null) headerUpdates.add(update);
    }

    public void addMenuItem(MenuItem item) { setup.getItems().add(item); }

    // ---- tabs ----

    private TabPage newPage(ObjectNode json) { return new TabPage(json, this); }

    private List<Object> allTabs() {
        List<Object> all = new ArrayList<>(tabs);
        all.addAll(moduleTabs);
        return all;
    }

    /** Rebuild the tab buttons and show the selected page. Called after any tab change. */
    private void refreshTabs() {
        ToggleGroup group = new ToggleGroup();
        tabRow.getChildren().clear();
        pages.getChildren().clear();
        for (TabPage page : tabs) {
            ToggleButton b = tabButton(page.name(), page, group);
            b.setOnContextMenuRequested(e -> {
                if (!Config.locked()) tabMenu(page).show(b, e.getScreenX(), e.getScreenY());
            });
            tabRow.getChildren().add(b);
            pages.getChildren().add(page.root());
            page.root().setVisible(page == selected);
        }
        if (!Config.locked()) {
            Button add = new Button("+");
            add.setTooltip(new Tooltip("New tab"));
            add.setOnAction(e -> addTab());
            tabRow.getChildren().add(add);
        }
        for (ModuleTab m : moduleTabs) {
            tabRow.getChildren().add(tabButton(m.name(), m, group));
            pages.getChildren().add(m.content());
            m.content().setVisible(m == selected);
        }
        lockLabel.setVisible(Config.locked());
        topicsButton.setVisible(!Config.locked());
        if (Config.locked()) showTopicsPanel(false);
    }

    private void showTopicsPanel(boolean show) {
        topicsButton.setSelected(show);
        root.setRight(show ? topicsPanel : null);
        if (show) topicsPanel.refresh();
    }

    /** Throw away the tab pages and rebuild them from config.json, e.g. after loading a layout. */
    private void reloadTabs() {
        tabs.forEach(TabPage::close);
        tabs.clear();
        for (JsonNode n : Config.tabs()) if (n instanceof ObjectNode o) tabs.add(newPage(o));
        selected = tabs.get(0);
        refreshTabs();
    }

    private ToggleButton tabButton(String text, Object tab, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setSelected(tab == selected);
        b.setCursor(Cursor.HAND);
        b.setMnemonicParsing(false);
        b.setPadding(new Insets(6, 14, 6, 14));
        b.setOnAction(e -> { selected = tab; refreshTabs(); });
        String base = "-fx-background-radius: 999; -fx-border-color: #1f2a44; -fx-border-radius: 999; -fx-font-weight: 700;";
        b.styleProperty().bind(Bindings.when(b.selectedProperty())
            .then("-fx-background-color: #1d4ed8; -fx-text-fill: #f8fafc; " + base)
            .otherwise("-fx-background-color: #111827; -fx-text-fill: #cbd5e1; " + base));
        return b;
    }

    private ContextMenu tabMenu(TabPage page) {
        int i = tabs.indexOf(page);
        MenuItem left = Forms.item("Move left", () -> moveTab(i, i - 1));
        MenuItem right = Forms.item("Move right", () -> moveTab(i, i + 1));
        MenuItem delete = Forms.item("Delete tab...", () -> deleteTab(page));
        left.setDisable(i == 0);
        right.setDisable(i == tabs.size() - 1);
        delete.setDisable(tabs.size() == 1);
        return new ContextMenu(Forms.item("Rename...", () -> Forms.ask("Rename tab", "Name:", page.name()).ifPresent(n -> {
            page.rename(n);
            Config.save();
            refreshTabs();
        })), left, right, delete);
    }

    private void addTab() {
        Forms.ask("New tab", "Name:", "Tab " + (tabs.size() + 1)).ifPresent(name -> {
            ObjectNode json = Config.tabs().addObject().put("name", name);
            json.putArray("tiles");
            TabPage page = newPage(json);
            tabs.add(page);
            selected = page;
            Config.save();
            refreshTabs();
        });
    }

    private void moveTab(int from, int to) {
        ArrayNode json = Config.tabs();
        JsonNode moved = json.remove(from);
        json.insert(to, moved);
        tabs.add(to, tabs.remove(from));
        Config.save();
        refreshTabs();
    }

    private void deleteTab(TabPage page) {
        if (!Forms.confirm("Delete the \"" + page.name() + "\" tab and its tiles?")) return;
        ArrayNode json = Config.tabs();
        for (int i = 0; i < json.size(); i++) if (json.get(i) == page.json()) { json.remove(i); break; }
        page.close();
        tabs.remove(page);
        if (selected == page) selected = tabs.get(0);
        Config.save();
        refreshTabs();
    }

    // ---- Setup menu ----

    private void buildSetupMenu() {
        MenuItem addTile = Forms.item("Add tile...", () -> {
            if (selected instanceof TabPage p) p.addNew(Double.NaN, Double.NaN);
            else Forms.info("Switch to one of your own tabs to add a tile.");
        });
        CheckMenuItem lock = new CheckMenuItem("Lock layout");
        lock.setSelected(Config.locked());
        lock.setOnAction(e -> {
            Config.root().put("locked", lock.isSelected());
            Config.save();
            refreshTabs();
        });
        addTile.disableProperty().bind(lock.selectedProperty());
        setup.getItems().addAll(
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
            if (enabled(m) && !m.settings().isEmpty()) moduleSettings.getItems().add(Forms.item(m.name(), () -> showModuleSettings(m)));
        }
        if (!moduleSettings.getItems().isEmpty()) setup.getItems().add(moduleSettings);
    }

    private void showSettings() {
        List<Forms.Field> fields = new ArrayList<>();
        fields.add(Forms.text("Team number", Integer.toString(Config.team())));
        fields.add(Forms.text("Window title", Config.title()));
        fields.add(Forms.text("Grid size (px, 1 = no snapping)", Integer.toString(Config.gridSize())));
        fields.add(Forms.text("Robot project folder", Config.robotProject()));
        for (DashModule m : modules) fields.add(Forms.check("Module: " + m.name(), enabled(m)));
        String header = "Team number sets the robot address (10.TE.AM.2) and log file names.\n"
            + (modules.isEmpty() ? "No team modules installed (see MODULES.md)." : "Tick the team modules to use.")
            + "\nTeam, title and modules apply when you reopen the dashboard.";
        Forms.show("Settings", header, fields).ifPresent(v -> {
            int team, grid;
            try {
                team = Integer.parseInt(v.get(0).trim());
                grid = Integer.parseInt(v.get(2).trim());
            } catch (NumberFormatException e) {
                Forms.error("Team number and grid size must be whole numbers.");
                return;
            }
            Config.root().put("team", team).put("title", v.get(1).trim()).put("gridSize", Math.max(1, grid))
                .put("robotProject", v.get(3).trim());
            for (int i = 0; i < modules.size(); i++) Config.module(modules.get(i).id()).put("enabled", Boolean.parseBoolean(v.get(i + 4)));
            Config.save();
        });
    }

    private void showModuleSettings(DashModule m) {
        List<Setting> settings = m.settings();
        List<Forms.Field> fields = new ArrayList<>();
        for (Setting s : settings) fields.add(Forms.of(s, ModuleContext.value(m, s)));
        Forms.show(m.name(), "Changes apply when you reopen the dashboard.", fields).ifPresent(v -> {
            for (int i = 0; i < settings.size(); i++) {
                Setting s = settings.get(i);
                if (s.kind() != Setting.Kind.NUMBER || v.get(i).isBlank()) continue;
                try {
                    Double.parseDouble(v.get(i).trim());
                } catch (NumberFormatException e) {
                    Forms.error(s.label() + " must be a number.");
                    return;
                }
            }
            ObjectNode saved = Config.obj(Config.module(m.id()), "settings");
            for (int i = 0; i < settings.size(); i++) saved.put(settings.get(i).key(), v.get(i).trim());
            Config.save();
        });
    }

    private void linkProject() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Pick your robot code folder (the one with build.gradle)");
        if (!Config.robotProject().isEmpty() && Files.isDirectory(Path.of(Config.robotProject()))) {
            dc.setInitialDirectory(new File(Config.robotProject()));
        }
        File dir = dc.showDialog(root.getScene().getWindow());
        if (dir == null) return;
        Config.root().put("robotProject", dir.getAbsolutePath());
        Config.save();
        int n = Topics.fromProject().size();
        Forms.info(n == 0 ? "Linked, but no topics were found in the code. Topic names must be written as text in quotes, "
            + "e.g. SmartDashboard.putNumber(\"Speed\", speed)."
            : "Linked. Found " + n + " topics in your code. They show up in the topic browser when you add a tile.");
    }

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
        Forms.show("Load layout from robot", "Downloads the layout from http://" + host + ":" + Layouts.ROBOT_PORT
            + "/.\nThe robot needs WebServer.start(" + Layouts.ROBOT_PORT + ", Filesystem.getDeployDirectory().getPath()).", fields)
            .ifPresent(v -> {
                try {
                    Layouts.merge(Config.tabs(), Layouts.fetchFromRobot(host, v.get(0).trim()), v.get(1).equals(REPLACE_ALL));
                    Config.save();
                    reloadTabs();
                } catch (IOException e) {
                    Forms.error("Couldn't load the layout: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
    }

    private static final String REPLACE_SAME_NAME = "Replace tabs with the same name, keep the rest";
    private static final String REPLACE_ALL = "Replace all my tabs";

    private void saveLayoutToProject() {
        if (Config.robotProject().isEmpty()) {
            Forms.error("Link your robot project first (Setup > Link robot project).");
            return;
        }
        try {
            Path file = Layouts.saveToProject(Path.of(Config.robotProject()));
            Forms.info("Saved " + file + ".\n\nIt deploys with your robot code. To load it from the robot on any laptop, add this "
                + "to robotInit():\n  WebServer.start(" + Layouts.ROBOT_PORT + ", Filesystem.getDeployDirectory().getPath());");
        } catch (IOException e) {
            Forms.error("Couldn't save the layout: " + e.getMessage());
        }
    }

    /** Runs register-ds.ps1 from the install folder, which edits the Driver Station's settings safely. */
    private void registerWithDriverStation() {
        String exe = System.getProperty("jpackage.app-path");
        Path script = Config.appDir().resolve("register-ds.ps1");
        if (exe == null || !Files.exists(script)) {
            Forms.error("This only works in the installed app. From source, run scripts\\register-ds.bat.");
            return;
        }
        if (!Forms.confirm("Make this dashboard the one the Driver Station opens?\nClose the Driver Station first; "
            + "it rewrites its settings when it closes.")) return;
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), "-Exe", exe).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (p.exitValue() == 0) Forms.info(out);
            else Forms.error(out);
        } catch (IOException e) {
            Forms.error("Couldn't run " + script + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadConfig() {
        FileChooser fc = jsonChooser("Load a dashboard config");
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if (f == null || !Forms.confirm("Replace your current settings and layout with " + f.getName()
            + "?\nThe current config is backed up to config.json.bak.")) return;
        try {
            Config.replaceWith(f.toPath());
            Forms.info("Loaded. Close and reopen the dashboard to apply.");
        } catch (IOException e) {
            Forms.error("Couldn't load " + f.getName() + ": " + e.getMessage());
        }
    }

    private void saveConfigAs() {
        FileChooser fc = jsonChooser("Save the dashboard config");
        fc.setInitialFileName("dashboard.json");
        File f = fc.showSaveDialog(root.getScene().getWindow());
        if (f == null) return;
        try {
            Config.exportTo(f.toPath());
        } catch (IOException e) {
            Forms.error("Couldn't save " + f.getName() + ": " + e.getMessage());
        }
    }

    private static FileChooser jsonChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dashboard config (*.json)", "*.json"));
        return fc;
    }

    private static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            new ProcessBuilder("explorer.exe", dir.toString()).start();
        } catch (IOException ignored) {
        }
    }

    // ---- header ----

    private Parent header() {
        Label title = new Label(Config.title());
        title.setFont(Font.font(22));
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: 700;");

        connPill.setPadding(new Insets(6, 10, 6, 10));
        connPill.setStyle(pillStyle(false));

        String robot = robotHost(Config.team());
        ComboBox<String> targetBox = new ComboBox<>(FXCollections.observableArrayList(
            robot.isEmpty() ? "Robot (set team in Setup)" : "Physical Robot (" + robot + ")", "Localhost (127.0.0.1)", "Custom..."));
        targetBox.setPrefWidth(210);
        TextField customHost = new TextField();
        customHost.setPromptText("Enter host, ex: " + (robot.isEmpty() ? "10.0.0.2" : robot));
        customHost.setPrefWidth(200);
        customHost.managedProperty().bind(customHost.visibleProperty());
        customHost.setVisible(false);

        Runnable connect = () -> {
            int i = targetBox.getSelectionModel().getSelectedIndex();
            String host = i == 0 ? robot : i == 1 ? "127.0.0.1" : customHost.getText().trim();
            if (!host.isEmpty()) NT.startClient(host);
        };
        targetBox.valueProperty().addListener((obs, oldV, newV) -> {
            customHost.setVisible("Custom...".equals(newV));
            if (!customHost.isVisible()) connect.run();
        });
        customHost.setOnAction(e -> connect.run());
        targetBox.getSelectionModel().select(0);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        recLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 800;");
        recLabel.setTooltip(new Tooltip("Logs save to " + NT.LOG_DIR));
        HBox bar = new HBox(12, title, spacer, recLabel, customHost, targetBox, connPill, setupButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 12, 10));
        return bar;
    }

    private static String pillStyle(boolean ok) {
        return String.format("""
            -fx-background-color: %s;
            -fx-border-color: %s;
            -fx-border-radius: 999;
            -fx-background-radius: 999;
            -fx-text-fill: #ffffff;
            -fx-font-weight: 700;
        """, ok ? "#14532d" : "#7f1d1d", ok ? Style.GREEN : Style.RED);
    }
}
