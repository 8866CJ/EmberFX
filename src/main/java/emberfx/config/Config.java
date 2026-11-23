package emberfx.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Everything the dashboard remembers, in one file: ~/.emberfx/config.json.
 *
 * <pre>
 * {
 *   "team": 254, "title": "EmberFX", "gridSize": 10, "locked": false,
 *   "robotProject": "C:\\code\\MyRobot",
 *   "tabs": [ { "name": "Driver", "tiles": [ { "type": "text", "title": "...", "topic": "...",
 *                                              "x": 0, "y": 0, "w": 200, "h": 120, "options": { ... } } ] } ],
 *   "modules": { "<module id>": { "enabled": true, "settings": { ... } } }
 * }
 * </pre>
 *
 * Missing keys fall back to the defaults in code, so an empty file is a valid config.
 */
public final class Config {
    public static final Path DIR = Paths.get(System.getProperty("user.home"), ".emberfx");
    public static final Path FILE = DIR.resolve("config.json");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final System.Logger LOG = System.getLogger(Config.class.getName());
    /** Where versions before the rename to EmberFX (1.0.x, "FRC Dashboard") kept everything. */
    private static final Path OLD_DIR = Paths.get(System.getProperty("user.home"), ".frcdash");

    private static final String OLD_DEFAULT_TITLE = "FRC Dashboard";
    private static boolean migratedFromOldName;

    private static String loadError; // set when config.json couldn't be read; shown once at startup
    private static boolean frozen;   // true = never write (file unreadable, or replaced until restart)
    private static ObjectNode root = load();

    private Config() {}

    /** Why config.json couldn't be read at startup, or null if it loaded fine. */
    public static String loadError() { return loadError; }

    private static ObjectNode load() {
        moveOldDataDir();
        if (!Files.exists(FILE)) return JSON.createObjectNode();
        try {
            JsonNode n = JSON.readTree(FILE.toFile());
            if (!(n instanceof ObjectNode o)) throw new IOException("not a JSON object");
            // Settings saves the window title, so 1.0.x configs often hold the old default name. Use the new one.
            if (migratedFromOldName && OLD_DEFAULT_TITLE.equals(o.path("title").asText())) o.remove("title");
            return o;
        } catch (IOException e) {
            // Keep the user's file: move it aside instead of overwriting it with defaults.
            Path bad = FILE.resolveSibling("config.json.broken");
            try {
                Files.move(FILE, bad, REPLACE_EXISTING);
                loadError = "config.json couldn't be read (" + e.getMessage() + ").\nIt was moved to " + bad
                    + " and the dashboard started with defaults.";
            } catch (IOException moveFailed) {
                frozen = true;
                loadError = "config.json couldn't be read (" + e.getMessage() + ").\nChanges won't be saved until it's fixed.";
            }
            return JSON.createObjectNode();
        }
    }

    /** First launch after the rename: move the old data folder (config, logs, modules) to the new name. */
    private static void moveOldDataDir() {
        if (Files.exists(DIR) || !Files.isDirectory(OLD_DIR)) return;
        try {
            Files.move(OLD_DIR, DIR);
            migratedFromOldName = true;
        } catch (IOException e) { // e.g. the old version is still running; it starts fresh and the old folder stays
            LOG.log(System.Logger.Level.WARNING, "Couldn't move " + OLD_DIR + " to " + DIR, e);
        }
    }

    public static ObjectNode root() { return root; }

    /** 0 when not set. */
    public static int team() { return root.path("team").asInt(0); }

    public static String title() { return root.path("title").asText("EmberFX"); }

    /** Tiles snap to this many pixels while dragging. 1 turns snapping off. */
    public static int gridSize() { return Math.max(1, root.path("gridSize").asInt(10)); }

    /** When locked, tiles and tabs can't be moved, added or removed. Buttons and sliders still work. */
    public static boolean locked() { return root.path("locked").asBoolean(false); }

    /** Folder of the team's robot code, scanned for topic names. Empty when not linked. */
    public static String robotProject() { return root.path("robotProject").asText(""); }

    /** The user's tabs, each {"name": ..., "tiles": [...]}. Always at least one. */
    public static ArrayNode tabs() {
        JsonNode n = root.get("tabs");
        ArrayNode tabs = n instanceof ArrayNode a ? a : root.putArray("tabs");
        if (tabs.isEmpty()) tabs.addObject().put("name", "Dashboard").putArray("tiles");
        return tabs;
    }

    /** A module's own section: {"enabled": ..., "settings": {...}, plus anything the module stores}. */
    public static ObjectNode module(String id) { return obj(obj(root, "modules"), id); }

    /** parent[key] as an object, created if missing. */
    public static ObjectNode obj(ObjectNode parent, String key) {
        JsonNode n = parent.get(key);
        return n instanceof ObjectNode o ? o : parent.putObject(key);
    }

    /** parent[key] as an array, created if missing. */
    public static ArrayNode array(ObjectNode parent, String key) {
        JsonNode n = parent.get(key);
        return n instanceof ArrayNode a ? a : parent.putArray(key);
    }

    public static synchronized void save() {
        if (frozen) return;
        try {
            Files.createDirectories(DIR);
            Path tmp = FILE.resolveSibling("config.json.tmp");
            JSON.writeValue(tmp.toFile(), root);
            Files.move(tmp, FILE, REPLACE_EXISTING, ATOMIC_MOVE); // never leaves a half-written config
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Couldn't save " + FILE, e);
        }
    }

    /** Write the current config somewhere else, to share it. */
    public static void exportTo(Path dest) throws IOException {
        JSON.writeValue(dest.toFile(), root);
    }

    /** Swap in another config (a shared layout). Backs up the current one. Takes effect on restart. */
    public static synchronized void replaceWith(Path src) throws IOException {
        if (!(JSON.readTree(src.toFile()) instanceof ObjectNode)) throw new IOException("not a dashboard config (expected a JSON object)");
        Files.createDirectories(DIR);
        if (Files.exists(FILE)) Files.copy(FILE, FILE.resolveSibling("config.json.bak"), REPLACE_EXISTING);
        Files.copy(src, FILE, REPLACE_EXISTING);
        frozen = true; // the running window must not overwrite the file we just loaded
    }

    /** Folder of the .exe when packaged, else the working directory (the project, under Gradle). */
    public static Path appDir() {
        String exe = System.getProperty("jpackage.app-path");
        return exe != null ? Paths.get(exe).getParent() : Paths.get("").toAbsolutePath();
    }
}
