$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$deps = Join-Path $projectRoot '.deps'
New-Item -ItemType Directory -Path $deps -Force | Out-Null
$downloads = @{
    'asm-9.8.jar' = 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.8/asm-9.8.jar'
    'gson-2.13.1.jar' = 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar'
}
foreach ($file in $downloads.Keys) {
    $target = Join-Path $deps $file
    if (!(Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri $downloads[$file] -OutFile $target -TimeoutSec 120 }
    $expected = (Invoke-RestMethod -Uri ($downloads[$file] + '.sha1') -TimeoutSec 30).ToString().Trim().ToUpperInvariant()
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA1).Hash -ne $expected) { throw "Checksum mismatch: $file" }
    Write-Output "Verified $file"
}
$jdkMarker = Join-Path $deps 'jdk-path.txt'
if (!(Test-Path -LiteralPath $jdkMarker)) {
    $releases = Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse' -TimeoutSec 30
    $package = $releases[0].binary.package
    $zip = Join-Path $deps 'jdk17.zip'
    if (!(Test-Path -LiteralPath $zip)) { Invoke-WebRequest -Uri $package.link -OutFile $zip -TimeoutSec 300 }
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $package.checksum.ToUpperInvariant()) { throw 'JDK checksum mismatch' }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $deps 'jdk') -Force
    $jdk = Get-ChildItem -LiteralPath (Join-Path $deps 'jdk') -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
    if (!$jdk) { throw 'javac not found after extraction' }
    $jdk.FullName | Set-Content -LiteralPath $jdkMarker -Encoding utf8
}
Write-Output 'Build dependencies ready.'
