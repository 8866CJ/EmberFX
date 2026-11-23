package frcdash.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import frcdash.DashModule;
import frcdash.ModuleContext;
import frcdash.NT;
import frcdash.config.Config;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The dashboard window: the header, the tab row, and the page that's showing.
 * User tabs are {@link TabPage}s; team modules can add fixed tabs and header items.
 * Also runs the frame loop that refreshes whatever is on screen.
 */
public final class DashboardView {
    /** The stylesheet for the dashboard's scene. */
    public static final String STYLESHEET =
        Objects.requireNonNull(DashboardView.class.getResource("dashboard.css"), "dashboard.css is missing").toExternalForm();
    private static final double DISCONNECTED_OPACITY = 0.6; // stale values are dimmed while disconnected

    /** A tab added by a team module. It can't be renamed, deleted or hold tiles. */
    private record ModuleTab(String name, Parent content, Runnable update) {}

    private final BorderPane root = Style.styled(new BorderPane(), "dashboard");
    private final StackPane pages = new StackPane();
    private final HBox tabRow = new HBox(8);
    private final HBox moduleHeaderItems = new HBox(10);
    private final Label lockedLabel = Style.styled(new Label("Layout locked"), "muted");
    private final ToggleButton topicsButton = new ToggleButton("Topics");
    private final TopicsPanel topicsPanel = new TopicsPanel(topic -> {
        TabPage page = selectedPage();
        if (page != null && !Config.locked()) page.addTopic(topic, Double.NaN, Double.NaN);
    });
    private final HeaderBar header;
    private final SetupMenu setupMenu;

    private final List<TabPage> tabs = new ArrayList<>();
    private final List<ModuleTab> moduleTabs = new ArrayList<>();
    private final List<Runnable> moduleHeaderUpdates = new ArrayList<>();
    private final Set<Runnable> failedUpdates = new HashSet<>(); // module updates that threw; skipped from then on
    private Object selected; // the TabPage or ModuleTab showing

    /**
     * @param modules  team modules found at startup; the enabled ones are started here
     * @param problems collects module start-up failures, to show the user
     */
    public DashboardView(List<DashModule> modules, List<String> problems) {
        for (JsonNode n : Config.tabs()) if (n instanceof ObjectNode o) tabs.add(new TabPage(o, this));
        selected = tabs.get(0);

        setupMenu = new SetupMenu(this, modules);
        Button setupButton = new Button("Setup");
        setupButton.setOnAction(e -> setupMenu.show(setupButton));
        header = new HeaderBar(setupButton);

        startModules(modules, problems);
        root.setTop(new VBox(2, header, tabBar()));
        root.setCenter(pages);
        refreshTabs();
        installTabShortcuts();

        pages.setOpacity(DISCONNECTED_OPACITY);
        NT.onConnectionChange(connected -> Platform.runLater(() -> {
            pages.setOpacity(connected ? 1.0 : DISCONNECTED_OPACITY);
            if (topicsButton.isSelected()) topicsPanel.refresh();
        }));
        startFrameLoop();
    }

    public Parent root() { return root; }

    /** 10.TE.AM.2, the roboRIO's address for a team number, or "" when no team is set. */
    public static String robotHost(int team) {
        return team > 0 ? "10." + team / 100 + "." + team % 100 + ".2" : "";
    }

    static boolean isEnabled(DashModule module) {
        return Config.module(module.id()).path("enabled").asBoolean(module.enabledByDefault());
    }

    // ---- what ModuleContext calls ----

    public void addModuleTab(String name, Parent content, Runnable update) {
        moduleTabs.add(new ModuleTab(name, content, update));
        refreshTabs();
    }

    public void addHeader(Node node, Runnable update) {
        moduleHeaderItems.getChildren().add(node);
        if (update != null) moduleHeaderUpdates.add(update);
    }

    public void addMenuItem(MenuItem item) { setupMenu.add(item); }

    // ---- used by TabPage and SetupMenu ----

    /** The user's tabs, in order. */
    List<TabPage> tabs() { return tabs; }

    /** The user tab that's showing, or null when a module tab is. */
    TabPage selectedPage() { return selected instanceof TabPage p ? p : null; }

