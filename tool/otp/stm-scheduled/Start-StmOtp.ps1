param([int]$Port = 18084, [switch]$Rebuild, [string]$LogPath)
$ErrorActionPreference = 'Stop'
if (!$LogPath) { $LogPath = Join-Path $PSScriptRoot "stm-otp-$Port.log" }
Set-Location -LiteralPath $PSScriptRoot
$root = Split-Path -Parent $PSScriptRoot
$zip = Join-Path $PSScriptRoot 'stm-gtfs.zip'
$url = 'https://www.stm.info/getContentAsset/38dcb679-4237-4c02-b6f4-5f8434665e5c/dfc3d011-8f63-43f6-9ed8-4b444333a1d0/GTFS_STM.zip?language=fr'

if (!(Test-Path -LiteralPath $zip)) {
    curl.exe -fL --retry 2 --connect-timeout 15 --max-time 180 --output "$zip.part" $url
    if ($LASTEXITCODE -ne 0) { throw 'STM GTFS download failed.' }
    Move-Item -LiteralPath "$zip.part" -Destination $zip
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    foreach ($entry in 'stops.txt','routes.txt','trips.txt','stop_times.txt') {
        if (!$archive.GetEntry($entry)) { throw "STM GTFS is missing $entry" }
    }
} finally {
    $archive.Dispose()
}
@{
    source = $url
    fileLastModifiedUtc = (Get-Item -LiteralPath $zip).LastWriteTimeUtc.ToString('o')
    sha256 = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash
} | ConvertTo-Json | Set-Content -LiteralPath 'stm-feed-source.json' -Encoding ascii

$java = Get-ChildItem -LiteralPath (Join-Path $root 'java25') -Recurse -Filter java.exe -File |
    Select-Object -First 1 -ExpandProperty FullName
if (!$java) { throw 'Java 25 executable was not found.' }
$ErrorActionPreference = 'Continue'
$jar = Join-Path $root 'otp.jar'
if ($Rebuild -or !(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'graph.obj'))) {
    & $java -Xmx4G -jar $jar --build --save . 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath 'stm-build.log'
    if ($LASTEXITCODE -ne 0) { throw "OTP build exited with code $LASTEXITCODE." }
}
# OTP startup can echo configuration. Redact header values before writing logs.
$headerSecrets = @()
$routerPath = Join-Path $PSScriptRoot 'router-config.json'
if (Test-Path -LiteralPath $routerPath) {
    $router = Get-Content -LiteralPath $routerPath -Raw | ConvertFrom-Json
    foreach ($updater in $router.updaters) {
        if ($updater.headers) {
            $headerSecrets += @($updater.headers.PSObject.Properties | ForEach-Object { [string]$_.Value } |
                Where-Object { $_.Length -ge 4 })
        }
    }
}
& $java -Xmx4G -jar $jar --load --serve --bindAddress 127.0.0.1 --port $Port . 2>&1 | ForEach-Object {
    $line = $_.ToString()
    foreach ($secret in $headerSecrets) { $line = $line.Replace($secret, '[redacted]') }
    $line
} | Tee-Object -FilePath $LogPath
if ($LASTEXITCODE -ne 0) { throw "OTP exited with code $LASTEXITCODE." }