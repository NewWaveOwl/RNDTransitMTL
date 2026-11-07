param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$query = Get-Content -LiteralPath 'old-montreal.graphql' -Raw
foreach ($test in @(@{File='walk'; Mode='WALK'}, @{File='bike'; Mode='BICYCLE'})) {
    $name = $test.File
    $variables = Get-Content -LiteralPath "$name-variables.json" -Raw | ConvertFrom-Json
    $body = @{ query=$query; variables=$variables } | ConvertTo-Json -Depth 10
    $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$Port/otp/gtfs/v1" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 15
    $response.Content | Set-Content -LiteralPath "$name-response.json" -Encoding utf8
    $result = $response.Content | ConvertFrom-Json
    $route = $result.data.planConnection.edges | Select-Object -First 1 -ExpandProperty node
    $leg = $route.legs | Where-Object { $_.mode -eq $test.Mode -and $_.distance -gt 0 -and $_.legGeometry.points } | Select-Object -First 1
    if ($result.errors -or !$leg -or $route.duration -le 0) {
        throw "$($test.Mode) failed. See $name-response.json"
    }
    Write-Output "$($test.Mode) passed: $($route.duration) s, $($leg.distance) m, $($leg.legGeometry.length) geometry points"
}
