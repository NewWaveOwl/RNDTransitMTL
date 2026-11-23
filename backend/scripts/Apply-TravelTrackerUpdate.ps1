[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ServerDirectory,
    [ValidateNotNullOrEmpty()][string]$UpdateDirectory,
    [string]$RollbackDirectory,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'

# Windows PowerShell -File can bind parameter defaults before PSScriptRoot is set.
# Resolve the omitted default in the script body, where its file location is known.
if (!$PSBoundParameters.ContainsKey('UpdateDirectory')) { $UpdateDirectory = $PSScriptRoot }

function Resolve-Directory([string]$Path) {
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    if (!$item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Use an existing real directory, not a symbolic link or junction.'
    }
    return [IO.Path]::GetFullPath($item.FullName).TrimEnd('\', '/')
}

function Resolve-JarPath([string]$Root, [string]$Relative) {
    if ($Relative -notmatch '^(app|services/backend/lib)/[A-Za-z0-9][A-Za-z0-9._-]*\.jar$') {
        throw 'Manifest contains an unsupported path. Only known launcher/backend JARs may be replaced.'
    }
    $absolute = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (!$absolute.StartsWith($Root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Manifest path escapes the selected directory.'
    }
    $cursor = Get-Item -LiteralPath $absolute -ErrorAction Stop
    if ($cursor -is [IO.DirectoryInfo]) { throw 'Expected a JAR file, not a directory.' }
    while ($cursor.FullName -ne $Root) {
        if ($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Update paths must not contain symbolic links or junctions.' }
        $cursor = if ($cursor -is [IO.DirectoryInfo]) { $cursor.Parent } else { $cursor.Directory }
        if ($null -eq $cursor) { throw 'Cannot verify update path ownership.' }
    }
    return $absolute
}

function Assert-Stopped([string]$Root) {
    $executables = @(
        (Join-Path $Root 'RNDTransitServer.exe'),
        (Join-Path $Root 'runtime\bin\java.exe'),
        (Join-Path $Root 'services\otp-runtime\bin\java.exe')
    )
    foreach ($process in Get-Process -ErrorAction Stop) {
        if ($process.ProcessName -eq 'RNDTransitServer') {
            throw 'Exit all RNDTransitServer instances, including the tray instance, before applying or rolling back.'
        }
        $image = $null
        try { $image = $process.Path } catch { }
        if ($image -and $executables -contains $image) {
            throw 'Stop the shared server and exit RNDTransitServer (including its tray instance) before applying or rolling back.'
        }
    }
}

function Assert-Hash([string]$File, $Entry) {
    if ($Entry.sha256 -notmatch '^[A-Fa-f0-9]{64}$' -or $Entry.size -lt 1 -or
        (Get-Item -LiteralPath $File).Length -ne $Entry.size -or
        (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ne $Entry.sha256) {
        throw 'Payload/backup checksum verification failed. No unverified file will be installed.'
    }
}

$server = Resolve-Directory $ServerDirectory
foreach ($required in @('RNDTransitServer.exe', 'runtime\bin\java.exe', 'services\otp-runtime\bin\java.exe', 'app\RNDTransitServer.cfg')) {
    if (!(Test-Path -LiteralPath (Join-Path $server $required) -PathType Leaf)) {
        throw 'Selected folder is not a complete existing RNDTransitServer portable application.'
    }
}
Assert-Stopped $server

if ($RollbackDirectory) {
    $backup = Resolve-Directory $RollbackDirectory
    $rollback = Get-Content -LiteralPath (Join-Path $backup 'rollback-manifest.json') -Raw | ConvertFrom-Json
    if ($rollback.schemaVersion -ne 1 -or $rollback.serverDirectory -ne $server -or @($rollback.files).Count -eq 0) {
        throw 'Rollback manifest does not belong to this application folder.'
    }
    $verified = @()
    foreach ($entry in $rollback.files) {
        $saved = Resolve-JarPath (Join-Path $backup 'payload') $entry.path
        $target = Resolve-JarPath $server $entry.path
        Assert-Hash $saved $entry
        $verified += @{ Source = $saved; Target = $target }
    }
    if ($CheckOnly) { Write-Host "Rollback check passed for $($verified.Count) JARs. No files changed."; return }
    foreach ($file in $verified) { Copy-Item -LiteralPath $file.Source -Destination $file.Target -Force }
    Write-Host "Restored $($verified.Count) JARs. External configuration, graph and runtimes were preserved."
    return
}

$update = Resolve-Directory $UpdateDirectory
$manifest = Get-Content -LiteralPath (Join-Path $update 'update-manifest.json') -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or @($manifest.files).Count -eq 0) { throw 'Unsupported or empty update manifest.' }
$payload = Resolve-Directory (Join-Path $update 'payload')
$paths = @($manifest.files | ForEach-Object { $_.path })
if (@($paths | Sort-Object -Unique).Count -ne $paths.Count) { throw 'Duplicate JAR paths in the update manifest.' }
$verified = @()
foreach ($entry in $manifest.files) {
    $source = Resolve-JarPath $payload $entry.path
    $target = Resolve-JarPath $server $entry.path
    Assert-Hash $source $entry
    $verified += @{ Source = $source; Target = $target; Relative = $entry.path }
}
foreach ($group in @('app', 'services/backend/lib')) {
    $expected = @($paths | Where-Object { $_.StartsWith($group + '/', [StringComparison]::Ordinal) } | Sort-Object)
    $actual = @(Get-ChildItem -LiteralPath (Join-Path $server $group) -File -Filter '*.jar' | ForEach-Object { $group + '/' + $_.Name } | Sort-Object)
    if ($expected.Count -eq 0 -or (Compare-Object $expected $actual)) {
        throw 'Existing dependency JAR filenames differ. Use a complete new portable package instead of this update.'
    }
}
if ($CheckOnly) { Write-Host "Update check passed for $($verified.Count) compatible, verified JARs. No files changed."; return }

$parent = Split-Path -Parent $server
$name = Split-Path -Leaf $server
$backup = Join-Path $parent ($name + '-traveltracker-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
if (Test-Path -LiteralPath $backup) { throw 'Backup destination already exists; no files changed.' }
New-Item -ItemType Directory -Path $backup | Out-Null
$originals = @()
foreach ($file in $verified) {
    $saved = Join-Path (Join-Path $backup 'payload') $file.Relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
    Copy-Item -LiteralPath $file.Target -Destination $saved
    $entry = [pscustomobject]@{ path = $file.Relative; sha256 = (Get-FileHash -LiteralPath $file.Target -Algorithm SHA256).Hash; size = (Get-Item -LiteralPath $file.Target).Length }
    Assert-Hash $saved $entry
    $originals += $entry
}
[ordered]@{ schemaVersion = 1; serverDirectory = $server; createdUtc = [DateTime]::UtcNow.ToString('o'); files = $originals } |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backup 'rollback-manifest.json') -Encoding UTF8
Write-Host "Verified backup: $backup"
try {
    foreach ($file in $verified) { Copy-Item -LiteralPath $file.Source -Destination $file.Target -Force }
    foreach ($entry in $manifest.files) { Assert-Hash (Resolve-JarPath $server $entry.path) $entry }
} catch {
    $failure = $_
    foreach ($entry in $originals) {
        Copy-Item -LiteralPath (Resolve-JarPath (Join-Path $backup 'payload') $entry.path) -Destination (Resolve-JarPath $server $entry.path) -Force
    }
    throw "Update failed; original JARs were restored. Backup: $backup. $failure"
}
Write-Host "Updated $($verified.Count) JARs. Start the existing EXE and open TravelTracker window."
Write-Host 'No EXE, runtime, OTP graph, bridge, credentials or external settings were changed.'
Write-Host "Rollback while stopped: .\Apply-TravelTrackerUpdate.ps1 -ServerDirectory '$server' -RollbackDirectory '$backup'"
