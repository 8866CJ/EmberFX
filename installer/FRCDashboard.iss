; Inno Setup script for the FRC Dashboard installer.
; Built by: gradlew.bat packageInstaller (or scripts\build-exe.bat), which passes AppVersion and SourceDir.
; Output: build\installer\FRCDashboard-Setup-<version>.exe, one file with the app and its own Java.
;
; Installs to %LOCALAPPDATA%\Programs\FRC Dashboard without admin rights, or to Program Files
; if the user picks "Install for all users". Settings and logs live in %USERPROFILE%\.frcdash
; and are kept on uninstall and upgrade.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\dist\FRCDashboard"
#endif

[Setup]
; Same id for every version, so a new installer upgrades the old install. Never change it.
AppId={{0E555C27-BB0F-4720-B458-5BA0DF7A355A}
AppName=FRC Dashboard
AppVersion={#AppVersion}
AppPublisher=FRCDashboard contributors
DefaultDirName={autopf}\FRC Dashboard
DefaultGroupName=FRC Dashboard
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputBaseFilename=FRCDashboard-Setup-{#AppVersion}
SetupIconFile={#SourceDir}\FRCDashboard.ico
UninstallDisplayIcon={app}\FRCDashboard.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\FRC Dashboard"; Filename: "{app}\FRCDashboard.exe"
Name: "{autodesktop}\FRC Dashboard"; Filename: "{app}\FRCDashboard.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\FRCDashboard.exe"; Description: "Start FRC Dashboard"; Flags: nowait postinstall skipifsilent
