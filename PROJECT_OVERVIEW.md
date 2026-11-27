# EmberFX

A driver dashboard for any FRC team. It talks to the robot over NetworkTables 4, logs everything it sees to `.wpilog` files, and lets each team build its own tabs out of tiles. Teams can add their own tile types and tabs as plugins.

## Install

Run `EmberFX-Setup-<version>.exe`.
- It installs to `%LOCALAPPDATA%\Programs\EmberFX` without admin rights, or to Program Files if you choose "all users".
- It adds Start menu and desktop shortcuts, and an uninstaller in Apps & Features.
- Java is included.
- Installing a newer version upgrades in place. Your settings and logs in `%USERPROFILE%\.emberfx` are kept on upgrade and uninstall.

To make the Driver Station open it, close the DS and choose **Menu > Use as Driver Station dashboard**.

No install: `EmberFX.zip` is the same app as a portable folder.

**From source:** `.\gradlew.bat runJavaFX`. This needs WPILib installed (the year in `build.gradle`).

## First run

1. **Settings** (bottom of the left bar): enter your team number. The robot address becomes 10.TE.AM.2, and log files are named `frc<team>_...`.
2. Pick **Physical Robot**, **Localhost** (simulation) or **Custom...** in the top right. The pill turns green when connected, and the tiles dim while disconnected.
3. **Menu > Link robot project:** pick your robot code folder. The dashboard reads your code for topic names, so you can build tiles before the robot is even on.
4. Click the pencil at the bottom of the left bar (or press Ctrl+E) to edit. A panel opens with **Tiles** and **Topics**: drag a topic onto the tab and it becomes a matching tile, or drag a tile type and pick its topic. Double-clicking also adds it, and right-clicking the canvas opens the full Add tile dialog.

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
| Radial gauge | A dial from min to max with the value in the middle; optional red-above warning | |
| Linear gauge | Segmented bars across or up. Point it at a table (e.g. `/SmartDashboard/Motor Temps`) for one labelled bar per number in it | |
| Checklist | A check per true/false value in a table (e.g. `/SmartDashboard/Ready`) | |
| Pre-match check | READY, CHECK or NOT READY from the battery, WPILib Alerts, DogLog faults, E-stop, the chosen auto (and whether its file exists), whether the robot is at the auto's start, a checklist table and Limelight heartbeats. Checks the robot has no data for are hidden. Read-only | |
| Alerts | Errors, warnings and infos from a WPILib `Alert` group | WPILib Alerts |
| Swerve | Chassis heading, module angles and speed arrows. Reads a struct `SwerveModuleState[]` topic, CTRE's generated `DriveState` table, or a `SwerveDrive` sendable (the layout Elastic uses; the Swerve tile's class comment has the robot code) | `SwerveDrive`, `SwerveModuleState[]` |
| Match timer | Time left (from a topic such as `SmartDashboard.putNumber("Match Time", DriverStation.getMatchTime())`), turning yellow then red, with mode, alliance, match number and game data from `/FMSInfo` | |
| Button | Pulse (true for 200 ms) or hold. On a robot `Command`, it runs the command and lights up while it runs | Commands |
| Toggle | Turns a robot boolean on and off | booleans the robot reads |
| Slider | Sends a number as you drag and follows the robot's value otherwise | |
| Input | Type a number or text and press Enter or Send | numbers and strings the robot reads |
| Chooser | Dropdown for a `SendableChooser` (auto selection) | `SendableChooser` |
| Camera | Live video from CameraServer, or any MJPEG URL such as PhotonVision's. Optional resolution, FPS and quality limits | CameraServer cameras |
| Field | Robot pose, other objects and paths from a WPILib `Field2d`, or a struct `Pose2d`/`Pose2d[]` topic. Field size is an option, and you can point it at your own field image. With a linked robot project it also draws the auto picked in the chooser (see below) | `Field2d`, struct poses |
| PID tuner | Edit p, i, d, izone and setpoint of a `PIDController`. Enter or leaving a field sends it; a field that differs from the robot is outlined | `PIDController` |
| Subsystem | The command a subsystem is running, or idle, and its default command | `Subsystem` |
| Scheduler | The commands the robot is running, each with a cancel button | `Scheduler` |
| Preferences | Every WPILib `Preferences` key with an editor for its type | `RobotPreferences` |
| Power distribution | Current per PDP/PDH channel, with voltage and total current; optional red-above warning | `PowerDistribution` |
| Gyro | Compass dial with the heading | `Gyro` |
| Encoder | Speed, distance and distance per tick | `Encoder` |
| Differential / Mecanum drive | Chassis with an arrow per side or wheel for the motor outputs | `DifferentialDrive`, `MecanumDrive` |
| Accelerometer | X, Y and Z bars, or one bar for a single-axis accelerometer | `3AxisAccelerometer`, `Accelerometer` |

To edit tiles:
- **Move:** drag a tile. It snaps to the grid set in Settings.
- **Resize:** drag its edges.
- **Edit, duplicate, copy, move to another tab, or remove:** right-click it.
- **Paste:** right-click an empty spot. A copied tile is plain text on the clipboard, so you can also paste it into a chat and a teammate can paste it into their dashboard.

