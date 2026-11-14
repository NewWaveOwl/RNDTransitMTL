param([Parameter(Mandatory)][ValidateRange(30,3600)][int]$PollSeconds)
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot 'exo-realtime.local.json'
$secure = Read-Host 'Chrono train subscription key' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim()
    if (!$token) { throw 'The subscription key cannot be empty.' }
    $encoded = [Uri]::EscapeDataString($token)
    foreach ($kind in 'tripupdate', 'alert') {
        $url = "https://exo.chrono-saeiv.com/api/opendata/v1/TRAINS/${kind}?token=$encoded"
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 15 -MaximumRedirection 0
            if ($response.StatusCode -ne 200) { throw 'Unexpected response.' }
        } catch {
            $status = $_.Exception.Response.StatusCode
            if ($status) { throw "Chrono rejected the $kind request (HTTP $([int]$status))." }
            throw "Could not verify Chrono $kind access."
        }
    }
    $settings = @{token=$token; pollSeconds=$PollSeconds} | ConvertTo-Json
    [IO.File]::WriteAllText($configPath, $settings, [Text.UTF8Encoding]::new($false))
    Write-Host 'Train feed access verified. Restart Start-StmRealtimeStack.ps1 to enable the local exo updaters.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
