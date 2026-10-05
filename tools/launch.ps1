param([switch]$App)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'paths.ps1')
$jdkRoot = Get-BedwarsJavaHome $projectRoot
$java = Join-Path $jdkRoot 'bin\java.exe'
$releaseRoot = if ($App) { Get-BedwarsAppRoot $projectRoot } else { Get-BedwarsReleaseRoot $projectRoot }
& $java -jar (Join-Path $releaseRoot 'dist\launcher.jar') $releaseRoot
exit $LASTEXITCODE