Outside edit mode (the pencil, or Ctrl+E) tiles and tabs can't be moved, added or removed, so nothing gets bumped during a match. Buttons, toggles and sliders still work. Tabs are the numbered buttons on the left bar; Ctrl+1 to Ctrl+9 switch between them, and right-clicking one in edit mode renames, moves or deletes it.

Controls only write to the robot after the driver uses them. Until then the topic doesn't exist on the robot, so robot code can check `exists()` and fall back to its own default. Camera tiles stop streaming while their tab is hidden, to save bandwidth.

**Struct values** (`Pose2d`, `SwerveModuleState[]`, `ChassisSpeeds` and any other WPILib or custom struct) are decoded from the schemas the robot publishes. A Text tile shows them as text, e.g. `Pose2d(x=1.20, y=3.40, rot=90.0 deg)`.

**Auto preview.** With a robot project linked, the Field tile draws the auto picked in the auto chooser (the "Auto chooser" option, default `/SmartDashboard/Auto Chooser`). It reads PathPlanner `deploy/pathplanner/autos` and `paths`, and Choreo `deploy/choreo/*.traj`, and flips them for the red alliance (rotated by default, mirrored as an option). While the robot is disabled, a dashed line shows how far the robot is from where the auto starts, so a robot placed in the wrong spot is caught before the match. Edited files are picked up within a second.

**Messages and tab changes from robot code.** Robot code written for Elastic works unchanged, through the same topics ElasticLib uses:
- `Elastic.sendNotification(...)` pops up a message in the bottom-right corner (`/Elastic/RobotNotifications`). Errors, warnings and infos get their own icon; a display time of 0 keeps it until closed.
- `Elastic.selectTab("Autonomous")` (or a 0-based index) switches tabs (`/Elastic/SelectedTab`).

### Finding topics

The Topics panel and the Add tile > Browse dialog both list two sources, with search:
- **Live topics:** everything the connected robot is publishing. WPILib Sendables (choosers, commands, Field2d) and CameraServer cameras show up once each, with their type.
- **Topics in your robot code:** found by reading the linked project:
  - `SmartDashboard.put*`, `get*` and `setDefault*`
  - `Logger.recordOutput` (AdvantageKit)
  - `get*Topic("/full/path")`

  Values the robot reads are marked, and they get a control (toggle or input) instead of a display. So do tuning values under `/Tuning` (AdvantageKit), `/Tunable` (DogLog) and `/Preferences`. Only names written as text in quotes are found, not names built from constants.

**Menu > Check topics** compares the two: topics the code mentions that the robot doesn't publish (often a stale deploy or a typo), topics the robot publishes that the code never mentions, and topics whose type differs between the code and the robot.

## Layouts: sharing, and keeping one with the robot code

- **Menu > Save layout to robot project** writes your tabs to `<robot project>/src/main/deploy/dashboard.json`, so the layout is versioned and deployed with the robot code.
- **Menu > Load layout from robot** downloads that file from the robot. You can replace tabs that have the same name and keep the rest, or replace all tabs. The robot code needs this in `robotInit()`:

  ```java
  WebServer.start(5800, Filesystem.getDeployDirectory().getPath());
  ```

- **Menu > Save config as / Load config** share the whole config, which also includes team, title and module settings.

Everything is stored in `%USERPROFILE%\.emberfx\config.json`.

## Logging

While connected, the dashboard saves **everything** the robot publishes over NetworkTables.

