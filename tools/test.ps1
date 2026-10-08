param([string]$ReleaseRoot)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'paths.ps1')
$jdkRoot = Get-BedwarsJavaHome $projectRoot
if (!$ReleaseRoot) { $ReleaseRoot = Get-BedwarsReleaseRoot $projectRoot }
$java = Join-Path $jdkRoot 'bin\java.exe'
$javac = Join-Path $jdkRoot 'bin\javac.exe'
$jar = Join-Path $jdkRoot 'bin\jar.exe'
$testRoot = Join-Path $projectRoot ('build\test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $testRoot 'classes'),(Join-Path $testRoot 'dist') -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $ReleaseRoot 'dist') -Filter '*.jar' | Copy-Item -Destination (Join-Path $testRoot 'dist')
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
$cp = "$(Join-Path $testRoot 'dist\core.jar');$(Join-Path $testRoot 'dist\bridge.jar');$(Join-Path $testRoot 'dist\agent.jar');$(Join-Path $testRoot 'dist\launcher.jar');$(Join-Path $testRoot 'dist\screens.jar')"
& $javac -encoding UTF-8 --release 17 -cp $cp -d (Join-Path $testRoot 'classes') @testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.core.CoreTest "$env:APPDATA\Badlion Client\Data\1.8.9.jar" $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" TableRenderTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Table rendering tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.AttachDiagnosticsTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Attach diagnostics tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.PortableStartupTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Portable startup tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.core.WorkerTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Worker tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.core.AutoWhoTest
if ($LASTEXITCODE -ne 0) { throw 'Auto /who state tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.core.AutoWhoAlertTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'In-game alert tests failed' }
& $java -cp "$(Join-Path $testRoot 'classes');$cp" local.bedwarstab.SettingsConcurrencyTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Settings concurrency tests failed' }
@('Manifest-Version: 1.0','Premain-Class: TestAgent','Can-Retransform-Classes: true','') | Set-Content -LiteralPath (Join-Path $testRoot 'test.mf') -Encoding ascii
& $jar --create --file (Join-Path $testRoot 'test-agent.jar') --manifest (Join-Path $testRoot 'test.mf') -C (Join-Path $testRoot 'classes') TestAgent.class
if ($LASTEXITCODE -ne 0) { throw 'Test agent packaging failed' }
& $java "-javaagent:$(Join-Path $testRoot 'test-agent.jar')" -cp "$(Join-Path $testRoot 'classes');$(Join-Path $testRoot 'dist\agent.jar')" IntegrationTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Instrumentation test failed' }
& $java "-javaagent:$(Join-Path $testRoot 'test-agent.jar')" -cp "$(Join-Path $testRoot 'classes');$(Join-Path $testRoot 'dist\agent.jar');$(Join-Path $env:APPDATA 'Badlion Client\Data\OptiFine_1.8.9_HD_U_M5.jar')" DirectIntegrationTest $testRoot
if ($LASTEXITCODE -ne 0) { throw 'Direct game integration test failed' }
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = Get-BedwarsStartupOption $testRoot
    & $java '-XX:+DisableAttachMechanism' "-Dbedwarstab.dataDir=$(Join-Path $testRoot 'startup-state')" -cp (Join-Path $testRoot 'classes') StartupTest $testRoot
    if ($LASTEXITCODE -ne 0) { throw 'Startup agent test failed' }
    & $java '-XX:+DisableAttachMechanism' '-Dbedwarstab.manualActivation=true' "-Dbedwarstab.dataDir=$(Join-Path $testRoot 'manual-state')" -cp "$(Join-Path $testRoot 'classes');$(Join-Path $testRoot 'dist\agent.jar')" ManualStartupTest $testRoot
    if ($LASTEXITCODE -ne 0) { throw 'Manual app startup test failed' }
} finally { $env:JAVA_TOOL_OPTIONS = $previousOptions }
Write-Output 'All tests passed. These tests do not inject into Badlion or use real API keys.'
