package emberfx;

import java.util.List;

/**
 * A team plugin, loaded from a jar in ~/.emberfx/modules/. It can add tile types, tabs,
 * header items and menu entries through the ModuleContext it gets in start().
 *
 * The jar lists its class in META-INF/services/emberfx.DashModule. See MODULES.md and examples/.
 */
public interface DashModule {
    /** Stable id, used as the config key. Lowercase with dashes, e.g. "shooter-tuning". */
    String id();

    /** Shown in Settings. */
    String name();

    /** Whether the module is on before the user changes it in Settings. */
    default boolean enabledByDefault() { return true; }

    /** Topics the module reads or writes, plus its other settings. Edited in Menu > Module settings. */
    default List<Setting> settings() { return List.of(); }

    /**
     * Register tile types and build UI. Called once, on the JavaFX thread, only when the module is enabled.
     * Anything written to the robot must be safe to lose: publish a safe value first, and expect the
     * topic to disappear when the dashboard disconnects.
     */
    void start(ModuleContext ctx);
}
