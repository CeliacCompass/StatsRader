param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'paths.ps1')
if (!$SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') -Candidate }
$releaseRoot = Get-BedwarsAppRoot $projectRoot
$jdkRoot = Get-BedwarsJavaHome $projectRoot
$jdkMetadata = Get-Content -LiteralPath (Join-Path $jdkRoot 'release') -Raw
if ($jdkMetadata -notmatch 'Temurin-17\.0\.20\.1\+1' -or $jdkMetadata -notmatch '79597447bd94') { throw 'Runtime changed: update the corresponding source archives and component notices before packaging.' }
$packageRoot = Join-Path $projectRoot ('build\package-' + [guid]::NewGuid().ToString('N'))
$payload = Join-Path $packageRoot 'payload'
$publishRoot = Join-Path $projectRoot 'publish'
New-Item -ItemType Directory -Path $payload,$publishRoot,(Join-Path $payload 'dist') -Force | Out-Null

# Explicit allowlist: never copy local settings, logs, screenshots, the workspace or installed client files.
foreach ($file in @('launcher.jar','agent.jar','bridge.jar','core.jar','screens.jar')) {
    Copy-Item -LiteralPath (Join-Path $releaseRoot "dist\$file") -Destination (Join-Path $payload "dist\$file")
}
& (Join-Path $jdkRoot 'bin\jlink.exe') --add-modules java.desktop,java.instrument,java.logging,java.management,jdk.management,jdk.attach,jdk.crypto.ec,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $payload 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'Portable Java runtime creation failed' }
foreach ($file in @('README-Portable.txt','THIRD-PARTY.txt')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "packaging\$file") -Destination (Join-Path $payload $file)
}
New-Item -ItemType Directory -Path (Join-Path $payload 'licenses\java') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $jdkRoot 'NOTICE') -Destination (Join-Path $payload 'licenses\java\NOTICE')
Copy-Item -LiteralPath (Join-Path $jdkRoot 'release') -Destination (Join-Path $payload 'licenses\java\release-metadata.txt')
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($library in @(@('asm','asm-9.8.jar'),@('gson','gson-2.13.1.jar'))) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $projectRoot ('.deps\' + $library[1])))
    try {
        $licenseRoot = Join-Path $payload ('licenses\' + $library[0])
        $found = 0
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notmatch '(?i)(LICENSE|NOTICE|COPYING)' -or $entry.FullName.EndsWith('/')) { continue }
            $target = [IO.Path]::GetFullPath((Join-Path $licenseRoot $entry.FullName))
            if (!$target.StartsWith([IO.Path]::GetFullPath($licenseRoot) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid license archive path' }
            New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true); $found++
        }
        if (!$found) {
            $licenseFile = Join-Path $projectRoot ("packaging\licenses\" + $library[0] + "\LICENSE.txt")
            if (!(Test-Path -LiteralPath $licenseFile)) { throw "Missing license for $($library[0])" }
            New-Item -ItemType Directory -Path $licenseRoot -Force | Out-Null
            Copy-Item -LiteralPath $licenseFile -Destination (Join-Path $licenseRoot 'LICENSE.txt')
        }
    } finally { $archive.Dispose() }
}
New-Item -ItemType Directory -Path (Join-Path $payload 'sources') -Force | Out-Null
$sourceHashes = @{
    'temurin-jdk17u-79597447bd94.tar.gz' = 'C7E2045844B32008CA77079586D209F59E15752ED355C713A1EC311C58ED206C'
    'temurin-build-e6ba7dec3d07.tar.gz' = 'B1340378B9ED62B32ACFACD012DD069EC2B9D940FC39D3C73F32142D64E89713'
}
foreach ($source in @('temurin-jdk17u-79597447bd94.tar.gz','temurin-build-e6ba7dec3d07.tar.gz')) {
    $sourceFile = Join-Path $projectRoot ('.deps\redistribution-sources\' + $source)
    if (!(Test-Path -LiteralPath $sourceFile)) { throw "Corresponding runtime source archive missing: $source" }
    if ((Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash -ne $sourceHashes[$source]) { throw "Source archive checksum mismatch: $source" }
    Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $payload ('sources\' + $source))
}

$releaseId = Split-Path $releaseRoot -Leaf
@("Product=StatsRader", "Version=0.3.1-beta.1", "Build=$releaseId", "Platform=Windows x86_64") | Set-Content -LiteralPath (Join-Path $payload 'VERSION.txt') -Encoding utf8
$manifest = Join-Path $packageRoot 'payload.manifest'
$files = @(Get-ChildItem -LiteralPath $payload -File -Recurse | Sort-Object FullName)
$manifestLines = foreach ($file in $files) {
    $relative = $file.FullName.Substring($payload.Length + 1).Replace('\','/')
    if ($relative -match '(?i)(settings\.properties|agent\.log|runtime-\d|attach-\d|badlion client|1\.8\.9\.jar|optifine|authlib|guava)' -or $relative.Contains('..')) { throw "Unexpected private/client file in payload: $relative" }
    (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $relative
}
[IO.File]::WriteAllLines($manifest, $manifestLines, (New-Object Text.UTF8Encoding($false)))
$zip = Join-Path $packageRoot 'payload.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($payload, $zip, [IO.Compression.CompressionLevel]::Optimal, $false)
$digestFile = Join-Path $packageRoot 'payload.sha256'
(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant() | Set-Content -LiteralPath $digestFile -Encoding ascii
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (!(Test-Path -LiteralPath $compiler)) { throw '.NET Framework C# compiler missing' }
$output = Join-Path $publishRoot ('StatsRader-0.3.1-beta.1-' + $releaseId + '-win-x64.exe')
& $compiler /nologo /target:winexe /platform:x64 /optimize+ "/win32icon:$(Join-Path $projectRoot 'assets\statsrader.ico')" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.IO.Compression.dll "/win32manifest:$(Join-Path $projectRoot 'packaging\portable.manifest')" "/resource:$zip,payload.zip" "/resource:$manifest,payload.manifest" "/resource:$digestFile,payload.sha256" "/out:$output" (Join-Path $projectRoot 'packaging\Portable.cs')
if ($LASTEXITCODE -ne 0) { throw 'Portable EXE compilation failed' }

# Test the shipped EXE from a new directory, with spaces/Unicode, not from the source checkout.
$relocated = Join-Path $packageRoot 'Empfaenger Test ü'
New-Item -ItemType Directory -Path $relocated -Force | Out-Null
$copiedExe = Join-Path $relocated 'StatsRader.exe'; Copy-Item -LiteralPath $output -Destination $copiedExe
$testCache = Join-Path $relocated 'Clean App Cache'
$process = Start-Process -FilePath $copiedExe -ArgumentList @('--verify-package', ('"' + $testCache + '"')) -WindowStyle Hidden -PassThru -Wait
if ($process.ExitCode -ne 0) { throw "Portable extraction failed; inspect $testCache" }
$installed = [IO.File]::ReadAllText((Join-Path $testCache 'verified-path.txt'))
$runtimeJava = Join-Path $installed 'runtime\bin\java.exe'
$priorJavaHome = $env:JAVA_HOME
$priorPath = $env:PATH
try {
    $env:JAVA_HOME = ''
    $env:PATH = Join-Path $env:WINDIR 'System32'
    & $runtimeJava '-Dfile.encoding=UTF-8' '-Djava.awt.headless=true' -jar (Join-Path $installed 'dist\launcher.jar') --self-check
    if ($LASTEXITCODE -ne 0) { throw 'Bundled runtime self-check failed' }
    $fixtureRoot = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build') -Directory -Filter 'test-*' | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'classes\ManualStartupTest.class') } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (!$fixtureRoot) { throw 'Integration fixture missing; run tools\build.ps1 -Candidate first.' }
    $priorToolOptions = $env:JAVA_TOOL_OPTIONS
    try {
        $env:JAVA_TOOL_OPTIONS = Get-BedwarsStartupOption $installed
        & $runtimeJava '-XX:+DisableAttachMechanism' '-Dbedwarstab.manualActivation=true' "-Dbedwarstab.dataDir=$(Join-Path $relocated 'Sandbox Player Data')" -cp "$(Join-Path $fixtureRoot.FullName 'classes');$(Join-Path $installed 'dist\agent.jar')" ManualStartupTest $installed
        if ($LASTEXITCODE -ne 0) { throw 'Shipped Java/agent/launcher integration failed' }
    } finally { $env:JAVA_TOOL_OPTIONS = $priorToolOptions }
} finally { $env:JAVA_HOME = $priorJavaHome; $env:PATH = $priorPath }
# A second launch must reuse the same verified package without touching settings or re-extracting.
$settingsSentinel = Join-Path $testCache 'settings.properties'
[IO.File]::WriteAllText($settingsSentinel, 'sentinel=preserve-local-user-settings')
$second = Start-Process -FilePath $copiedExe -ArgumentList @('--verify-package', ('"' + $testCache + '"')) -WindowStyle Hidden -PassThru -Wait
if ($second.ExitCode -ne 0 -or [IO.File]::ReadAllText((Join-Path $testCache 'verified-path.txt')) -ne $installed -or [IO.File]::ReadAllText($settingsSentinel) -ne 'sentinel=preserve-local-user-settings') { throw 'Portable reuse/settings preservation failed' }
$sha = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
($sha + '  ' + (Split-Path $output -Leaf)) | Set-Content -LiteralPath ($output + '.sha256') -Encoding ascii
$shareFile = Join-Path $publishRoot 'StatsRader-Portable.exe'
Copy-Item -LiteralPath $output -Destination $shareFile -Force
($sha + '  StatsRader-Portable.exe') | Set-Content -LiteralPath ($shareFile + '.sha256') -Encoding ascii
Copy-Item -LiteralPath (Join-Path $projectRoot 'packaging\README-Portable.txt') -Destination (Join-Path $publishRoot 'README.txt') -Force
$output | Set-Content -LiteralPath (Join-Path $publishRoot 'latest.txt') -Encoding utf8
Write-Output "PASS: single EXE extraction, moved path with Unicode, bundled Java without JAVA_HOME/PATH, cache reuse and settings preservation."
Write-Output "Portable file: $output"
Write-Output "Size MiB: $([Math]::Round((Get-Item -LiteralPath $output).Length / 1MB, 1))"
Write-Output "SHA256: $sha"
