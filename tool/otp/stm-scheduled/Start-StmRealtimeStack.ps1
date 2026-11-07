param(
    [int]$AdapterPort = 18086,
    [int]$OtpPort = 18085,
    [switch]$RunTests,
    [switch]$InstallAndroid
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. (Join-Path $PSScriptRoot 'Stop-StmManagedProcess.ps1')
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'otp.jar'
$bin = Split-Path (Get-ChildItem (Join-Path $root 'java25') -Recurse -Filter java.exe -File |
    Select-Object -First 1 -ExpandProperty FullName)

$upstreamPath = Join-Path $PSScriptRoot 'stm-upstream.json'
$routerPath = Join-Path $PSScriptRoot 'router-config.json'
$config = Get-Content -LiteralPath $routerPath -Raw | ConvertFrom-Json
if ($config.updaters | Where-Object { $_.feedId -eq 'STM' -and $_.headers.apiKey }) {
    [IO.File]::WriteAllText($upstreamPath, ($config | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
}
if (!(Test-Path -LiteralPath $upstreamPath)) { throw 'No saved STM credentials. Run Connect-StmRealtime.ps1 first.' }

$stmIndex = Get-Item -LiteralPath (Join-Path $PSScriptRoot 'stm-adapter-index.json') -ErrorAction SilentlyContinue
$indexInputs = Get-Item -LiteralPath 'stm-gtfs.zip', 'prepare-stm-index.py'
if (!$stmIndex -or ($indexInputs | Where-Object LastWriteTimeUtc -gt $stmIndex.LastWriteTimeUtc)) {
    python (Join-Path $PSScriptRoot 'prepare-stm-index.py')
    if ($LASTEXITCODE -ne 0) { throw 'STM identifier index failed.' }
}
$exoConfigPath = Join-Path $PSScriptRoot 'exo-realtime.local.json'
$exoPollingSeconds = 0
if (Test-Path -LiteralPath $exoConfigPath) {
    try { $exoConfig = Get-Content -LiteralPath $exoConfigPath -Raw | ConvertFrom-Json }
    catch { throw 'Invalid local exo realtime configuration.' }
    $exoPollingSeconds = [int]$exoConfig.pollSeconds
    if (!$exoConfig.token -or $exoPollingSeconds -lt 30 -or $exoPollingSeconds -gt 3600) {
        throw 'Set the exo subscription token and a polling interval from 30 to 3600 seconds.'
    }
    if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'exo-trains-gtfs.zip'))) {
        throw 'Build the combined graph with exo-trains-gtfs.zip before enabling exo realtime.'
    }
}
& "$bin\javac.exe" -cp $jar ExoRealtimeFeed.java StmRealtimeAdapter.java StmRealtimeAdapterTest.java
if ($LASTEXITCODE -ne 0) { throw 'Adapter compilation failed.' }
Write-Host 'Running offline fixture tests with simulated data...'
& "$bin\java.exe" -cp "$jar;." StmRealtimeAdapterTest
if ($LASTEXITCODE -ne 0) { throw 'Adapter regression checks failed.' }
Write-Host 'Offline tests passed. Restarting the live servers...'

# Clean up servers whose full command lines identify this adapter / OTP instance.
# Any other owner of a preferred port is handled by selecting another port below.
$owned = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $command = $_.CommandLine
    $command -and (
        ($command.Contains($jar) -and $command -match "--port\s+$OtpPort(?:\s|$)") -or
        ($command.Contains($PSScriptRoot) -and $command.Contains('StmRealtimeAdapter') -and
            $command -match "\s+$AdapterPort\s+$OtpPort(?:\s|$)")
    )
})
foreach ($server in $owned) {
    Stop-StmManagedProcess -ProcessId $server.ProcessId
}
function Select-AvailablePort {
    param([int]$PreferredPort, [int]$AvoidPort = 0)
    $probe = $null
    try {
        $probe = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $PreferredPort)
        $probe.Server.ExclusiveAddressUse = $true
        $probe.Start()
        $candidate = $probe.LocalEndpoint.Port
        if ($candidate -ne $AvoidPort) { return $candidate }
    } catch {
        # Let Windows select an available port when the preferred port is occupied.
    } finally {
        if ($probe) { $probe.Stop() }
    }
    do {
        $probe = New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
        try {
            $probe.Server.ExclusiveAddressUse = $true
            $probe.Start()
            $selected = $probe.LocalEndpoint.Port
        } finally {
            $probe.Stop()
        }
    } while ($selected -eq $AvoidPort)
    Write-Host "Port $PreferredPort is occupied. Using available port $selected."
    return $selected
}

