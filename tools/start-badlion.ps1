param([switch]$App)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'paths.ps1')
$releaseRoot = if ($App) { Get-BedwarsAppRoot $projectRoot } else { Get-BedwarsReleaseRoot $projectRoot }
$option = Get-BedwarsStartupOption $releaseRoot
if ($App) { $option = '"-Dbedwarstab.manualActivation=true" ' + $option }
$launcher = Join-Path $env:LOCALAPPDATA 'Programs\Badlion Client\Badlion Client.exe'
if (!(Test-Path -LiteralPath $launcher)) { throw 'Badlion-Launcher wurde nicht am erwarteten Installationsort gefunden.' }
if (Get-Process -Name 'Badlion Client' -ErrorAction SilentlyContinue) {
    throw 'Badlion und Minecraft zuerst vollstaendig beenden (auch das Badlion-Symbol im Infobereich), dann dieses Skript erneut starten. Es wird kein laufendes Spiel geschlossen.'
}
$start = New-Object System.Diagnostics.ProcessStartInfo
# Badlion writes authentication objects to inherited console output. Discard both
# streams in a separate hidden command process; agent diagnostics use their own files.
$start.FileName = Join-Path $env:SystemRoot 'System32\cmd.exe'
$start.Arguments = '/d /s /c ""' + $launcher + '" >NUL 2>&1"'
$start.WorkingDirectory = Split-Path $launcher -Parent
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
# Only this launcher and its children inherit the startup agent. No global settings change.
$existing = $start.EnvironmentVariables['JAVA_TOOL_OPTIONS']
$start.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = ($existing + ' ' + $option).Trim()
$process = [System.Diagnostics.Process]::Start($start)
Write-Output 'Badlion-Start aufgerufen. Jetzt Minecraft 1.8.9 im geoeffneten Launcher starten.'
if ($App) { Write-Output 'Nach dem Spielstart Start-BedwarsTab-App.cmd oeffnen und Inject klicken.' }
else { Write-Output 'Die Erweiterung versucht sich automatisch zu laden; kein Klick auf Injizieren erforderlich.' }
Write-Output 'Diagnose: %LOCALAPPDATA%\BedwarsTab\startup-<Spiel-PID>.log und agent.log.'
Write-Output 'Falls kein neues startup-Protokoll entsteht, hat Badlion die Java-Startoption nicht uebernommen.'
