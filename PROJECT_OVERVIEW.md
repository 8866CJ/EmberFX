# FRC Dashboard

A driver dashboard for any FRC team. It talks to the robot over NetworkTables 4, logs everything it sees to `.wpilog` files, and lets each team build its own tabs out of tiles. Teams can add their own tile types and tabs as plugins.

## Install

Run `FRCDashboard-Setup-<version>.exe`.
- It installs to `%LOCALAPPDATA%\Programs\FRC Dashboard` without admin rights, or to Program Files if you choose "all users".
- It adds Start menu and desktop shortcuts, and an uninstaller in Apps & Features.
- Java is included.
- Installing a newer version upgrades in place. Your settings and logs in `%USERPROFILE%\.frcdash` are kept on upgrade and uninstall.

To make the Driver Station open it, close the DS and choose **Setup > Use as Driver Station dashboard**.

No install: `FRCDashboard.zip` is the same app as a portable folder.

**From source:** `.\gradlew.bat runJavaFX`. This needs WPILib installed (the year in `build.gradle`).

## First run

1. **Setup > Settings:** enter your team number. The robot address becomes 10.TE.AM.2, and log files are named `frc<team>_...`.
2. Pick **Physical Robot**, **Localhost** (simulation) or **Custom...** in the top right. The pill turns green when connected, and the tiles dim while disconnected.
3. **Setup > Link robot project:** pick your robot code folder. The dashboard reads your code for topic names, so you can build tiles before the robot is even on.
4. Click **Topics** (top right of the tab row) and drag topics onto the tab. Each one becomes a matching tile. Double-clicking a topic also adds it, and right-clicking the canvas opens the full Add tile dialog.

## Tabs

- **New tab:** click **+**.
- **Rename, move or delete a tab:** right-click it.
- **Switch tabs:** Ctrl+1 to Ctrl+9.

Team modules can add their own tabs after yours.

## Tiles

| Tile | For | Picked automatically for |
|---|---|---|
| Text | Any value; numbers use a format like `%.2f V` | numbers, strings, arrays |
| Boolean box | Green/red box with optional text and colors | booleans |
| Number bar | A bar between a min and a max | |
| Graph | A number over the last N seconds | |
| Button | Pulse (true for 200 ms) or hold. On a robot `Command`, it runs the command and lights up while it runs | Commands |
| Toggle | Turns a robot boolean on and off | booleans the robot reads |
| Slider | Sends a number as you drag and follows the robot's value otherwise | |
| Input | Type a number or text and press Enter or Send | numbers and strings the robot reads |
| Chooser | Dropdown for a `SendableChooser` (auto selection) | `SendableChooser` |
| Camera | Live video from CameraServer, or any MJPEG URL such as PhotonVision's. Optional resolution, FPS and quality limits | CameraServer cameras |
| Field | Robot pose, other objects and paths from a WPILib `Field2d`. Field size is an option, and you can point it at your own field image | `Field2d` |

To edit tiles:
- **Move:** drag a tile. It snaps to the grid set in Settings.
- **Resize:** drag its edges.
- **Edit, duplicate, move to another tab, or remove:** right-click it.

**Setup > Lock layout** stops tiles and tabs from being moved, added or removed during matches. Buttons, toggles and sliders still work.

Controls only write to the robot after the driver uses them. Until then the topic doesn't exist on the robot, so robot code can check `exists()` and fall back to its own default. Camera tiles stop streaming while their tab is hidden, to save bandwidth.

### Finding topics

The Topics panel and the Add tile > Browse dialog both list two sources, with search:
- **Live topics:** everything the connected robot is publishing. WPILib Sendables (choosers, commands, Field2d) and CameraServer cameras show up once each, with their type.
- **Topics in your robot code:** found by reading the linked project:
  - `SmartDashboard.put*`, `get*` and `setDefault*`
  - `Logger.recordOutput` (AdvantageKit)
  - `get*Topic("/full/path")`

  Values the robot reads are marked, and they get a control (toggle or input) instead of a display. Only names written as text in quotes are found, not names built from constants.

## Layouts: sharing, and keeping one with the robot code

- **Setup > Save layout to robot project** writes your tabs to `<robot project>/src/main/deploy/dashboard.json`, so the layout is versioned and deployed with the robot code.
- **Setup > Load layout from robot** downloads that file from the robot. You can replace tabs that have the same name and keep the rest, or replace all tabs. The robot code needs this in `robotInit()`:

  ```java
  WebServer.start(5800, Filesystem.getDeployDirectory().getPath());
  ```

