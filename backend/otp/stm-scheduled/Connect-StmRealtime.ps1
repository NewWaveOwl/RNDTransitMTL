param([int]$Port = 18085)
$ErrorActionPreference = 'Stop'
$secureKey = Read-Host 'Paste the STM API Key / Client ID (input hidden)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($key)) { throw 'An API key is required.' }
    $path = Join-Path $PSScriptRoot 'router-config.json'
    $config = [pscustomobject]@{}
    if (Test-Path -LiteralPath $path) {
        $config = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    }
    $updaters = @($config.updaters | Where-Object {
        $_ -and !($_.feedId -eq 'STM' -and $_.type -eq 'stop-time-updater')
    })
    $updaters += @{
        type = 'stop-time-updater'
        feedId = 'STM'
        url = 'https://api.stm.info/pub/od/gtfs-rt/ic/v2/tripUpdates'
        frequency = 'PT60S'
        headers = @{apiKey = $key}
    }
    $config | Add-Member -Force -NotePropertyName updaters -NotePropertyValue $updaters
    [IO.File]::WriteAllText($path, ($config | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
    Write-Host 'Configured STM TripUpdates at a 60-second interval.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $key = $null
    $secureKey.Dispose()
}
& (Join-Path $PSScriptRoot 'Start-StmOtp.ps1') -Port $Port
