[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourceDirectory,
    [Parameter(Mandatory)][string]$Destination,
    [Parameter(Mandatory)][hashtable]$BusFeeds,
    [Parameter(Mandatory)][string]$OsmFile,
    [string]$Java = (Join-Path $PSScriptRoot 'services\otp-runtime\bin\java.exe'),
    [string]$OtpJar = (Join-Path $PSScriptRoot 'services\otp\otp.jar'),
    [ValidateRange(1024,65536)][int]$MemoryMb = 4096,
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
$target = [IO.Path]::GetFullPath($Destination)
$osm = (Resolve-Path -LiteralPath $OsmFile).Path
if ($source.TrimEnd('\') -eq $target.TrimEnd('\')) { throw 'Choose a separate new destination; the running graph is preserved.' }
if (Test-Path -LiteralPath $target) { throw 'Destination already exists. Choose a new directory; this script never overwrites a graph.' }
if (!$BusFeeds.Count) { throw 'Supply at least one Chrono agency code and its matching GTFS ZIP.' }
$sectors = @{}
$catalogFile = Join-Path $source 'exo-buses.json'
if (Test-Path -LiteralPath $catalogFile) {
    try { $oldCatalog = Get-Content -LiteralPath $catalogFile -Raw | ConvertFrom-Json }
    catch { throw 'Invalid existing exo bus graph manifest.' }
    foreach ($agency in $oldCatalog.agencies) { $sectors[[string]$agency] = Join-Path $source "exo-bus-$agency-gtfs.zip" }
}
foreach ($agency in $BusFeeds.Keys) { $sectors[[string]$agency] = [string]$BusFeeds[$agency] }
if ($sectors.Count -gt 16) { throw 'At most 16 bus sectors are supported.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
function File-Sha256([string]$file) {
    $inputStream = [IO.File]::OpenRead($file)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($inputStream)).Replace('-','').ToLowerInvariant() }
    finally { $inputStream.Dispose(); $algorithm.Dispose() }
}
function Check-Gtfs([string]$file, [switch]$Bus) {
    $archive = [IO.Compression.ZipFile]::OpenRead($file)
    try {
        foreach ($name in @('agency.txt','routes.txt','trips.txt','stops.txt','stop_times.txt')) {
            if (!$archive.GetEntry($name)) { throw "Missing GTFS file $name" }
        }
        if ($Bus) {
            $reader = [IO.StreamReader]::new($archive.GetEntry('routes.txt').Open())
            try { $routes = @($reader.ReadToEnd() | ConvertFrom-Csv) } finally { $reader.Dispose() }
            if (!$routes.Count -or @($routes | Where-Object { [string]$_.route_type -ne '3' }).Count) {
                throw 'Expected a bus-sector archive containing route_type 3.'
            }
        }
    } finally { $archive.Dispose() }
}
foreach ($agency in @($sectors.Keys)) {
    if ($agency -cnotmatch '^[A-Z][A-Z0-9_]{1,31}$' -or $agency -eq 'TRAINS') { throw 'Use the exact uppercase Chrono bus agency code (letters, digits, underscores).' }
    $sectors[$agency] = (Resolve-Path -LiteralPath $sectors[$agency]).Path
    Check-Gtfs $sectors[$agency] -Bus
}
Check-Gtfs (Join-Path $source 'stm-gtfs.zip')
foreach ($name in @('stm-adapter-index.json')) { if (!(Test-Path -LiteralPath (Join-Path $source $name) -PathType Leaf)) { throw "Source is missing $name" } }
$stmCredentials = Join-Path $source 'stm-upstream.json'
if (!(Test-Path -LiteralPath $stmCredentials)) { $stmCredentials = Join-Path $source 'router-config.json' }
if (!(Test-Path -LiteralPath $stmCredentials)) { throw 'Source is missing saved STM feed configuration.' }
if (!$PrepareOnly) {
    $Java = (Resolve-Path -LiteralPath $Java).Path
    $OtpJar = (Resolve-Path -LiteralPath $OtpJar).Path
}
New-Item -ItemType Directory -Path $target | Out-Null
foreach ($name in @('stm-gtfs.zip','stm-adapter-index.json','exo-trains-gtfs.zip','exo-realtime.local.json','exo-bus-realtime.local.json')) {
    $inputFile = Join-Path $source $name
    if (Test-Path -LiteralPath $inputFile -PathType Leaf) { Copy-Item -LiteralPath $inputFile -Destination (Join-Path $target $name) }
}
Copy-Item -LiteralPath $stmCredentials -Destination (Join-Path $target 'stm-upstream.json')
Copy-Item -LiteralPath $osm -Destination (Join-Path $target 'map.osm.pbf')
$baseFile = Join-Path $source 'build-config.json'
if (Test-Path -LiteralPath $baseFile) {
    try { $build = Get-Content -LiteralPath $baseFile -Raw | ConvertFrom-Json }
    catch { throw 'Invalid source build-config.json; the original graph is unchanged.' }
} else { $build = [pscustomobject]@{transitModelTimeZone='America/Toronto'} }
# Deliberately select only archives copied into the new build directory.
$feeds = @(@{type='gtfs'; feedId='STM'; source='stm-gtfs.zip'})
if (Test-Path -LiteralPath (Join-Path $target 'exo-trains-gtfs.zip')) { $feeds += @{type='gtfs'; feedId='EXO_TRAINS'; source='exo-trains-gtfs.zip'} }
$hashes = @{}
$agencies = @($sectors.Keys | Sort-Object)
foreach ($agency in $agencies) {
    $name = "exo-bus-$agency-gtfs.zip"
    Copy-Item -LiteralPath $sectors[$agency] -Destination (Join-Path $target $name)
    $hashes[$agency] = File-Sha256 (Join-Path $target $name)
    $feeds += @{type='gtfs'; feedId="EXO_BUS_$agency"; source=$name}
}
$build | Add-Member -Force -NotePropertyName transitFeeds -NotePropertyValue $feeds
# A source osm[] setting must not refer back to the original directory.
$build | Add-Member -Force -NotePropertyName osm -NotePropertyValue @(@{source='map.osm.pbf'})
$build | Add-Member -Force -NotePropertyName graph -NotePropertyValue 'graph.obj'
$build | Add-Member -Force -NotePropertyName streetGraph -NotePropertyValue 'streetGraph.obj'
$build | Add-Member -Force -NotePropertyName buildReportDir -NotePropertyValue 'build-report/'
$build | Add-Member -Force -NotePropertyName cache -NotePropertyValue @{enabled=$false}
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $target 'build-config.json'), ($build | ConvertTo-Json -Depth 30), $utf8)
if ($PrepareOnly) {
    Write-Host "Prepared inputs only (no graph or import marker): $target"
    return
}
Push-Location -LiteralPath $target
try {
    $ErrorActionPreference = 'Continue'
    & $Java "-Xmx${MemoryMb}M" -jar $OtpJar --build --save . 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath 'graph-build.log'
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($buildExit -ne 0 -or !(Test-Path -LiteralPath 'graph.obj' -PathType Leaf)) { throw 'Graph build failed. Keep the original data directory; inspect graph-build.log and retry in a new directory.' }
    $manifest = @{agencies=$agencies; graphSha256=(File-Sha256 (Join-Path $target 'graph.obj')); scheduleSha256=$hashes}
    [IO.File]::WriteAllText((Join-Path $target 'exo-buses.json'), ($manifest | ConvertTo-Json -Depth 10), $utf8)
    [IO.File]::WriteAllText((Join-Path $target '.rndtransit-data'), "Prepared combined graph; contains private feed credentials. Do not distribute.`n", $utf8)
} finally { Pop-Location }
Write-Host "Combined bus graph ready: $target"
Write-Host 'Stop the launcher, select this directory in Configuration, then use exo bus settings to enable the imported sectors.'