- **Setup > Save config as / Load config** share the whole config, which also includes team, title and module settings.

Everything is stored in `%USERPROFILE%\.frcdash\config.json`.

## Logging

While connected, the dashboard saves **everything** the robot publishes over NetworkTables.

- Files: `%USERPROFILE%\.frcdash\logs\frc<team>_<date>_<time>.wpilog`. A new file starts each time the robot connects.
- A red **REC** label shows the file being written.
- Open the files in AdvantageScope (File > Open Log). Robot data is under `NT:/`.
- Anything that happens while disconnected isn't in these files. Check the Driver Station logs for that.

## Files

| Path | What |
|---|---|
| `%USERPROFILE%\.frcdash\config.json` | All settings, tabs and tiles |
| `%USERPROFILE%\.frcdash\logs\` | NetworkTables logs |
| `%USERPROFILE%\.frcdash\modules\` | Team module jars |
| `%USERPROFILE%\.frcdash\<module id>\` | A module's own data |

## Building

`scripts\build-exe.bat` builds both:
- `build\installer\FRCDashboard-Setup-<version>.exe`, the one-file installer
- `build\dist\FRCDashboard.zip`, the portable folder

Both include Java. Building needs WPILib (for the JDK and libraries), and the installer also needs [Inno Setup 6](https://jrsoftware.org/isinfo.php): `winget install JRSoftware.InnoSetup --scope user`.

| Script | What it does |
|---|---|
| `scripts\build-exe.bat` | Builds the installer and the zip |
| `scripts\register-ds.bat [exe]` | Makes the exe the Driver Station's Default dashboard (the same thing the Setup menu item does). It refuses while the DS is open, because the DS rewrites its settings when it closes, and it backs up the DS `.ini` first. |
| `scripts\install.ps1` | The one-line PowerShell install. The release workflow fills in the repo name. |
| `scripts\reset-config.bat` | Moves `config.json` to `config.json.bak`. Logs and module data are kept. |

## Releasing

Push a version tag and GitHub Actions builds and publishes the release:

```powershell
git tag v1.2.0
git push origin v1.2.0
```

`.github/workflows/release.yml` runs the tests, builds the installer and zip with the version from the tag, and attaches three files that keep the same names in every release. So these links always point at the newest release, which is handy for a download page:

| Link | What |
|---|---|
| `https://github.com/8866CJ/FRCDashboard/releases/latest/download/FRCDashboard-Setup.exe` | Installer |
| `https://github.com/8866CJ/FRCDashboard/releases/latest/download/FRCDashboard-Portable.zip` | Portable zip |
| `https://github.com/8866CJ/FRCDashboard/releases/latest/download/install.ps1` | The script for `irm <link> \| iex` |

`.github/workflows/build.yml` runs the tests on every push and pull request.

## Code tour

```
src/main/java/frcdash/
  MainApp, Launcher        start-up; loads team module jars
  NT                       NetworkTables connection + .wpilog logging
  Config                   config.json (schema in its class comment)
  Layouts                  layout files: load from the robot, save to the robot project, merge
  DashModule, ModuleContext, Setting    the plugin API
  tiles/
    Tile                   base class: title, update(), track() for NT handles
    TileType, TileConfig   what a tile type is; one tile's saved settings
    TileTypes              the list of tile types + automatic tile picking
    TextTile ... FieldTile one class per built-in tile
  topics/
    LiveTopics             topics the robot publishes now
    ProjectScanner         topic names found in robot code
    Topics, FoundTopic     both merged
  ui/
    DashboardView          window, header, tab row, Setup menu
    TabPage                one tab: tiles, drag/resize/snap, drop target, tile menus
    TileDialog             Add/Edit tile dialog
    TopicList              searchable, draggable topic list
    TopicsPanel, TopicBrowser   the side panel and the Browse dialog, both built on TopicList
    Forms, Style           shared dialogs and colors
installer/FRCDashboard.iss the Inno Setup installer script
examples/
  custom-tile/             a module that adds a tile type
  custom-tab/              a module that adds a whole tab (shooter tuning)
```

To add a built-in tile, write a `Tile` subclass and add one line to `TileTypes.BUILT_IN`. `TextTile` is the smallest example.

Conventions:
- Source files are ASCII-only, because Windows compiles them as windows-1252. Build symbols like the REC dot from char codes.
- Batch files use CRLF line endings, which `.gitattributes` enforces.
- Run `.\gradlew.bat test` before sending changes.
