[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OtpJar,
    [Parameter(Mandatory)][string]$OtpJdk,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Destination,
    [switch]$SkipBuild,
    [switch]$NoZip
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$backend = Join-Path $repo 'backend'
if (!$Destination) { $Destination = Join-Path $backend ('launcher\build\packages\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
$Destination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists. Choose a new folder; packaging never deletes existing files.' }
if (!$Jdk) { throw 'Set JAVA_HOME or -Jdk to a Windows x64 JDK 21.' }
$Jdk = (Resolve-Path -LiteralPath $Jdk).Path
$OtpJdk = (Resolve-Path -LiteralPath $OtpJdk).Path
$OtpJar = (Resolve-Path -LiteralPath $OtpJar).Path
foreach ($file in @((Join-Path $Jdk 'bin\java.exe'), (Join-Path $Jdk 'bin\jpackage.exe'), (Join-Path $Jdk 'bin\jlink.exe'),
    (Join-Path $OtpJdk 'bin\javac.exe'), (Join-Path $OtpJdk 'bin\jlink.exe'))) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing Windows JDK tool: $file" }
}
# Java prints version/settings to stderr. Inspect version files without native stderr handling in PS 5.1.
$release21 = Get-Content -LiteralPath (Join-Path $Jdk 'release') -Raw
$release25 = Get-Content -LiteralPath (Join-Path $OtpJdk 'release') -Raw
if ($release21 -notmatch 'JAVA_VERSION="21\.' -or $release21 -notmatch 'OS_ARCH="(?:amd64|x86_64)"') { throw 'Launcher/backend packaging requires a Windows x64 JDK 21.' }
if ($release25 -notmatch 'JAVA_VERSION="25\.' -or $release25 -notmatch 'OS_ARCH="(?:amd64|x86_64)"') { throw 'The prepared OTP version requires a Windows x64 JDK 25.' }
if (!$SkipBuild) {
    Push-Location -LiteralPath $repo
    try {
        & .\gradlew.bat -p backend test :launcher:test installDist :launcher:installDist --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Backend/launcher build or tests failed.' }
    } finally { Pop-Location }
}
$backendLib = Join-Path $backend 'build\install\backend\lib'
$launcherLib = Join-Path $backend 'launcher\build\install\launcher\lib'
if (!(Test-Path -LiteralPath $backendLib) -or !(Test-Path -LiteralPath $launcherLib)) { throw 'Run the backend and launcher installDist tasks first.' }
$work = Join-Path $backend ('launcher\build\staging\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work -Force | Out-Null
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Write-Host 'Creating bundled Java 21 runtime...'
& (Join-Path $Jdk 'bin\jlink.exe') --module-path (Join-Path $Jdk 'jmods') --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.httpserver,jdk.naming.dns,jdk.zipfs --strip-debug --no-header-files --no-man-pages --output (Join-Path $work 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'Java 21 runtime packaging failed.' }
$launcherJar = Get-ChildItem -LiteralPath $launcherLib -Filter 'launcher*.jar' -File | Select-Object -First 1
if (!$launcherJar) { throw 'Launcher main JAR not found.' }
Write-Host 'Creating portable RNDTransitServer.exe...'
# jpackage checks Windows ACLs using Files.isWritable; mapped workspace drives can
# fail that probe despite supporting file creation. Build on the local temp volume.
$imageDestination = Join-Path ([IO.Path]::GetTempPath()) ('rnd-server-package-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $imageDestination | Out-Null
& (Join-Path $Jdk 'bin\jpackage.exe') --type app-image --name RNDTransitServer --app-version 1.0.0 --vendor RNDTransitMTL --description 'RNDTransit backend and routing server' --input $launcherLib --main-jar $launcherJar.Name --main-class transit.launcher.LauncherKt --runtime-image (Join-Path $work 'runtime') --dest $imageDestination --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'Windows app-image packaging failed.' }
Copy-Item -LiteralPath (Join-Path $imageDestination 'RNDTransitServer') -Destination $Destination -Recurse
$app = Join-Path $Destination 'RNDTransitServer'
$services = Join-Path $app 'services'
New-Item -ItemType Directory -Path (Join-Path $services 'otp'), (Join-Path $services 'bridge'), (Join-Path $services 'backend\lib') -Force | Out-Null
Copy-Item -LiteralPath $OtpJar -Destination (Join-Path $services 'otp\otp.jar')
Get-ChildItem -LiteralPath $backendLib -File | Copy-Item -Destination (Join-Path $services 'backend\lib')
Write-Host 'Compiling production bridge (no test controls)...'
& (Join-Path $OtpJdk 'bin\javac.exe') -cp $OtpJar -d (Join-Path $services 'bridge') (Join-Path $backend 'otp\stm-scheduled\ExoRealtimeFeed.java') (Join-Path $backend 'otp\stm-scheduled\StmRealtimeAdapter.java')
if ($LASTEXITCODE -ne 0) { throw 'Bridge compilation failed.' }
Write-Host 'Creating separate OTP Java 25 runtime...'
# ALL-MODULE-PATH preserves compatibility with OTP plugins and its bundled libraries.
& (Join-Path $OtpJdk 'bin\jlink.exe') --module-path (Join-Path $OtpJdk 'jmods') --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --output (Join-Path $services 'otp-runtime')
if ($LASTEXITCODE -ne 0) { throw 'OTP Java 25 runtime packaging failed.' }
Copy-Item -LiteralPath (Join-Path $backend 'launcher\SERVER_GUIDE.md') -Destination (Join-Path $app 'SERVER_GUIDE.md')
Copy-Item -LiteralPath (Join-Path $backend 'launcher\README.md') -Destination (Join-Path $app 'VERIFICATION.md')
Copy-Item -LiteralPath (Join-Path $backend 'launcher\THIRD_PARTY.md') -Destination (Join-Path $app 'THIRD_PARTY.md')
Copy-Item -LiteralPath (Join-Path $repo 'gradle\libs.versions.toml') -Destination (Join-Path $app 'build-versions.toml')
# Whitelisted inputs only: no graphs, API keys, tunnel tokens, local properties or caches enter the package.
$manifest = Get-ChildItem -LiteralPath $app -Recurse -File | ForEach-Object {
    [pscustomobject]@{ path = $_.FullName.Substring($app.Length + 1); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $app 'package-manifest.json') -Encoding UTF8
if (!$NoZip) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = Join-Path $Destination 'RNDTransitServer-Windows-x64.zip'
    [IO.Compression.ZipFile]::CreateFromDirectory($app, $zip)
    Write-Host "Portable ZIP: $zip"
}
Write-Host "Launcher: $(Join-Path $app 'RNDTransitServer.exe')"
Write-Host 'Copy the ENTIRE folder, not just the executable. First run: Setup / import.'
