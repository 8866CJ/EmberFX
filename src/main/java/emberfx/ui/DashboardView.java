package emberfx.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.wpi.first.networktables.NetworkTablesJNI;
import edu.wpi.first.networktables.PubSubOption;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.networktables.TimestampedString;
import emberfx.DashModule;
import emberfx.ModuleContext;
import emberfx.NT;
import emberfx.config.Config;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The dashboard window: an icon rail on the left (menu, one numbered button per tab, edit mode,
 * settings), the Tiles/Topics panel while editing, and the toolbar over the page that's showing.
 * User tabs are {@link TabPage}s; team modules can add fixed tabs and toolbar items.
 * Also runs the frame loop that refreshes whatever is on screen.
 */
public final class DashboardView {
    /** The stylesheet for the dashboard's scene. */
    public static final String STYLESHEET =
        Objects.requireNonNull(DashboardView.class.getResource("dashboard.css"), "dashboard.css is missing").toExternalForm();
    private static final double DISCONNECTED_OPACITY = 0.6; // stale values are dimmed while disconnected
    private static final double SIDE_PANEL_SHARE = 0.24;    // starting width of the side panel, as a share of the window
    /** ElasticLib's Elastic.selectTab: a tab name, or a 0-based index as text. */
    static final String SELECTED_TAB_TOPIC = "/Elastic/SelectedTab";
    private static final long REQUEST_MAX_AGE_MICROS = 3_000_000;

    /** A tab added by a team module. It can't be renamed, deleted or hold tiles. */
    private record ModuleTab(String name, Parent content, Runnable update) {}

    private final BorderPane main = new BorderPane();
    private final Notifications notifications = new Notifications();
    private final StackPane root = new StackPane(main, notifications);
    private final VBox rail = Style.styled(new VBox(), "nav-rail");
    private final StackPane pages = new StackPane();
    private final SplitPane split = new SplitPane();
    private final HBox moduleHeaderItems = new HBox(10);
    private final SidePanel sidePanel = new SidePanel(
        type -> { TabPage p = selectedPage(); if (p != null) p.addNew(type, Double.NaN, Double.NaN); },
        topic -> { TabPage p = selectedPage(); if (p != null) p.addTopic(topic, Double.NaN, Double.NaN); });
    private final HeaderBar header = new HeaderBar(moduleHeaderItems);
    private final SetupMenu setupMenu;
    private final PauseTransition topicRefresh = new PauseTransition(Duration.seconds(1));
    // Elastic's robot-side API (ElasticLib), so robot code written for Elastic works here too.
    // sendAll + keepDuplicates: every notification arrives, even two identical ones in a row.
    private final StringSubscriber robotNotifications = NT.instance().getStringTopic(Notifications.TOPIC)
        .subscribe("", PubSubOption.sendAll(true), PubSubOption.keepDuplicates(true));
    private final StringSubscriber robotSelectedTab = NT.instance().getStringTopic(SELECTED_TAB_TOPIC)
        .subscribe("", PubSubOption.keepDuplicates(true));

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

        startModules(modules, problems);
        main.setLeft(rail);
        main.setCenter(new BorderPane(split, header, null, null, null));
        StackPane.setAlignment(notifications, Pos.BOTTOM_RIGHT);
        SplitPane.setResizableWithParent(sidePanel, false); // window resizes go to the page, not the panel
        topicRefresh.setOnFinished(e -> sidePanel.refresh());
        refreshTabs();
        installShortcuts();

