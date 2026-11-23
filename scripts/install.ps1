# Installs or updates EmberFX from the latest GitHub release. Paste into PowerShell or Windows Terminal:
#
#   irm https://github.com/8866CJ/EmberFX/releases/latest/download/install.ps1 | iex
#
# No admin rights needed: it installs for the current user, with Start menu and desktop shortcuts.
# Settings and logs in %USERPROFILE%\.emberfx are kept.
& {
    $ErrorActionPreference = 'Stop'
    $repo = '__REPO__'                                   # filled in by the release workflow (so forks install their own build)
    if ($repo.StartsWith('__')) { $repo = '8866CJ/EmberFX' }

    $url = "https://github.com/$repo/releases/latest/download/EmberFX-Setup.exe"
    $setup = Join-Path $env:TEMP 'EmberFX-Setup.exe'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue' # the progress bar makes downloads much slower in Windows PowerShell

    Write-Host "Downloading EmberFX from github.com/$repo ..."
    Invoke-WebRequest -Uri $url -OutFile $setup -UseBasicParsing

    Write-Host 'Installing...'
    $p = Start-Process -FilePath $setup -ArgumentList '/SILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/CURRENTUSER' -Wait -PassThru
    Remove-Item $setup -ErrorAction SilentlyContinue
    if ($p.ExitCode -ne 0) { throw "The installer stopped with exit code $($p.ExitCode)." }

    Write-Host 'Done. Open "EmberFX" from the Start menu.'
}
