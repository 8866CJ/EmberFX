# EmberFX

A driver dashboard any FRC team can use. Build your own tabs by dragging topics onto them. Tiles include text, boolean box, number bar, graph, button, toggle, slider, input, auto chooser, camera stream and field map. Topics come from the live robot or straight from your robot code. Every NetworkTables value is recorded to `.wpilog` files for AdvantageScope.

## Install (Windows)

**Installer:** download `EmberFX-Setup.exe` from the [latest release](https://github.com/8866CJ/EmberFX/releases/latest) and run it.

**Or paste this into PowerShell / Windows Terminal:**

```powershell
irm https://github.com/8866CJ/EmberFX/releases/latest/download/install.ps1 | iex
```

Both include Java and need no admin rights. Running either again updates the dashboard. Your settings are kept.

No install: `EmberFX-Portable.zip` from the same release runs from any folder.

**Coming from FRC Dashboard 1.0.x?** EmberFX is the same app, renamed. Installing it upgrades in place and moves your settings, layouts and logs over. Afterwards, run **Setup > Use as Driver Station dashboard** again, because the program file has a new name.

## Get started

1. **Setup > Settings:** enter your team number.
2. **Setup > Link robot project:** pick your robot code folder, so the dashboard can find topic names before the robot is on.
3. **Topics:** drag topics onto a tab. Use **+** to add more tabs.
4. **Setup > Use as Driver Station dashboard:** the DS will open the dashboard for you. Close the DS first.

## More

- [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md): every feature, the files it uses, building, and a tour of the code.
- [MODULES.md](MODULES.md): write your own tile types or tabs as a plugin jar.
