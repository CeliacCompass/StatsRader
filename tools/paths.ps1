function Get-BedwarsJavaHome([string]$ProjectRoot) {
    $marker = Join-Path $ProjectRoot '.deps\jdk-path.txt'
    if (Test-Path -LiteralPath $marker) {
        $candidate = (Get-Content -LiteralPath $marker -Raw).Trim()
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) { return $candidate }
    }
    $runtimeRoot = Join-Path $ProjectRoot '.deps\jdk'
    if (Test-Path -LiteralPath $runtimeRoot) {
        $candidate = Get-ChildItem -LiteralPath $runtimeRoot -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    throw 'Java-Werkzeuge fehlen. Zuerst tools\bootstrap.ps1 ausführen.'
}
function Get-BedwarsReleaseRoot([string]$ProjectRoot) {
    $releases = [IO.Path]::GetFullPath((Join-Path $ProjectRoot 'releases'))
    $pointer = Join-Path $releases 'current.txt'
    $name = 'config-menu'
    if (Test-Path -LiteralPath $pointer) { $name = (Get-Content -LiteralPath $pointer -Raw).Trim() }
    if ($name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Ungültiger Versionsverweis in releases\current.txt' }
    $candidate = Join-Path $releases $name
    if (!(Test-Path -LiteralPath (Join-Path $candidate 'dist\launcher.jar'))) { throw 'Keine vollständige Version gefunden. tools\build.ps1 ausführen.' }
    return $candidate
}

function Get-BedwarsAppRoot([string]$ProjectRoot) {
    $releases = [IO.Path]::GetFullPath((Join-Path $ProjectRoot 'releases'))
    $pointer = Join-Path $releases 'app.txt'
    if (!(Test-Path -LiteralPath $pointer)) { throw 'App-Version fehlt. tools\build.ps1 -Candidate ausfuehren.' }
    $name = (Get-Content -LiteralPath $pointer -Raw).Trim()
    if ($name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Ungueltiger Versionsverweis in releases\app.txt' }
    $candidate = Join-Path $releases $name
    if (!(Test-Path -LiteralPath (Join-Path $candidate 'dist\launcher.jar'))) { throw 'Unvollstaendige App-Version.' }
    return $candidate
}

function Get-BedwarsStartupOption([string]$ReleaseRoot) {
    $resolved = [IO.Path]::GetFullPath($ReleaseRoot)
    foreach ($file in @('agent.jar','core.jar','bridge.jar','screens.jar')) {
        if (!(Test-Path -LiteralPath (Join-Path $resolved "dist\$file"))) { throw "Unvollstaendige Version: $file" }
    }
    if ($resolved.Contains('"') -or $resolved.Contains("`r") -or $resolved.Contains("`n")) { throw 'Nicht unterstuetzter Pfad' }
    # Quote the whole JVM option; JAVA_TOOL_OPTIONS performs its own tokenization.
    return '"-javaagent:' + (Join-Path $resolved 'dist\agent.jar') + '=' + $resolved + '"'
}
