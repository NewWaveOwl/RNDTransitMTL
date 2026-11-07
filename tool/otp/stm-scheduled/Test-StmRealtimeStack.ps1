param([int]$Port = 0, [int]$OtpPort = 0, [switch]$InstallAndroid)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'Stop-StmManagedProcess.ps1')
if (!$Port -or !$OtpPort) {
    $stack = Get-Content (Join-Path $PSScriptRoot 'stm-stack-processes.json') -Raw | ConvertFrom-Json
    if (!$Port) { $Port = $stack.adapterPort }
    if (!$OtpPort) { $OtpPort = $stack.otpPort }
}
$base = "http://127.0.0.1:$Port"
$evidence = Join-Path $PSScriptRoot ('realtime-tests-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $evidence | Out-Null
$query = @'
query StmTestTrip($departure:OffsetDateTime!, $excludeRealtime:Boolean!) {
  planConnection(
    origin:{location:{stopLocation:{stopLocationId:"STM:52511"}}}
    destination:{location:{stopLocation:{stopLocationId:"STM:51971"}}}
    dateTime:{earliestDeparture:$departure}
    modes:{transitOnly:true,transit:{transit:[{mode:BUS}]}}
    preferences:{transit:{timetable:{excludeRealTimeUpdates:$excludeRealtime}}}
    first:1
  ){
    routingErrors{code description}
    edges{node{duration legs{
      mode realTime route{shortName}
      start{scheduledTime estimated{time delay}}
      end{scheduledTime estimated{time delay}}
      alerts{alertHash alertHeaderText alertDescriptionText activityPeriods{start end}}
      trip{pattern{code alerts{alertHash alertHeaderText alertDescriptionText activityPeriods{start end}}}}
      legGeometry{length points}
    }}}
  }
}
'@
function Set-TestMode($mode) {
    Invoke-RestMethod -Uri "$base/test-mode" -Method Post -ContentType text/plain -Body $mode -TimeoutSec 5 | Out-Null
}
function Save-Evidence($name, $data) {
    $data | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath (Join-Path $evidence "$name.json") -Encoding utf8
}
function Wait-Downloaded {
    param([long]$AfterMillis = 0)
    Write-Host 'Waiting for successful STM source downloads (freshness is checked separately)...'
    $lastState = ''
    $deadline = (Get-Date).AddSeconds(100)
    do {
        $status = Invoke-RestMethod "$base/status" -TimeoutSec 5
        $stateLabel = 'Predictions=' + $status.tripUpdates.state + ', alerts=' + $status.alerts.state
        if ($stateLabel -ne $lastState) { Write-Host $stateLabel; $lastState = $stateLabel }
        if (!$status.tripUpdates.error -and !$status.alerts.error -and
            $status.tripUpdates.downloadedAtMillis -gt $AfterMillis -and
            $status.alerts.downloadedAtMillis -gt $AfterMillis) { return $status }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'STM source downloads did not recover before the timeout. Inspect the adapter log.'
}
function Request-Trip {
    param($Name, $Origin = 'STM:52511', $Destination = 'STM:51971',
        [DateTimeOffset]$Departure = [DateTimeOffset]::Now, [switch]$ExcludeRealtime)
    $departure = [TimeZoneInfo]::ConvertTime($Departure, [TimeZoneInfo]::FindSystemTimeZoneById('Eastern Standard Time'))
    $body = @{
        query=$query.Replace('STM:52511', $origin).Replace('STM:51971', $destination)
        variables=@{departure=$departure.ToString('o'); excludeRealtime=[bool]$ExcludeRealtime}
    }
    Save-Evidence "$name-request" $body
    $options = @{
        Uri="$base/otp/gtfs/v1"; Method='Post'; ContentType='application/json'
        Body=($body | ConvertTo-Json -Depth 10); TimeoutSec=40
    }
    $response = Invoke-RestMethod @options
    Save-Evidence "$name-response" $response
    if ($response.errors -or $response.data.planConnection.routingErrors.Count -gt 0 -or
        !$response.data.planConnection.edges.Count) { throw "$name returned no itinerary. See evidence." }
    return $response
}
function Check-Scheduled($name) {
    $response = Request-Trip $name
    foreach ($edge in $response.data.planConnection.edges) {
        foreach ($leg in $edge.node.legs) {
            if ($leg.realTime -or $leg.start.estimated -or $leg.end.estimated) {
                throw "$name incorrectly exposed realtime estimates."
            }
            if ($leg.alerts.Count -or $leg.trip.pattern.alerts.Count) {
                throw "$name incorrectly exposed service alerts."
            }
        }
    }
    # Capture OTP itself as well, to distinguish retained engine estimates from adapter fallback.
    $options = @{
        Uri="http://127.0.0.1:$OtpPort/otp/gtfs/v1"
        Method='Post'; ContentType='application/json'; TimeoutSec=40
        Body=(Get-Content -LiteralPath (Join-Path $evidence "$name-request.json") -Raw)
    }
    try { Save-Evidence "$name-otp-direct-response" (Invoke-RestMethod @options) }
    catch { Save-Evidence "$name-otp-direct-error" @{error=$_.Exception.GetType().Name} }
    Write-Host "PASS $name - scheduled fallback"
}
function Check-OtpRecovery {
    $jar = Join-Path (Split-Path -Parent $PSScriptRoot) 'otp.jar'
    $owners = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Contains($jar) -and
            $_.CommandLine -match "--port\s+$OtpPort(?:\s|$)"
    })
    if ($owners.Count -ne 1) {
        throw 'Exactly one managed OTP process could not be identified. Processes were left running.'
    }
    Stop-StmManagedProcess -ProcessId $owners[0].ProcessId
    try {
        $options = @{
            Uri="$base/otp/gtfs/v1"; Method='Post'; ContentType='application/json'
            Body=(@{query=$query;variables=@{departure=[DateTimeOffset]::Now.ToString('o');excludeRealtime=$false}} |
                ConvertTo-Json -Depth 10)
            TimeoutSec=40
        }
        $statusCode = 0
        try { Invoke-WebRequest -UseBasicParsing @options | Out-Null }
        catch { if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode } }
        Save-Evidence 'otp-outage' @{observedAt=[DateTimeOffset]::Now.ToString('o');httpStatus=$statusCode}
        if ($statusCode -ne 502) { throw "OTP outage returned $statusCode instead of a retryable HTTP 502." }
        Write-Host 'PASS OTP outage - retryable HTTP 502'
    } finally {
        $options = @{
            FilePath='powershell.exe'
            ArgumentList=@('-NoProfile','-ExecutionPolicy','Bypass','-File',
                ('"' + (Join-Path $PSScriptRoot 'Start-StmOtp.ps1') + '"'),'-Port',"$OtpPort",
                '-LogPath',('"' + (Join-Path $evidence 'stm-recovery-engine.log') + '"'))
            WindowStyle='Hidden'
            RedirectStandardOutput=(Join-Path $evidence 'stm-recovery-otp.log')
            RedirectStandardError=(Join-Path $evidence 'stm-recovery-otp-error.log')
        }
        Start-Process @options | Out-Null
    }
    $deadline = (Get-Date).AddSeconds(100)
    do {
        try {
            Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$OtpPort/graphiql" -TimeoutSec 3 | Out-Null
            Request-Trip 'otp-recovered' | Out-Null
            Write-Host 'PASS OTP restart - itinerary available again'
            return
        } catch { Start-Sleep -Seconds 2 }
    } while ((Get-Date) -lt $deadline)
    throw 'OTP did not recover before the timeout.'
}
try {
    Set-TestMode 'live'
    $initialStatus = Wait-Downloaded
    Save-Evidence 'initial-status' $initialStatus
    $north = Request-Trip 'live'
    Write-Host 'PASS live - a routed bus itinerary returned'
    $liveAlertsVerified = $false
    if ($initialStatus.alerts.state -eq 'fresh') {
        $south = Request-Trip 'affected-alert' 'STM:62787' 'STM:52486'
        $southAlerts = @($south.data.planConnection.edges[0].node.legs | ForEach-Object { $_.alerts } |
            Where-Object { $_ })
        if (!$southAlerts.Count) {
            Write-Warning 'PENDING: the current southbound itinerary returned no matching service alert.'
        } else {
            $northHashes = @($north.data.planConnection.edges[0].node.legs | ForEach-Object { $_.alerts } |
                Where-Object { $_ } | ForEach-Object { $_.alertHash })
            if ($southAlerts | Where-Object { $_.alertHash -in $northHashes }) {
                throw 'A southbound test alert also appeared in the northbound itinerary. Check selector scope.'
            }
            $liveAlertsVerified = $true
            Write-Host 'PASS live alert - affected southbound journey and unaffected northbound journey'
        }
    } else {
        $visibleAlerts = @($north.data.planConnection.edges[0].node.legs | ForEach-Object { $_.alerts } |
            Where-Object { $_ })
        if ($visibleAlerts.Count) { throw 'Unavailable or stale service alerts were shown as current.' }
        Write-Host 'PASS stale/unavailable alert feed - no current alerts exposed'
        Write-Warning 'PENDING: matching a fresh live service alert. STM source is currently stale/unavailable.'
    }
    Write-Host 'Testing an invalid key; waiting for the next polling cycle...'
    Set-TestMode 'invalid-key'
    $deadline = (Get-Date).AddSeconds(100)
    do {
        $status = Invoke-RestMethod "$base/status" -TimeoutSec 5
        if ($status.tripUpdates.error -eq 'STM HTTP 400') { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Save-Evidence 'invalid-key-status' $status
    if ($status.tripUpdates.error -ne 'STM HTTP 400') { throw 'Did not observe STM rejecting the test key.' }
    Check-Scheduled 'invalid-key'
    Set-TestMode 'feed-outage'
    Save-Evidence 'outage-status' (Invoke-RestMethod "$base/status" -TimeoutSec 5)
    $outageCode = 0
    try { Invoke-WebRequest -UseBasicParsing "$base/trip-updates" -TimeoutSec 5 | Out-Null }
    catch { if ($_.Exception.Response) { $outageCode = [int]$_.Exception.Response.StatusCode } }
    if ($outageCode -ne 503) { throw 'The simulated feed outage did not return HTTP 503.' }
    Check-Scheduled 'outage'
    Set-TestMode 'stale'
    Save-Evidence 'stale-status' (Invoke-RestMethod "$base/status" -TimeoutSec 5)
    $feed = Invoke-WebRequest -UseBasicParsing "$base/trip-updates" -TimeoutSec 5
    if ($feed.StatusCode -ne 200) { throw 'Expected successful HTTP with old data.' }
    Check-Scheduled 'stale'
    $recoveryStarted = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    Set-TestMode 'live'
    $recoveredStatus = Wait-Downloaded -AfterMillis $recoveryStarted
    Save-Evidence 'recovery-status' $recoveredStatus
    $departure = [DateTimeOffset]::Now
    $recovered = Request-Trip 'recovered' -Departure $departure
    $scheduled = Request-Trip 'recovered-scheduled' -Departure $departure -ExcludeRealtime
    $scheduledEstimates = @($scheduled.data.planConnection.edges[0].node.legs |
        Where-Object { $_.realTime -or $_.start.estimated -or $_.end.estimated })
    if ($scheduledEstimates.Count) { throw 'The scheduled baseline returned a realtime estimate.' }
    Write-Host 'PASS upstream connectivity recovery'
    $appliedPredictions = @($recovered.data.planConnection.edges[0].node.legs |
        Where-Object { $_.realTime -and ($_.start.estimated -or $_.end.estimated) })
    $freshPredictionsVerified = $recoveredStatus.tripUpdates.state -eq 'fresh' -and $appliedPredictions.Count -gt 0
    if (!$freshPredictionsVerified) {
        Write-Warning 'PENDING: the recovered feed did not supply a fresh estimate for the selected trip.'
    } else {
        Write-Host 'PASS fresh STM prediction applied after recovery'
    }
    Check-OtpRecovery
    Save-Evidence 'test-summary' @{
        completedAt=[DateTimeOffset]::Now.ToString('o')
        upstreamConnectivityRecoveryVerified=$true
        predictionOutageRecoveryVerified=$freshPredictionsVerified
        liveAlertMatchingVerified=$liveAlertsVerified
        phoneChecksVerified=$false
    }
    if (!$liveAlertsVerified) { Write-Warning 'Fresh live alert matching is still pending. Inspect saved status and itinerary evidence.' }
    Write-Host "Saved sanitized evidence to $evidence"
} finally {
    try { Set-TestMode 'live' }
    catch { Write-Warning 'Could not restore live mode because the adapter is unavailable. Restart the stack.' }
}
if ($InstallAndroid) {
    & (Join-Path $PSScriptRoot 'Install-StmAndroid.ps1') -Port $Port
}
