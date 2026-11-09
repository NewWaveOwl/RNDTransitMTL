[CmdletBinding(DefaultParameterSetName = 'Backend')]
param(
    [Parameter(ParameterSetName = 'Emulator')][switch]$Emulator,
    [Parameter(ParameterSetName = 'Phone')][switch]$Phone,
    [ValidateRange(1024, 65535)][int]$Port = 18087,
    [switch]$RefreshTimetable
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $root 'stm-demo'
$jdk = Join-Path $root 'java25'
$jar = Join-Path $root 'otp.jar'
$base = "http://127.0.0.1:$Port"
. (Join-Path $PSScriptRoot 'Stop-StmManagedProcess.ps1')

function Get-DemoDownload([string]$Url, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination -PathType Leaf) { return }
    Write-Host "Downloading $([IO.Path]::GetFileName($Destination))..."
    $partial = "$Destination.part"
    & curl.exe --fail --location --retry 2 --connect-timeout 15 --max-time 900 --output $partial $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed. Retry the script to download $Url." }
    Move-Item -LiteralPath $partial -Destination $Destination -Force
}

New-Item -ItemType Directory -Path $data -Force | Out-Null
try {
    $lock = [IO.File]::Open((Join-Path $data 'setup.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] {
    throw 'The STM demo setup directory is busy. Wait for the other run to finish and try again.'
}

try {
    $stateFile = Join-Path $data "otp-$Port.json"
    $server = $null
    $startedServer = $false
    if (Test-Path -LiteralPath $stateFile -PathType Leaf) {
        try {
            $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
            $process = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
            if ($process -and $process.ProcessName -eq 'java' -and
                $process.StartTime.ToFileTimeUtc() -eq $state.started) {
                $server = $process
            }
        } catch {
            Write-Verbose 'The saved OTP process state is no longer usable.'
        }
    }
    if ($server -and $RefreshTimetable) {
        Stop-StmManagedProcess -ProcessId $server.Id
        $server = $null
    }

    if (!$server) {
        $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
        try {
            $probe.Server.ExclusiveAddressUse = $true
            $probe.Start()
        } catch [Net.Sockets.SocketException] {
            throw "Port $Port is already in use. Rerun with -Port and a free port."
        } finally { $probe.Stop() }

        $java = Get-ChildItem -LiteralPath $jdk -Recurse -Filter java.exe -File -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
        if (!$java) {
            $jdkZip = Join-Path $data 'java25.zip'
            $architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
            $arch = switch ($architecture) {
                'X64' { 'x64' }
                'Arm64' { 'aarch64' }
                default { throw "Unsupported Windows architecture: $architecture." }
            }
            Get-DemoDownload "https://api.adoptium.net/v3/binary/latest/25/ga/windows/$arch/jdk/hotspot/normal/eclipse" $jdkZip
            Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdk -Force
            $java = Get-ChildItem -LiteralPath $jdk -Recurse -Filter java.exe -File |
                Select-Object -First 1 -ExpandProperty FullName
            if (!$java) { throw 'Java 25 was not found in the downloaded archive.' }
        }

        Get-DemoDownload 'https://repo.maven.apache.org/maven2/org/opentripplanner/otp-shaded/2.10.0/otp-shaded-2.10.0.jar' $jar
        Get-DemoDownload 'https://download.bbbike.org/osm/bbbike/Montreal/Montreal.osm.pbf' (Join-Path $data 'Montreal.osm.pbf')
        @{
            transitModelTimeZone = 'America/Toronto'
            transitFeeds = @(@{ type = 'gtfs'; feedId = 'STM'; source = 'stm-gtfs.zip' })
        } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $data 'build-config.json') -Encoding ascii

        if ($RefreshTimetable) {
            foreach ($name in 'graph.obj', 'stm-gtfs.zip') {
                $path = Join-Path $data $name
                if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path }
            }
        }
        Push-Location
        try {
            & (Join-Path $PSScriptRoot 'Start-StmOtp.ps1') -DataDirectory $data -BuildOnly
        } finally { Pop-Location }

        $server = Start-Process -FilePath $java -ArgumentList @(
            '-Xmx4G', '-jar', ('"' + $jar + '"'), '--load', '--serve', '--bindAddress', '127.0.0.1',
            '--port', "$Port", ('"' + $data + '"')
        ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $data "otp-$Port.log") `
            -RedirectStandardError (Join-Path $data "otp-$Port-error.log")
        $startedServer = $true
        @{ pid = $server.Id; started = $server.StartTime.ToFileTimeUtc() } |
            ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding ascii
    }

    $deadline = (Get-Date).AddMinutes(3)
    do {
        try {
            Invoke-WebRequest -UseBasicParsing "$base/graphiql" -TimeoutSec 3 | Out-Null
            break
        } catch {
            if ($server.HasExited -or (Get-Date) -ge $deadline) {
                if ($startedServer -and !$server.HasExited) {
                    Stop-StmManagedProcess -ProcessId $server.Id
                }
                throw "OTP did not start on port $Port. See otp-$Port.log and otp-$Port-error.log in $data."
            }
            Start-Sleep -Seconds 2
        }
    } while ($true)

    # Use noon in Montreal so the smoke test works when the app's live departure time has limited service.
    $montreal = [TimeZoneInfo]::FindSystemTimeZoneById('Eastern Standard Time')
    $noon = [TimeZoneInfo]::ConvertTime([DateTimeOffset]::Now, $montreal).Date.AddHours(12)
    $departure = [DateTimeOffset]::new($noon, $montreal.GetUtcOffset($noon))
    $query = @'
query StmDemoSmokeTest($departure: OffsetDateTime!) {
  planConnection(
    origin: {location: {stopLocation: {stopLocationId: "STM:62787"}}}
    destination: {location: {stopLocation: {stopLocationId: "STM:52486"}}}
    dateTime: {earliestDeparture: $departure}
    modes: {transitOnly: true, transit: {transit: [{mode: BUS}]}}
    first: 1
  ) {
    routingErrors { code description }
    edges { node { legs { mode route { shortName } legGeometry { points } } } }
  }
}
'@
    $body = @{ query = $query; variables = @{ departure = $departure.ToString('o') } } | ConvertTo-Json -Depth 4
    $response = Invoke-RestMethod "$base/otp/gtfs/v1" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
    if ($response.errors) { throw ($response.errors.message -join '; ') }
    $bus = $response.data.planConnection.edges.node.legs | Where-Object { $_.mode -eq 'BUS' -and $_.legGeometry.points }
    if (!$bus) {
        $details = $response.data.planConnection.routingErrors.code -join ', '
        throw "No STM bus route with geometry for $departure ($details). If the timetable is stale, rerun with -RefreshTimetable."
    }
    Write-Host "PASS: STM bus $($bus.route.shortName -join ', ') returned route geometry. Backend: $base/otp/gtfs/v1"
} finally { $lock.Dispose() }

if ($Emulator -or $Phone) {
    & (Join-Path $PSScriptRoot 'Install-StmAndroid.ps1') -Port $Port -Emulator:$Emulator
    Write-Host 'Open Map, expand the planner, and tap Load STM test trip.'
}
