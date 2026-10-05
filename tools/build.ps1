param([switch]$Candidate)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'paths.ps1')
$jdkRoot = Get-BedwarsJavaHome $projectRoot
$javac = Join-Path $jdkRoot 'bin\javac.exe'
$jar = Join-Path $jdkRoot 'bin\jar.exe'
$build = Join-Path $projectRoot ('build\' + [guid]::NewGuid().ToString('N'))
$releaseName = 'build-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$releaseRoot = Join-Path $projectRoot ('releases\' + $releaseName)
$dist = Join-Path $releaseRoot 'dist'
New-Item -ItemType Directory -Path $build,$dist -Force | Out-Null
$asm = Join-Path $projectRoot '.deps\asm-9.8.jar'
$gson = Join-Path $projectRoot '.deps\gson-2.13.1.jar'
foreach ($module in @('bridge','boot','launcher','core','screens')) {
    $out = Join-Path $build $module
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot "src\$module") -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
    if ($module -in @('launcher','core','boot')) { $sources += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\shared') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName) }
    $cp = "$asm;$gson;$(Join-Path $build 'bridge')"
    if ($module -eq 'screens') {
        $cp += ";$(Join-Path $env:APPDATA 'Badlion Client\Data\1.8.9.jar')"
        $cp += ";$(Join-Path $env:APPDATA 'Badlion Client\Data\OptiFine_1.8.9_HD_U_M5.jar')"
        $cp += ";$(Join-Path $env:APPDATA '.minecraft\libraries\com\google\guava\guava\17.0\guava-17.0.jar')"
        $cp += ";$(Join-Path $env:APPDATA '.minecraft\libraries\com\mojang\authlib\1.5.21\authlib-1.5.21.jar')"
    }
    & $javac -encoding UTF-8 --release 17 -cp $cp -d $out @sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $module" }
}
# Bundle dependencies only in the isolated core loader; retain their license files.
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($dependency in @($asm,$gson)) {
    $archive = [IO.Compression.ZipFile]::OpenRead($dependency)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/') -or $entry.FullName -eq 'module-info.class' -or $entry.FullName -match '^META-INF/(MANIFEST.MF|versions/|.*\.(SF|RSA|DSA)$)') { continue }
            $target = [IO.Path]::GetFullPath((Join-Path (Join-Path $build 'core') $entry.FullName))
            $allowed = [IO.Path]::GetFullPath((Join-Path $build 'core')) + [IO.Path]::DirectorySeparatorChar
            if (!$target.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid dependency archive path' }
            New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
    } finally { $archive.Dispose() }
}
$manifest = Join-Path $build 'agent.mf'
@('Manifest-Version: 1.0','Agent-Class: local.bedwarstab.Boot','Premain-Class: local.bedwarstab.Boot','Can-Retransform-Classes: true','Can-Redefine-Classes: true','') | Set-Content -LiteralPath $manifest -Encoding ascii
& $jar --create --file (Join-Path $dist 'agent.jar') --manifest $manifest -C (Join-Path $build 'boot') .
if ($LASTEXITCODE -ne 0) { throw 'Agent packaging failed' }
& $jar --create --file (Join-Path $dist 'launcher.jar') --main-class local.bedwarstab.Launcher -C (Join-Path $build 'launcher') . -C (Join-Path $projectRoot 'assets') statsrader-logo.png
if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed' }
foreach ($module in @('bridge','core','screens')) {
    & $jar --create --file (Join-Path $dist "$module.jar") -C (Join-Path $build $module) .
    if ($LASTEXITCODE -ne 0) { throw "Packaging failed: $module" }
}
Write-Output 'Built launcher.jar, agent.jar, bridge.jar, core.jar and screens.jar.'
& (Join-Path $PSScriptRoot 'test.ps1') -ReleaseRoot $releaseRoot
# Switch only after all checks pass. Running launchers keep their complete old release.
$pointerName = if ($Candidate) { 'app.txt' } else { 'current.txt' }
$pointer = Join-Path $projectRoot ('releases\' + $pointerName)
$pending = Join-Path $projectRoot ('releases\current-' + [guid]::NewGuid().ToString('N') + '.tmp')
$releaseName | Set-Content -LiteralPath $pending -Encoding utf8
Move-Item -LiteralPath $pending -Destination $pointer -Force
Write-Output "Tested release selected ($pointerName): $releaseName"
