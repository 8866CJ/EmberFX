# Extending the dashboard

Most teams never need code. Tabs and the eleven built-in tiles cover displays, buttons, toggles, sliders, inputs, graphs, auto choosers, cameras and the field map. When you want something they can't do, write a **team module**: a small jar dropped into `%USERPROFILE%\.frcdash\modules\`. The dashboard itself doesn't need to be recompiled.

A module can add:

| What | How | Example |
|---|---|---|
| A new tile type | `ctx.addTileType(...)` | [examples/custom-tile](examples/custom-tile): "Limit light", a number that turns red over a limit |
| A whole tab | `ctx.addTab(...)` | [examples/custom-tab](examples/custom-tab): shooter tuning with a distance -> RPM table |
| A header item | `ctx.addHeader(...)` | the TUNING MODE pill in the custom-tab example |
| A Setup menu entry | `ctx.addMenuItem(...)` | |

## A new tile type

```java
public final class LimitLightModule implements DashModule {
    public String id() { return "limit-light"; }            // config key, keep it stable
    public String name() { return "Limit light tile"; }     // shown in Setup > Settings

    public void start(ModuleContext ctx) {
        ctx.addTileType(new TileType(
            "limit-light", "Limit light", "A number that turns red when it goes over a limit.",
            List.of(Setting.number("limit", "Turn red above", 10)),   // options in the tile's dialog
            200, 120,                                                 // starting size
            Set.of(),                                                 // data types to pick it for automatically
            LimitLightTile::new));
    }

    static final class LimitLightTile extends Tile {
        private final DoubleSubscriber value;
        private final Label label = new Label("--");

        LimitLightTile(TileConfig config) {
            super(config);                                           // draws the card and title
            value = track(NT.subDouble(config.topic(), 0.0));        // track() closes it when the tile goes away
            setCenter(label);
        }

        @Override public void update() {                             // every frame while the tab is showing
            double v = value.get();
            label.setText(String.format("%.1f", v));
            label.setStyle(Style.text(v > config.optionDouble("limit") ? Style.RED : Style.GREEN, 36));
        }
    }
}
```

The new type appears in **Add tile** next to the built-ins. Users pick its topic and options like any other tile, and it's saved in their layout.

### Tile rules of thumb

- Read NetworkTables in `update()`. Don't start threads.
- Wrap every subscriber and publisher in `track(...)`.
- If the tile writes to the robot, create the publisher on the first user action, not in the constructor. That way nothing appears on the robot until the driver uses it (see `SliderTile`).
- `config.option("key")`, `optionDouble` and `optionBool` read the options you declared, with their defaults.

## A whole tab

```java
public void start(ModuleContext ctx) {
    MyTab tab = new MyTab(ctx);
    ctx.addTab("Shooter Tuning", tab.root, tab::update);     // update runs every frame while the tab shows
}
```

List the topics your tab uses as settings, so teams can point them at their own robot without editing your code:

```java
public List<Setting> settings() {
    return List.of(
        Setting.writes("rpmTopic", "Tuning RPM", "/Dashboard/TuneRPM"),        // labeled "sends to robot"
        Setting.reads("distanceTopic", "Distance, m", "/SmartDashboard/Vision/DistanceM"),
        Setting.number("maxRpm", "Slider max RPM", 5600));
}
```

Read them with `ctx.get("rpmTopic")` and `ctx.getDouble("maxRpm")`. Users edit them in **Setup > Module settings**.

## What `ModuleContext` gives you

| Method | Use |
|---|---|
| `addTileType(type)` | A new kind of tile in Add tile |
| `addTab(title, node, update)` | A full-page tab after the user's tabs |
| `addHeader(node, update)` | Something small on the right of the tab row |
| `addMenuItem(item)` | An entry in the Setup menu |
| `get`, `getDouble`, `getBool` | Current value of one of your settings |
| `config()` + `save()` | Your own section of `config.json` |
| `dataDir()` | `%USERPROFILE%\.frcdash\<id>\` for your files |

`NT.subDouble`, `subBool`, `pubDouble`, `pubBool` and `pubString` make NetworkTables handles. `NT.instance()` gives you everything else. `frcdash.ui.Style` has the card, colors and text styles so your UI matches.

## Writing to the robot safely

The dashboard can close or disconnect at any moment, and everything it published disappears from the robot's NetworkTables.

- Publish a safe value first. The tuning tab sends "tuning off" before anything else.
- On the robot, only use a dashboard value while its topic exists (`topic.exists()`), and fall back to your own default otherwise.

## Building and installing

1. **Point your build at the dashboard jar.** Your jar compiles against it, but doesn't include it:

   ```groovy
   dependencies {
       compileOnly files('path/to/FRCDashboard.jar')   // in the app folder: FRCDashboard\app\FRCDashboard.jar
   }
   ```

2. **Register your module class.** Create `src/main/resources/META-INF/services/frcdash.DashModule` containing one line, your class name:

   ```
   team.example.LimitLightModule
   ```

3. **Build it and copy it into the modules folder.** From this repo:

   ```powershell
   .\gradlew.bat -p examples\custom-tile jar
   copy build\custom-tile\libs\custom-tile.jar $env:USERPROFILE\.frcdash\modules\
   ```

4. **Reopen the dashboard.** Modules are on by default. Turn them off in **Setup > Settings**.

If a module throws while starting or updating, the dashboard shows a warning and keeps running without it. A tile whose type isn't installed shows a placeholder and keeps its settings.

The API is NetworkTables (`edu.wpi.first.networktables`), JavaFX 17, Jackson, and the classes in `frcdash`, `frcdash.tiles` and `frcdash.ui.Style`. Keep `id()` and tile type ids the same between versions, or saved layouts won't find them.