- Files: `%USERPROFILE%\.emberfx\logs\frc<team>_<date>_<time>.wpilog`. A new file starts each time the robot connects.
- A red **REC** label shows the file being written.
- **Menu > Open latest log** opens the newest file with its usual program (AdvantageScope, if it's installed). In AdvantageScope, robot data is under `NT:/`.
- Anything that happens while disconnected isn't in these files. Check the Driver Station logs for that.

## Replaying logs

**Menu > Open log to replay** plays a log back through your tabs, so every tile shows what it showed (or would have shown) at that moment of the match.

- Opens `.wpilog` files (EmberFX's own logs, robot `DataLogManager` logs, AdvantageKit logs) and CTRE `.hoot` files. Pick several at once to merge them, e.g. the roboRIO and CANivore hoot files of one match.
- `.hoot` files are converted with CTRE's free **owlet** tool, the same way AdvantageScope does it. EmberFX finds the owlet versions AdvantageScope has downloaded (or Phoenix Tuner X's), picks the one matching the log, and asks for `owlet.exe` if there is none. Converted files are cached in `%USERPROFILE%\.emberfx\replay-cache`; delete that folder to free the space.
- Phoenix signals appear as topics like `/Phoenix6/<bus>/TalonFX-1/Velocity`, and CTRE swerve telemetry as `/DriveState/...`, so the Field and Swerve tiles replay it directly.
- The bar along the bottom: play/pause (Space), speed (click to cycle 0.25x to 4x), and a timeline tinted by robot mode (auto amber, teleop green); click or drag anywhere on it to jump. **Exit replay** reconnects to the robot.
- While replaying, the dashboard is disconnected from the robot and records nothing. A control used during a replay never reaches the robot afterwards.

## Files

| Path | What |
|---|---|
| `%USERPROFILE%\.emberfx\config.json` | All settings, tabs and tiles |
| `%USERPROFILE%\.emberfx\logs\` | NetworkTables logs |
| `%USERPROFILE%\.emberfx\modules\` | Team module jars |
| `%USERPROFILE%\.emberfx\<module id>\` | A module's own data |

## Building

`scripts\build-exe.bat` builds both:
- `build\installer\EmberFX-Setup-<version>.exe`, the one-file installer
- `build\dist\EmberFX.zip`, the portable folder

Both include Java. Building needs WPILib (for the JDK and libraries), and the installer also needs [Inno Setup 6](https://jrsoftware.org/isinfo.php): `winget install JRSoftware.InnoSetup --scope user`.

| Script | What it does |
|---|---|
| `scripts\build-exe.bat` | Builds the installer and the zip |
| `scripts\register-ds.bat [exe]` | Makes the exe the Driver Station's Default dashboard (the same thing the menu item does). It refuses while the DS is open, because the DS rewrites its settings when it closes, and it backs up the DS `.ini` first. |
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
| `https://github.com/8866CJ/EmberFX/releases/latest/download/EmberFX-Setup.exe` | Installer |
| `https://github.com/8866CJ/EmberFX/releases/latest/download/EmberFX-Portable.zip` | Portable zip |
| `https://github.com/8866CJ/EmberFX/releases/latest/download/install.ps1` | The script for `irm <link> \| iex` |

`.github/workflows/build.yml` runs the tests on every push and pull request.

## Code tour

```
src/main/java/emberfx/
  DashModule, ModuleContext,     the plugin API (package emberfx): keep it backward compatible,
  Setting, NT                    team modules compile against it
  app/
    Launcher, MainApp            start-up and shutdown
    ModuleLoader                 finds team module jars in ~/.emberfx/modules
    SessionLog                   records every NetworkTables value to a .wpilog per connection
  config/
    Config                       config.json (schema in its class comment)
    Layouts                      layout files: load from the robot, save to the robot project, merge
  autos/                         PathPlanner and Choreo auto files from the robot project, for the auto preview
  replay/                        log replay: LogIndex (loads and merges .wpilog), Replayer (publishes a moment
                                 into NT), HootConverter (.hoot via CTRE owlet)
  structs/                       WPILib struct schemas and decoding (StructRegistry, StructSubscriber, Poses)
  tiles/
    Tile                         base class: title, update(), track() for NT handles
    TileType, TileConfig         what a tile type is; one tile's saved settings
    TileTypes                    the list of tile types + automatic tile picking
    TextTile ... MatchTimerTile  one class per built-in tile; CanvasTile is the base for drawn ones (redrawn
                                 only when needsRedraw() says something changed), TopicGroup reads tables
  topics/
    LiveTopics                   topics the robot publishes now
    TopicCheck                   code topics vs live topics: missing, unexpected, wrong type
    ProjectScanner               topic names found in robot code
    Topics, FoundTopic           both merged
  ui/
    DashboardView                the window: tabs, module tabs, frame loop
    HeaderBar                    toolbar: tab name, REC label, robot address picker, connection pill
    SetupMenu                    the main menu (top of the left bar) and its actions
    TabPage                      one tab: tiles, drag/resize/snap, drop target, tile menus
    TileDialog                   Add/Edit tile dialog
    TopicList                    searchable, draggable topic list
    Notifications                pop-up messages from robot code (Elastic-compatible)
    TopicCheckDialog             Menu > Check topics
    ReplayLoader, ReplayBar      Menu > Open log to replay, and the replay controls
    SidePanel, TopicBrowser      the edit-mode Tiles/Topics panel and the Browse dialog, both built on TopicList
    Forms, Style                 shared dialogs; colors and small builders
src/main/resources/emberfx/ui/
  dashboard.css                  every fixed color and style
src/test/java/emberfx/...        one test class per class under test, in the same package
installer/EmberFX.iss       the Inno Setup installer script
examples/
  custom-tile/                   a module that adds a tile type
  custom-tab/                    a module that adds a whole tab (shooter tuning)
```

To add a built-in tile, write a `Tile` subclass and add one line to `TileTypes.BUILT_IN`. `TextTile` is the smallest example.

Conventions:
- Fixed styling goes in `dashboard.css` as style classes. Use inline styles only for colors that change at runtime.
- Report problems to the user, or log them with `System.Logger`. Don't swallow exceptions silently.
- Formatting follows `.editorconfig`: 4-space indents and UTF-8. Line endings are handled by `.gitattributes`; `.bat` and `.ps1` files must be CRLF.
- Source files are ASCII-only, because Windows compiles them as windows-1252. Build symbols like the REC dot from char codes.
- Run `.\gradlew.bat test` before sending changes, and add a test for new logic next to the class it tests.