    /** Show a warning without blocking; safe to call from the frame loop. */
    void warn(String text) {
        Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, text).show());
    }

    /** Throw away the tab pages and rebuild them from config.json, e.g. after loading a layout. */
    void reloadTabs() {
        tabs.forEach(TabPage::close);
        tabs.clear();
        for (JsonNode n : Config.tabs()) if (n instanceof ObjectNode o) tabs.add(new TabPage(o, this));
        selected = tabs.get(0);
        refreshTabs();
    }

    /** Rebuild the tab buttons and show the selected page. Called after any tab or lock change. */
    void refreshTabs() {
        boolean locked = Config.locked();
        ToggleGroup group = new ToggleGroup();
        tabRow.getChildren().clear();
        pages.getChildren().clear();
        for (TabPage page : tabs) {
            ToggleButton button = tabButton(page.name(), page, group);
            button.setOnContextMenuRequested(e -> {
                if (!Config.locked()) tabMenu(page).show(button, e.getScreenX(), e.getScreenY());
            });
            tabRow.getChildren().add(button);
            pages.getChildren().add(page.root());
            page.root().setVisible(page == selected);
        }
        if (!locked) {
            Button add = new Button("+");
            add.setTooltip(new Tooltip("New tab"));
            add.setOnAction(e -> addTab());
            tabRow.getChildren().add(add);
        }
        for (ModuleTab tab : moduleTabs) {
            tabRow.getChildren().add(tabButton(tab.name(), tab, group));
            pages.getChildren().add(tab.content());
            tab.content().setVisible(tab == selected);
        }
        lockedLabel.setVisible(locked);
        topicsButton.setVisible(!locked);
        if (locked) showTopicsPanel(false);
    }

    // ---- setup ----

    private void startModules(List<DashModule> modules, List<String> problems) {
        for (DashModule module : modules) {
            if (!isEnabled(module)) continue;
            try {
                module.start(new ModuleContext(module, this));
            } catch (RuntimeException | LinkageError e) { // a broken team module must not take down the dashboard
                problems.add(module.name() + " didn't start: " + e);
            }
        }
    }

    private HBox tabBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topicsButton.setTooltip(new Tooltip("Show every topic; drag one onto the tab to add it"));
        topicsButton.setOnAction(e -> showTopicsPanel(topicsButton.isSelected()));
        HBox bar = new HBox(tabRow, lockedLabel, spacer, moduleHeaderItems, topicsButton);
        bar.getStyleClass().add("tab-bar");
        return bar;
    }

    /** Ctrl+1 to Ctrl+9 switch to that tab. */
    private void installTabShortcuts() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isShortcutDown() || e.getText().isEmpty() || !Character.isDigit(e.getText().charAt(0))) return;
            int index = e.getText().charAt(0) - '1';
            List<Object> all = new ArrayList<>(tabs);
            all.addAll(moduleTabs);
            if (index >= 0 && index < all.size()) {
                selected = all.get(index);
                refreshTabs();
                e.consume();
            }
        });
    }

    /** Refresh the header and the page that's showing, once per frame. */
    private void startFrameLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                header.update();
                if (selected instanceof TabPage page) page.update();
                if (selected instanceof ModuleTab tab && tab.update() != null) runModuleUpdate(tab.update());
                moduleHeaderUpdates.forEach(DashboardView.this::runModuleUpdate);
            }
        }.start();
    }

    /** Run a module's update; one that throws is reported once and skipped from then on. */
    private void runModuleUpdate(Runnable update) {
        if (failedUpdates.contains(update)) return;
        try {
            update.run();
        } catch (RuntimeException e) {
            failedUpdates.add(update);
            warn("A module stopped updating: " + e);
        }
    }

    // ---- tabs ----

    private void showTopicsPanel(boolean show) {
        topicsButton.setSelected(show);
        root.setRight(show ? topicsPanel : null);
        if (show) topicsPanel.refresh();
    }

    private ToggleButton tabButton(String text, Object tab, ToggleGroup group) {
        ToggleButton button = Style.styled(new ToggleButton(text), "tab-button");
        button.setToggleGroup(group);
        button.setSelected(tab == selected);
        button.setMnemonicParsing(false);
        button.setOnAction(e -> {
            selected = tab;
            refreshTabs();
        });
        return button;
    }

    private ContextMenu tabMenu(TabPage page) {
        int i = tabs.indexOf(page);
        MenuItem rename = Forms.item("Rename...", () -> Forms.ask("Rename tab", "Name:", page.name()).ifPresent(name -> {
            page.rename(name);
            Config.save();
            refreshTabs();
        }));
        MenuItem left = Forms.item("Move left", () -> moveTab(i, i - 1));
        MenuItem right = Forms.item("Move right", () -> moveTab(i, i + 1));
        MenuItem delete = Forms.item("Delete tab...", () -> deleteTab(page));
        left.setDisable(i == 0);
        right.setDisable(i == tabs.size() - 1);
        delete.setDisable(tabs.size() == 1);
        return new ContextMenu(rename, left, right, delete);
    }

    private void addTab() {
        Forms.ask("New tab", "Name:", "Tab " + (tabs.size() + 1)).ifPresent(name -> {
            ObjectNode json = Config.tabs().addObject().put("name", name);
            json.putArray("tiles");
            TabPage page = new TabPage(json, this);
            tabs.add(page);
            selected = page;
            Config.save();
            refreshTabs();
        });
    }

    private void moveTab(int from, int to) {
        ArrayNode json = Config.tabs();
        json.insert(to, json.remove(from));
        tabs.add(to, tabs.remove(from));
        Config.save();
        refreshTabs();
    }

    private void deleteTab(TabPage page) {
        if (!Forms.confirm("Delete the \"" + page.name() + "\" tab and its tiles?")) return;
        ArrayNode json = Config.tabs();
        for (int i = 0; i < json.size(); i++) {
            if (json.get(i) == page.json()) {
                json.remove(i);
                break;
            }
        }
        page.close();
        tabs.remove(page);
        if (selected == page) selected = tabs.get(0);
        Config.save();
        refreshTabs();
    }
}
