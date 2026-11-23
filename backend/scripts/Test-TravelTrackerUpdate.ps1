[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$fixture = Join-Path $repo ('backend\launcher\build\update-checks\' + [Guid]::NewGuid().ToString('N'))
$server = Join-Path $fixture 'RNDTransitServer'
$update = Join-Path $fixture 'update'
$payload = Join-Path $update 'payload'
$helper = Join-Path $PSScriptRoot 'Apply-TravelTrackerUpdate.ps1'
$paths = @('app/launcher.jar', 'services/backend/lib/backend.jar')

function Write-Fixture([string]$File, [string]$Contents) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $File) -Force | Out-Null
    Set-Content -LiteralPath $File -Value $Contents -Encoding UTF8
}
function Assert-Value([bool]$Condition, [string]$Message) { if (!$Condition) { throw $Message } }
function Assert-Rejected([scriptblock]$Action) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    Assert-Value $rejected 'An unsafe or incompatible update was accepted.'
}
foreach ($required in @('RNDTransitServer.exe', 'runtime/bin/java.exe', 'services/otp-runtime/bin/java.exe', 'app/RNDTransitServer.cfg')) {
    Write-Fixture (Join-Path $server $required) 'preserve-existing-runtime-or-configuration'
}
$entries = @()
foreach ($relative in $paths) {
    Write-Fixture (Join-Path $server $relative) ('old-' + $relative)
    $file = Join-Path $payload $relative
    Write-Fixture $file ('new-' + $relative)
    $entries += [pscustomobject]@{ path = $relative; sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash; size = (Get-Item -LiteralPath $file).Length }
}
$manifestPath = Join-Path $update 'update-manifest.json'
$manifest = [ordered]@{ schemaVersion = 1; files = $entries }
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
$runtimeHash = (Get-FileHash -LiteralPath (Join-Path $server 'runtime/bin/java.exe') -Algorithm SHA256).Hash

& $helper -ServerDirectory $server -UpdateDirectory $update -CheckOnly
Assert-Value ((Get-Content -LiteralPath (Join-Path $server $paths[0]) -Raw).Trim() -eq ('old-' + $paths[0])) 'CheckOnly changed a target file.'
Assert-Value (@(Get-ChildItem -LiteralPath $fixture -Directory -Filter '*-traveltracker-backup-*').Count -eq 0) 'CheckOnly created a backup or wrote state.'
Write-Output 'PASS: dry-check verifies without target writes'

$standaloneHelper = Join-Path $update 'Apply-TravelTrackerUpdate.ps1'
Copy-Item -LiteralPath $helper -Destination $standaloneHelper
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $standaloneHelper -ServerDirectory $server -CheckOnly
Assert-Value ($LASTEXITCODE -eq 0) 'Windows PowerShell -File failed to resolve its default update directory.'
Assert-Value ((Get-Content -LiteralPath (Join-Path $server $paths[0]) -Raw).Trim() -eq ('old-' + $paths[0])) 'Standalone CheckOnly changed a target file.'
Assert-Value (@(Get-ChildItem -LiteralPath $fixture -Directory -Filter '*-traveltracker-backup-*').Count -eq 0) 'Standalone CheckOnly created a backup.'
Write-Output 'PASS: Windows PowerShell -File resolves an omitted update directory without writes'

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $standaloneHelper -ServerDirectory $server
Assert-Value ($LASTEXITCODE -eq 0) 'Standalone Windows PowerShell apply failed.'
Assert-Value ((Get-Content -LiteralPath (Join-Path $server $paths[0]) -Raw).Trim() -eq ('new-' + $paths[0])) 'Launcher JAR was not updated.'
Assert-Value ((Get-Content -LiteralPath (Join-Path $server $paths[1]) -Raw).Trim() -eq ('new-' + $paths[1])) 'Backend JAR was not updated.'
Assert-Value ((Get-FileHash -LiteralPath (Join-Path $server 'runtime/bin/java.exe') -Algorithm SHA256).Hash -eq $runtimeHash) 'A bundled runtime changed.'
Write-Output 'PASS: apply replaces only expected JARs and preserves runtime'

$backup = @(Get-ChildItem -LiteralPath $fixture -Directory -Filter '*-traveltracker-backup-*')
Assert-Value ($backup.Count -eq 1) 'Expected one unique backup.'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $standaloneHelper -ServerDirectory $server -RollbackDirectory $backup[0].FullName
Assert-Value ($LASTEXITCODE -eq 0) 'Standalone Windows PowerShell rollback failed.'
Assert-Value ((Get-Content -LiteralPath (Join-Path $server $paths[0]) -Raw).Trim() -eq ('old-' + $paths[0])) 'Rollback did not restore the launcher.'
Assert-Value ((Get-Content -LiteralPath (Join-Path $server $paths[1]) -Raw).Trim() -eq ('old-' + $paths[1])) 'Rollback did not restore the backend.'
Write-Output 'PASS: verified backup restores both original JARs'

Write-Fixture (Join-Path $payload $paths[0]) 'corrupted-payload'
Assert-Rejected { & $helper -ServerDirectory $server -UpdateDirectory $update -CheckOnly }
Write-Fixture (Join-Path $payload $paths[0]) ('new-' + $paths[0])
Write-Output 'PASS: corrupted payload rejected'

$manifest.files = @($entries[0], $entries[0])
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Assert-Rejected { & $helper -ServerDirectory $server -UpdateDirectory $update -CheckOnly }
Write-Output 'PASS: duplicate paths rejected'

$manifest.files = @([pscustomobject]@{ path = '../../outside.jar'; sha256 = $entries[0].sha256; size = $entries[0].size })
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Assert-Rejected { & $helper -ServerDirectory $server -UpdateDirectory $update -CheckOnly }
Write-Output 'PASS: path traversal rejected'

$manifest.files = $entries
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Write-Fixture (Join-Path $server 'app/unexpected-version.jar') 'an-incompatible-dependency'
Assert-Rejected { & $helper -ServerDirectory $server -UpdateDirectory $update -CheckOnly }
Write-Output 'PASS: incompatible dependency set rejected'
Write-Output "8 update checks passed. Isolated fixture retained for inspection: $fixture"
