[CmdletBinding()]
param(
    [string]$Endpoint = 'http://127.0.0.1:18088/v1/trips/generate',
    [ValidateSet('walk','bike','bus','metro','train')][string]$Mode = 'walk',
    [ValidateRange(5,360)][int]$Minutes = 30
)
$ErrorActionPreference = 'Stop'
$origin = @{ label='Confirmed test origin'; point=@{latitude=45.50455; longitude=-73.55610} }
$request = @{ origin=$origin; plannedMinutes=$Minutes; modes=@($Mode); selectedRouteIds=@();
    zonesByTransport=@{}; excludedDestinations=@() }
if ($Mode -in @('bus','metro','train')) { $request.zonesByTransport[$Mode] = @('A','B','C','D') }
$destinations = @()
foreach ($number in 1..2) {
    $body = $request | ConvertTo-Json -Depth 15 -Compress
    $trip = Invoke-RestMethod -Method Post -Uri $Endpoint -ContentType 'application/json' -Body $body -TimeoutSec 30
    if ($trip.status -ne 'success') { throw "Trip $number failed: $($trip.message)" }
    if ($trip.origin.point.latitude -ne $origin.point.latitude -or $trip.origin.point.longitude -ne $origin.point.longitude) {
        throw 'The confirmed origin changed.'
    }
    if ($trip.elapsedSeconds -gt $Minutes*60) { throw 'Trip exceeds the time budget.' }
    if (!@($trip.otp.data.planConnection.edges[0].node.legs).Count) { throw 'Trip has no route legs.' }
    $point = $trip.destination.point
    if ($destinations | Where-Object { $_.latitude -eq $point.latitude -and $_.longitude -eq $point.longitude }) {
        throw 'The two generated destinations are identical.'
    }
    $destinations += $point
    $request.excludedDestinations = @($destinations)
    Write-Host "PASS trip $number : $($trip.destination.label), $($trip.elapsedSeconds)s, $($trip.attempts) attempts"
}
