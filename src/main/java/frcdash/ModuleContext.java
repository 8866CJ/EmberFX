package frcdash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import frcdash.config.Config;
import frcdash.tiles.TileType;
import frcdash.tiles.TileTypes;
import frcdash.ui.DashboardView;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.MenuItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** What a module gets in start(): its settings, storage, and the places it can add things. */
public final class ModuleContext {
    private static final System.Logger LOG = System.getLogger(ModuleContext.class.getName());

    private final DashModule module;
    private final DashboardView window;

    public ModuleContext(DashModule module, DashboardView window) {
        this.module = module;
        this.window = window;
    }

    /** A new kind of tile users can pick in Add tile. The most common thing a module adds. */
    public void addTileType(TileType type) { TileTypes.register(type); }

    /** A full-page tab after the user's tabs. update runs every frame while it's showing (may be null). */
    public void addTab(String title, Parent content, Runnable update) { window.addModuleTab(title, content, update); }

    /** Something small on the right of the tab row. update runs every frame (may be null). */
    public void addHeader(Node node, Runnable update) { window.addHeader(node, update); }

    /** An entry in the Setup menu. */
    public void addMenuItem(MenuItem item) { window.addMenuItem(item); }

    /** A setting's current value: from config.json, else its default. */
    public String get(String key) { return value(module, find(key)); }

    public double getDouble(String key) {
        try {
            return Double.parseDouble(get(key).trim());
        } catch (NumberFormatException e) {
            return Double.parseDouble(find(key).defaultValue());
        }
    }

    public boolean getBool(String key) { return Boolean.parseBoolean(get(key).trim()); }

    /** This module's section of config.json, for anything else it wants to keep. Call save() after changing it. */
    public ObjectNode config() { return Config.module(module.id()); }

    public void save() { Config.save(); }

    /** ~/.frcdash/<module id>/ for the module's own files. Created if missing. */
    public Path dataDir() {
        Path dir = Config.DIR.resolve(module.id());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) { // the module will see the problem when it writes there
            LOG.log(System.Logger.Level.WARNING, "Couldn't create " + dir, e);
        }
        return dir;
    }

    /** A module setting's saved value, or its default. */
    public static String value(DashModule m, Setting s) {
        JsonNode v = Config.module(m.id()).path("settings").get(s.key());
        return v == null || v.isNull() ? s.defaultValue() : v.asText();
    }

    private Setting find(String key) {
        return module.settings().stream().filter(s -> s.key().equals(key)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(module.id() + " has no setting named " + key));
    }
}
