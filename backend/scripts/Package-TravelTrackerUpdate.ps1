[CmdletBinding()]
param(
    [string]$Destination,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$backend = Join-Path $repo 'backend'
if (!$Destination) { $Destination = Join-Path $backend ('launcher\build\updates\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
$Destination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists. Choose a new folder; no existing package is replaced.' }
if (!$SkipBuild) {
    Push-Location -LiteralPath $repo
    try {
        & .\gradlew.bat -p backend test :launcher:test installDist :launcher:installDist --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Backend/launcher checks failed; no update package was created.' }
    } finally { Pop-Location }
}
$groups = @(
    @{ Source = Join-Path $backend 'launcher\build\install\launcher\lib'; Target = 'app' },
    @{ Source = Join-Path $backend 'build\install\backend\lib'; Target = 'services/backend/lib' }
)
foreach ($group in $groups) {
    if (!(Test-Path -LiteralPath $group.Source -PathType Container)) { throw 'Run backend and launcher installDist before packaging.' }
    if (@(Get-ChildItem -LiteralPath $group.Source -File -Filter '*.jar').Count -eq 0) { throw 'A verified installDist library directory is empty.' }
}
$staged = Join-Path $Destination 'update'
New-Item -ItemType Directory -Path $staged -Force | Out-Null
$entries = @()
foreach ($group in $groups) {
    $target = Join-Path (Join-Path $staged 'payload') $group.Target
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    foreach ($jar in Get-ChildItem -LiteralPath $group.Source -File -Filter '*.jar' | Sort-Object Name) {
        Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $target $jar.Name)
        $entries += [pscustomobject]@{
            path = $group.Target + '/' + $jar.Name
            sha256 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
            size = $jar.Length
        }
    }
}
$manifest = [ordered]@{
    schemaVersion = 1
    createdUtc = [DateTime]::UtcNow.ToString('o')
    description = 'TravelTracker shared routing, Public Nominatim and secondary control window'
    compatibility = 'Existing portable Windows package with identical launcher/backend JAR filenames; runtimes and graph unchanged'
    files = $entries
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $staged 'update-manifest.json') -Encoding UTF8
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Apply-TravelTrackerUpdate.ps1') -Destination $staged
Copy-Item -LiteralPath (Join-Path $backend 'launcher\SERVER_GUIDE.md') -Destination $staged
@'
TravelTracker update for an existing RNDTransitServer Windows portable package

Stop all shared services and EXIT RNDTransitServer, including its tray instance.
Back up your external LOCALAPPDATA\RNDTransitServer settings and retain the full old
portable application folder. Extract this ZIP to a separate directory.

From the extracted update directory, inspect the target without changing files:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-TravelTrackerUpdate.ps1 -ServerDirectory "D:\Server\RNDTransitServer" -UpdateDirectory "." -CheckOnly

Then apply to the SAME stopped application folder:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-TravelTrackerUpdate.ps1 -ServerDirectory "D:\Server\RNDTransitServer" -UpdateDirectory "."

The script validates exact JAR compatibility/checksums, creates a new sibling backup
and replaces only launcher/backend JARs. It does not touch your EXE, bundled Java,
OTP, bridge, graph, credentials, LOCALAPPDATA settings or Microsoft CLI sign-in.
If dependency filenames differ, use a new complete portable package instead.

For rollback while stopped:
  powershell -NoProfile -File .\Apply-TravelTrackerUpdate.ps1 -ServerDirectory "D:\Server\RNDTransitServer" -RollbackDirectory "D:\Server\RNDTransitServer-traveltracker-backup-TIMESTAMP"

Reopen the existing EXE and select TravelTracker window. See SERVER_GUIDE.md for
TravelTracker Auth identifiers and Services:Routing Dev Tunnel URL configuration.
'@ | Set-Content -LiteralPath (Join-Path $staged 'README.txt') -Encoding UTF8
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $Destination 'RNDTransitServer-TravelTracker-Update.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($staged, $zip)
Write-Host "Update ZIP: $zip"
Write-Host "SHA256: $((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash)"
Write-Host 'Apply only to a stopped existing portable app after reviewing README.txt. No server was modified.'