$AdapterPort = Select-AvailablePort -PreferredPort $AdapterPort
$OtpPort = Select-AvailablePort -PreferredPort $OtpPort -AvoidPort $AdapterPort
if ($OtpPort -eq $AdapterPort) {
    $OtpPort = Select-AvailablePort -PreferredPort 0 -AvoidPort $AdapterPort
}
$others = @($config.updaters | Where-Object {
    !($_.feedId -in @('STM','EXO_TRAINS') -and $_.type -in @('stop-time-updater','real-time-alerts'))
})
$updates = @(
    @{type='stop-time-updater'; feedId='STM'; url="http://127.0.0.1:$AdapterPort/trip-updates"; frequency='PT60S'},
    @{type='real-time-alerts'; feedId='STM'; url="http://127.0.0.1:$AdapterPort/alerts"; frequency='PT60S'}
)
if ($exoPollingSeconds) {
    $updates += @(
        @{type='stop-time-updater'; feedId='EXO_TRAINS'; url="http://127.0.0.1:$AdapterPort/exo/trip-updates"; frequency="PT${exoPollingSeconds}S"},
        @{type='real-time-alerts'; feedId='EXO_TRAINS'; url="http://127.0.0.1:$AdapterPort/exo/alerts"; frequency="PT${exoPollingSeconds}S"}
    )
}
$config | Add-Member -Force -NotePropertyName updaters -NotePropertyValue ($others + $updates)
[IO.File]::WriteAllText($routerPath, ($config | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))

function Quote-Argument([string]$value) { return '"' + $value + '"' }
$session = Join-Path $PSScriptRoot ('stack-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + "-$PID")
New-Item -ItemType Directory -Path $session | Out-Null
$adapterOptions = @{
    FilePath = "$bin\java.exe"
    ArgumentList = @('-cp', (Quote-Argument "$jar;$PSScriptRoot"), 'StmRealtimeAdapter',
        (Quote-Argument $PSScriptRoot), "$AdapterPort", "$OtpPort", '--test-controls')
    WindowStyle = 'Hidden'
    RedirectStandardOutput = (Join-Path $session 'stm-adapter.log')
    RedirectStandardError = (Join-Path $session 'stm-adapter-error.log')
    PassThru = $true
}
$adapter = Start-Process @adapterOptions
$otpOptions = @{
    FilePath = 'powershell.exe'
    ArgumentList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Quote-Argument "$PSScriptRoot\Start-StmOtp.ps1"), '-Port', "$OtpPort",
        '-LogPath', (Quote-Argument (Join-Path $session 'stm-otp.log')))
    WindowStyle = 'Hidden'
    RedirectStandardOutput = (Join-Path $session 'stm-stack-otp.log')
    RedirectStandardError = (Join-Path $session 'stm-stack-otp-error.log')
    PassThru = $true
}
$otp = Start-Process @otpOptions
@{adapterPid=$adapter.Id; otpLauncherPid=$otp.Id; adapterPort=$AdapterPort; otpPort=$OtpPort; logDirectory=$session} |
    ConvertTo-Json | Set-Content -LiteralPath 'stm-stack-processes.json' -Encoding ascii
Write-Host "Started STM adapter on $AdapterPort and OTP on $OtpPort."
Write-Host "Status: http://127.0.0.1:$AdapterPort/status"
Write-Host "Logs: $session"
$deadline = (Get-Date).AddSeconds(60)
$ready = $false
do {
    $adapter.Refresh()
    $otp.Refresh()
    if ($adapter.HasExited -or $otp.HasExited) {
        throw "A server exited during startup. Inspect logs in $session"
    }
    try {
        Invoke-RestMethod "http://127.0.0.1:$AdapterPort/status" -TimeoutSec 3 | Out-Null
        Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$OtpPort/graphiql" -TimeoutSec 3 | Out-Null
        $ready = $true
        break
    } catch { Start-Sleep -Seconds 2 }
} while ((Get-Date) -lt $deadline)
if (!$ready) { throw "Stack did not start. Inspect logs in $session" }
if ($RunTests) {
    & (Join-Path $PSScriptRoot 'Test-StmRealtimeStack.ps1') -Port $AdapterPort -OtpPort $OtpPort
}
if ($InstallAndroid) {
    & (Join-Path $PSScriptRoot 'Install-StmAndroid.ps1') -Port $AdapterPort
}
