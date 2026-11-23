[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Java,
    [Parameter(Mandatory)][string]$OtpJar,
    [Parameter(Mandatory)][string]$OsmFile,
    [Parameter(Mandatory)][string]$StmGtfs,
    [string]$ExoGtfs,
    [Parameter(Mandatory)][string]$Destination,
    [ValidateRange(1024,32768)][int]$MemoryMb = 4096
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($file in @($Java, $OtpJar, $OsmFile, $StmGtfs) + @($ExoGtfs | Where-Object { $_ })) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf) -or (Get-Item -LiteralPath $file).Length -eq 0) { throw 'Choose existing nonempty Java, OTP, OSM and GTFS input files.' }
}
$Java = (Resolve-Path -LiteralPath $Java).Path
$OtpJar = (Resolve-Path -LiteralPath $OtpJar).Path
$javaRelease = Join-Path (Split-Path -Parent (Split-Path -Parent $Java)) 'release'
if (!(Test-Path -LiteralPath $javaRelease) -or [IO.File]::ReadAllText($javaRelease) -notmatch '(?m)^JAVA_VERSION="25\.') { throw 'The verified OTP 2.10 setup requires Java 25.' }
$archive = [IO.Compression.ZipFile]::OpenRead($OtpJar)
try {
    $versionEntry = $archive.GetEntry('META-INF/maven/org.opentripplanner/otp-shaded/pom.properties')
    if (!$versionEntry) { throw 'Cannot identify the OTP shaded JAR.' }
    $reader = [IO.StreamReader]::new($versionEntry.Open())
    try { if ($reader.ReadToEnd() -notmatch '(?m)^version=2\.10\.0\r?$') { throw 'This reproducible setup is verified for OTP 2.10.0. Revalidate another version separately.' } } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
$Destination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $Destination) { throw 'Choose a new destination. Existing graphs, data and server installations are never replaced.' }
New-Item -ItemType Directory -Path $Destination | Out-Null
$sources = @(
    @{ Source = $OsmFile; Name = 'region.osm.pbf' },
    @{ Source = $StmGtfs; Name = 'stm-gtfs.zip' }
)
if ($ExoGtfs) { $sources += @{ Source = $ExoGtfs; Name = 'exo-trains-gtfs.zip' } }
$inputs = @()
foreach ($source in $sources) {
    Copy-Item -LiteralPath $source.Source -Destination (Join-Path $Destination $source.Name)
    $copied = Get-Item -LiteralPath (Join-Path $Destination $source.Name)
    $inputs += [pscustomobject]@{ file = $source.Name; size = $copied.Length; sha256 = (Get-FileHash -LiteralPath $copied.FullName -Algorithm SHA256).Hash }
}
$feeds = @(@{ type = 'gtfs'; feedId = 'STM'; source = 'stm-gtfs.zip' })
if ($ExoGtfs) { $feeds += @{ type = 'gtfs'; feedId = 'EXO_TRAINS'; source = 'exo-trains-gtfs.zip' } }
$configuration = @{ transitModelTimeZone = 'America/Toronto'; transitFeeds = $feeds } | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $Destination 'build-config.json'), $configuration, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $Destination 'router-config.json'), '{}', [Text.UTF8Encoding]::new($false))
Write-Host 'Building a separate scheduled-only graph. Original source data, credentials and existing server are unchanged.'
& $Java "-Xmx$($MemoryMb)M" -jar $OtpJar --build --save $Destination
if ($LASTEXITCODE -ne 0) { throw 'OTP graph build failed. The incomplete new directory is retained for inspection; keep using your previous graph.' }
$graph = Join-Path $Destination 'graph.obj'
if (!(Test-Path -LiteralPath $graph -PathType Leaf)) { throw 'OTP did not create graph.obj. Do not treat this directory as a prepared graph.' }
[ordered]@{
    schemaVersion = 1
    createdUtc = [DateTime]::UtcNow.ToString('o')
    otpVersion = '2.10.0'
    javaMajorVersion = 25
    timeZone = 'America/Toronto'
    otpJarSha256 = (Get-FileHash -LiteralPath $OtpJar -Algorithm SHA256).Hash
    inputs = $inputs
    graphSha256 = (Get-FileHash -LiteralPath $graph -Algorithm SHA256).Hash
    attribution = 'OpenStreetMap contributors; STM and exo GTFS data publishers. Retain the source data licenses.'
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $Destination 'routing-build-manifest.json') -Encoding UTF8
Write-Host "Separate graph ready: $Destination"
Write-Host 'Test real mode coverage with Test-TravelTrackerRouting.ps1 before importing or deploying this graph.'
