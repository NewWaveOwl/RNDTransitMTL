param(
    [Parameter(Mandatory=$true)][uri]$TripUpdatesUrl,
    [Parameter(Mandatory=$true)][uri]$ServiceAlertsUrl,
    [Parameter(Mandatory=$true)][ValidateRange(1,86400)][int]$TripUpdatesPollSeconds,
    [Parameter(Mandatory=$true)][ValidateRange(1,86400)][int]$ServiceAlertsPollSeconds,
    [hashtable]$TripUpdatesHeaders = @{},
    [hashtable]$ServiceAlertsHeaders = @{}
)
$ErrorActionPreference = 'Stop'
foreach ($url in @($TripUpdatesUrl, $ServiceAlertsUrl)) {
    if ($url.Scheme -ne 'https') { throw 'STM feeds require an HTTPS URL.' }
}
$path = Join-Path $PSScriptRoot 'router-config.json'
$config = @{updaters=@()}
if (Test-Path -LiteralPath $path) {
    $config = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    $otherUpdaters = @($config.updaters | Where-Object {
        !($_.feedId -eq 'STM' -and $_.type -in @('stop-time-updater','real-time-alerts'))
    })
} else {
    $otherUpdaters = @()
}
$updates = @(
    @{
        type='stop-time-updater'
        feedId='STM'
        url=$TripUpdatesUrl.AbsoluteUri
        frequency="PT${TripUpdatesPollSeconds}S"
        headers=$TripUpdatesHeaders
    },
    @{
        type='real-time-alerts'
        feedId='STM'
        url=$ServiceAlertsUrl.AbsoluteUri
        frequency="PT${ServiceAlertsPollSeconds}S"
        headers=$ServiceAlertsHeaders
    }
)
if ($config -is [hashtable]) {
    $config.updaters = $otherUpdaters + $updates
} else {
    $config | Add-Member -Force -NotePropertyName updaters -NotePropertyValue ($otherUpdaters + $updates)
}
[IO.File]::WriteAllText($path, ($config | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
Write-Host 'Saved STM updater configuration. Restart OTP to activate it.'
