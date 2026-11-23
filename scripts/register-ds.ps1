# Points the Driver Station's "Default" dashboard at EmberFX.exe.
# Backs up the DS settings file first, and refuses to run while the DS is open
# (the DS overwrites this file when it closes, which would undo the change).
# CmdletBinding makes a mistyped option an error instead of being silently ignored.
[CmdletBinding()]
param(
    [string]$Exe,
    [string]$Ini = 'C:\Users\Public\Documents\FRC\FRC DS Data Storage.ini'
)
$ErrorActionPreference = 'Stop'
$ini = $Ini

if (-not $Exe) {
    # Next to this script in the app folder, else the build output in the repo.
    foreach ($p in @("$PSScriptRoot\EmberFX.exe", "$PSScriptRoot\..\build\dist\EmberFX\EmberFX.exe")) {
        if (Test-Path $p) { $Exe = $p; break }
    }
}
if (-not $Exe -or -not (Test-Path $Exe)) {
    Write-Host "Can't find EmberFX.exe. Build it with scripts\build-exe.bat, or pass its path:"
    Write-Host "  register-ds.bat C:\path\to\EmberFX.exe"
    exit 1
}
$Exe = (Resolve-Path $Exe).Path

if (Get-Process -Name DriverStation -ErrorAction SilentlyContinue) {
    Write-Host 'The Driver Station is open. Close it and run this again.'
    exit 1
}
if (-not (Test-Path $ini)) {
    Write-Host "No Driver Station settings at $ini."
    Write-Host 'Open and close the Driver Station once, then run this again.'
    exit 1
}

# Latin-1 reads and writes every byte unchanged, so nothing else in the file moves.
$enc = [System.Text.Encoding]::GetEncoding(28591)
$text = [System.IO.File]::ReadAllText($ini, $enc)
$pattern = '(?m)^DashboardCmdLine[ \t]*=[^\r\n]*'
if ($text -notmatch $pattern) {
    Write-Host "No DashboardCmdLine line in $ini. Pick any dashboard in the DS once, close it, and run this again."
    exit 1
}

$backup = "$ini.$(Get-Date -Format yyyyMMdd_HHmmss).bak"
Copy-Item -LiteralPath $ini -Destination $backup

# The DS stores the command with doubled quotes and doubled backslashes.
$line = 'DashboardCmdLine = ""' + $Exe.Replace('\', '\\') + '""'
$text = [regex]::Replace($text, $pattern, [System.Text.RegularExpressions.MatchEvaluator] { param($m) $line })
[System.IO.File]::WriteAllText($ini, $text, $enc)

Write-Host "Done. The Driver Station's Default dashboard now starts:"
Write-Host "  $Exe"
Write-Host "Backup of the old settings: $backup"
Write-Host 'In the DS Setup tab, make sure Dashboard Type is set to Default.'
