; Inno Setup script for the EmberFX installer.
; Built by: gradlew.bat packageInstaller (or scripts\build-exe.bat), which passes AppVersion and SourceDir.
; Output: build\installer\EmberFX-Setup-<version>.exe, one file with the app and its own Java.
;
; Installs to %LOCALAPPDATA%\Programs\EmberFX without admin rights, or to Program Files
; if the user picks "Install for all users". Settings and logs live in %USERPROFILE%\.emberfx
; and are kept on uninstall and upgrade.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\dist\EmberFX"
#endif

[Setup]
; Same id for every version, so a new installer upgrades the old install. Never change it.
AppId={{0E555C27-BB0F-4720-B458-5BA0DF7A355A}
AppName=EmberFX
AppVersion={#AppVersion}
AppPublisher=EmberFX contributors
DefaultDirName={autopf}\EmberFX
DefaultGroupName=EmberFX
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputBaseFilename=EmberFX-Setup-{#AppVersion}
; jpackage writes this icon next to the exe on some JDK builds only, so it's optional.
#if FileExists(AddBackslash(SourceDir) + "EmberFX.ico")
SetupIconFile={#SourceDir}\EmberFX.ico
#endif
UninstallDisplayIcon={app}\EmberFX.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

; Upgrading from 1.0.x, when the app was called FRC Dashboard: remove its old program files and shortcuts.
[InstallDelete]
Type: files; Name: "{app}\FRCDashboard.exe"
Type: files; Name: "{app}\FRCDashboard.ico"
Type: files; Name: "{app}\app\FRCDashboard.*"
Type: files; Name: "{autoprograms}\FRC Dashboard.lnk"
Type: files; Name: "{autodesktop}\FRC Dashboard.lnk"

[Icons]
Name: "{autoprograms}\EmberFX"; Filename: "{app}\EmberFX.exe"
Name: "{autodesktop}\EmberFX"; Filename: "{app}\EmberFX.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\EmberFX.exe"; Description: "Start EmberFX"; Flags: nowait postinstall skipifsilent