        pages.setOpacity(DISCONNECTED_OPACITY);
        NT.onConnectionChange(connected -> Platform.runLater(() -> {
            pages.setOpacity(connected ? 1.0 : DISCONNECTED_OPACITY);
            if (!Config.locked()) topicRefresh.playFromStart(); // after the robot has announced its topics
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

    /** Rebuild the rail and show the selected page. Called after any tab or edit mode change. */
    void refreshTabs() {
        boolean editing = !Config.locked();
        pages.getChildren().clear();
        for (TabPage page : tabs) {
            pages.getChildren().add(page.root());
            page.root().setVisible(page == selected);
            page.setEditing(editing);
        }
        for (ModuleTab tab : moduleTabs) {
            pages.getChildren().add(tab.content());
            tab.content().setVisible(tab == selected);
        }
        buildRail(editing);

        TabPage page = selectedPage();
        header.setPage(page != null ? page.name() : ((ModuleTab) selected).name(), editing && page != null);
        boolean showPanel = editing && page != null;
        if (showPanel != split.getItems().contains(sidePanel)) {
            split.getItems().setAll(showPanel ? List.of(sidePanel, pages) : List.of(pages));
            if (showPanel) {
                split.setDividerPositions(SIDE_PANEL_SHARE);
                sidePanel.refresh();
                topicRefresh.playFromStart(); // again shortly, in case we just connected
            }
        } else if (split.getItems().isEmpty()) {
            split.getItems().add(pages);
        }
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

    /** Menu, a numbered button per tab, +, module tabs, then edit mode and settings at the bottom. */
    private void buildRail(boolean editing) {
        rail.getChildren().clear();
        Button menu = railButton(new Button(), Style.icon(Style.ICON_MENU), "Menu");
        menu.setOnAction(e -> setupMenu.show(menu));
        rail.getChildren().addAll(menu, new Separator());

        for (int i = 0; i < tabs.size(); i++) {
            TabPage page = tabs.get(i);
            ToggleButton b = tabButton(String.valueOf(i + 1), page.name(), i, page);
            b.setOnContextMenuRequested(e -> {
                if (!Config.locked()) tabMenu(page).show(b, e.getScreenX(), e.getScreenY());
            });
            rail.getChildren().add(b);
        }
        if (editing) {
            Button add = railButton(new Button(), Style.icon(Style.ICON_PLUS), "New tab");
            add.setOnAction(e -> addTab());
            rail.getChildren().add(add);
        }
        for (int i = 0; i < moduleTabs.size(); i++) {
            ModuleTab tab = moduleTabs.get(i);
            rail.getChildren().add(tabButton(tab.name().substring(0, 1).toUpperCase(), tab.name(), tabs.size() + i, tab));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        ToggleButton edit = railButton(new ToggleButton(), Style.icon(Style.ICON_EDIT),
            (editing ? "Stop editing" : "Edit layout") + "  (Ctrl+E)");
        edit.setSelected(editing);
        edit.setOnAction(e -> toggleEditing());
        Button settings = railButton(new Button(), Style.icon(Style.ICON_SETTINGS), "Settings");
        settings.setOnAction(e -> setupMenu.showSettings());
        rail.getChildren().addAll(spacer, edit, settings);
    }

    /** A rail button for a tab: its number (or initial) in a rounded square. Ctrl+number switches to it. */
    private ToggleButton tabButton(String label, String name, int index, Object tab) {
        Rectangle outline = Style.styled(new Rectangle(20, 20), "tab-outline");
        ToggleButton b = railButton(new ToggleButton(label), outline, index < 9 ? name + "  (Ctrl+" + (index + 1) + ")" : name);
        b.setContentDisplay(ContentDisplay.CENTER);
        b.setSelected(tab == selected);
        b.setOnAction(e -> {
            selected = tab;
            refreshTabs();
        });
        return b;
    }

    private static <T extends ButtonBase> T railButton(T button, Node graphic, String tooltip) {
        button.getStyleClass().setAll("nav-button");
        button.setGraphic(graphic);
        button.setMnemonicParsing(false);
        Tooltip tip = new Tooltip(tooltip);
        tip.setShowDelay(Duration.millis(300));
        button.setTooltip(tip);
        return button;
    }

    private void toggleEditing() {
        Config.root().put("locked", !Config.locked());
        Config.save();
        refreshTabs();
    }

    /** Ctrl+1 to Ctrl+9 switch tabs, Ctrl+E turns edit mode on and off. */
    private void installShortcuts() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isShortcutDown()) return;
            if (e.getCode() == KeyCode.E) {
                toggleEditing();
                e.consume();
                return;
            }
            if (e.getText().isEmpty() || !Character.isDigit(e.getText().charAt(0))) return;
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

    /** Refresh the toolbar and the page that's showing, once per frame. */
    private void startFrameLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                header.update();
                readRobotRequests();
                if (selected instanceof TabPage page) page.update();
                if (selected instanceof ModuleTab tab && tab.update() != null) runModuleUpdate(tab.update());
                moduleHeaderUpdates.forEach(DashboardView.this::runModuleUpdate);
            }
        }.start();
    }

    /** Notifications and tab changes the robot code asked for since the last frame. */
    private void readRobotRequests() {
        // On connect, NetworkTables hands over the last value the robot ever sent. Like Elastic, skip
        // anything older than a few seconds, so a reconnect doesn't replay an old error or yank the tab.
        long oldest = NetworkTablesJNI.now() - REQUEST_MAX_AGE_MICROS;
        for (TimestampedString v : robotNotifications.readQueue()) {
            if (v.timestamp >= oldest && !v.value.isBlank()) notifications.showJson(v.value);
        }
        TimestampedString[] tabRequests = robotSelectedTab.readQueue();
        if (tabRequests.length > 0 && tabRequests[tabRequests.length - 1].timestamp >= oldest) {
            selectTab(tabRequests[tabRequests.length - 1].value);
        }
    }

    /** Show the tab with this name (any case), or at this 0-based index. Unknown names are ignored. */
    private void selectTab(String request) {
        List<Object> all = new ArrayList<>(tabs);
        all.addAll(moduleTabs);
        int index = tabIndex(request, all.stream().map(t -> t instanceof TabPage p ? p.name() : ((ModuleTab) t).name()).toList());
        if (index >= 0 && all.get(index) != selected) {
            selected = all.get(index);
            refreshTabs();
        }
    }

    /** Index of the tab a request names: a name match first, then a number. -1 when nothing matches. */
    static int tabIndex(String request, List<String> names) {
        String r = request.trim();
        for (int i = 0; i < names.size(); i++) if (names.get(i).equalsIgnoreCase(r)) return i;
        try {
            int i = Integer.parseInt(r);
            return i >= 0 && i < names.size() ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
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

    private ContextMenu tabMenu(TabPage page) {
        int i = tabs.indexOf(page);
        MenuItem rename = Forms.item("Rename...", () -> askTabName("Rename tab", page.name(), page).ifPresent(name -> {
            page.rename(name);
            Config.save();
            refreshTabs();
        }));
        MenuItem up = Forms.item("Move up", () -> moveTab(i, i - 1));
        MenuItem down = Forms.item("Move down", () -> moveTab(i, i + 1));
        MenuItem delete = Forms.item("Delete tab...", () -> deleteTab(page));
        up.setDisable(i == 0);
        down.setDisable(i == tabs.size() - 1);
        delete.setDisable(tabs.size() == 1);
        return new ContextMenu(rename, up, down, delete);
    }

    private void addTab() {
        askTabName("New tab", "Tab " + (tabs.size() + 1), null).ifPresent(name -> {
            ObjectNode json = Config.tabs().addObject().put("name", name);
            json.putArray("tiles");
            TabPage page = new TabPage(json, this);
            tabs.add(page);
            selected = page;
            Config.save();
            refreshTabs();
        });
    }

    /**
     * A tab name that no other tab has (in any case), asking again after a clash.
     * Unique names matter: Move to tab, the robot's selectTab and layout merging all go by name.
     */
    private Optional<String> askTabName(String title, String initial, TabPage renaming) {
        String suggestion = initial;
        while (true) {
            Optional<String> name = Forms.ask(title, "Name:", suggestion);
            if (name.isEmpty()) return name;
            String n = name.get();
            if (tabs.stream().noneMatch(p -> p != renaming && p.name().equalsIgnoreCase(n))) return name;
            Forms.error("There's already a tab called \"" + n + "\".");
            suggestion = n;
        }
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
