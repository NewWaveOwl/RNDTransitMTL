[CmdletBinding()]
param(
    [string]$OtpEndpoint,
    [string]$CatalogueEndpoint,
    [string]$StackDirectory = (Join-Path $PSScriptRoot 'stm-scheduled'),
    [ValidateRange(1024,65535)][int]$Port = 18088
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (!$OtpEndpoint) {
    $stateFile = Join-Path $StackDirectory 'stm-stack-processes.json'
    if (!(Test-Path -LiteralPath $stateFile)) {
        throw 'Start the combined STM/exo OTP stack first, or provide -OtpEndpoint and -CatalogueEndpoint.'
    }
    $stack = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $OtpEndpoint = "http://127.0.0.1:$($stack.adapterPort)/otp/gtfs/v1"
    if (!$CatalogueEndpoint) { $CatalogueEndpoint = "http://127.0.0.1:$($stack.otpPort)/otp/gtfs/v1" }
}
if (!$CatalogueEndpoint) { $CatalogueEndpoint = $OtpEndpoint }
$previousOtp = $env:OTP_URL
$previousCatalogue = $env:OTP_CATALOGUE_URL
$previousPort = $env:PORT
try {
    $env:OTP_URL = $OtpEndpoint
    $env:OTP_CATALOGUE_URL = $CatalogueEndpoint
    $env:PORT = "$Port"
    Write-Host "Planning via $OtpEndpoint; catalogue via $CatalogueEndpoint"
    Push-Location -LiteralPath $repo
    try { & .\gradlew.bat :backend:run --console=plain } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw 'Trip backend stopped. Read the startup error above.' }
} finally {
    $env:OTP_URL = $previousOtp
    $env:OTP_CATALOGUE_URL = $previousCatalogue
    $env:PORT = $previousPort
}
