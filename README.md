# EmberFX

A driver dashboard any FRC team can use. Build your own tabs by dragging topics onto them. Tiles include text, gauges, graphs, checklists, alerts, buttons, toggles, sliders, inputs, the auto chooser, a match timer, swerve and drive views, a field map that previews your PathPlanner or Choreo auto, camera streams, and editors for PID controllers, preferences and running commands. Topics come from the live robot or straight from your robot code, struct values such as `Pose2d` are decoded, and robot code written for Elastic's notifications and tab switching works unchanged. Every NetworkTables value is recorded to `.wpilog` files for AdvantageScope.

## Install (Windows)

**Installer:** download `EmberFX-Setup.exe` from the [latest release](https://github.com/8866CJ/EmberFX/releases/latest) and run it.

**Or paste this into PowerShell / Windows Terminal:**

```powershell
irm https://github.com/8866CJ/EmberFX/releases/latest/download/install.ps1 | iex
```

Both include Java and need no admin rights. Running either again updates the dashboard. Your settings are kept.

No install: `EmberFX-Portable.zip` from the same release runs from any folder.

**Coming from FRC Dashboard 1.0.x?** EmberFX is the same app, renamed. Installing it upgrades in place and moves your settings, layouts and logs over. Afterwards, run **Menu > Use as Driver Station dashboard** again, because the program file has a new name.

## Get started

1. **Settings** (bottom of the left bar): enter your team number.
2. **Menu > Link robot project:** pick your robot code folder, so the dashboard can find topic names before the robot is on.
3. **Edit** (the pencil, or Ctrl+E): drag topics or tile types from the side panel onto the tab. Use **+** on the left bar to add tabs. Turn edit mode off before a match so nothing gets bumped.
4. **Menu > Use as Driver Station dashboard:** the DS will open the dashboard for you. Close the DS first.

## More

- [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md): every feature, the files it uses, building, and a tour of the code.
- [MODULES.md](MODULES.md): write your own tile types or tabs as a plugin jar.
