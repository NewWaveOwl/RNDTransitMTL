[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Java,
    [Parameter(Mandatory)][string]$OtpJar,
    [Parameter(Mandatory)][string]$DataDirectory,
    [string]$OsmFile,
    [string]$Destination,
    [string]$ExistingOtpUrl,
    [ValidateRange(1024,65535)][int]$Port = 21885,
    [ValidateRange(1024,32768)][int]$MemoryMb = 1536,
    [string]$DepartureTime = '2026-11-23T08:00:00-05:00',
    [string]$CasesFile,
    [string]$QueryFile
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
foreach ($file in @($Java, $OtpJar, (Join-Path $DataDirectory 'graph.obj'))) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw 'Choose the existing Java 25 runtime, OTP 2.10 JAR and prepared graph directory.' }
}
if ($DepartureTime -notmatch '(Z|[+-][0-9]{2}:[0-9]{2})$') { throw 'DepartureTime requires an explicit UTC offset.' }
$departure = [DateTimeOffset]::Parse($DepartureTime, [Globalization.CultureInfo]::InvariantCulture)
if (!$Destination) { $Destination = Join-Path $repo ('backend\build\routing-evidence\' + [Guid]::NewGuid().ToString('N')) }
$Destination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $Destination) { throw 'Choose a new evidence directory; previous results are never overwritten.' }
$Java = (Resolve-Path -LiteralPath $Java).Path
$OtpJar = (Resolve-Path -LiteralPath $OtpJar).Path
$DataDirectory = (Resolve-Path -LiteralPath $DataDirectory).Path
$javaRelease = Join-Path (Split-Path -Parent (Split-Path -Parent $Java)) 'release'
if (!(Test-Path -LiteralPath $javaRelease) -or [IO.File]::ReadAllText($javaRelease) -notmatch '(?m)^JAVA_VERSION="25\.') { throw 'Use the verified Java 25 runtime.' }
if ($ExistingOtpUrl) {
    $uri = [Uri]$ExistingOtpUrl
    if ($uri.Scheme -notin @('http', 'https') -or $uri.Host -notin @('127.0.0.1', 'localhost', '[::1]') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
        throw 'ExistingOtpUrl must point to a private loopback OTP GraphQL endpoint.'
    }
}
New-Item -ItemType Directory -Path $Destination | Out-Null

function Invoke-RoutingGraphql([string]$Url, [string]$Body) {
    $request = [Net.HttpWebRequest]::Create($Url)
    $request.Proxy = $null
    $request.Method = 'POST'
    $request.ContentType = 'application/json'
    $request.Accept = 'application/json'
    $request.Timeout = 10000
    $request.ReadWriteTimeout = 10000
    $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $request.ContentLength = $bytes.Length
    $stream = $request.GetRequestStream()
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    $response = $request.GetResponse()
    try {
        $reader = [IO.StreamReader]::new($response.GetResponseStream(), [Text.Encoding]::UTF8)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $response.Dispose() }
}
function Json-Text($Value) { return $Value | ConvertTo-Json -Depth 25 -Compress }
function Save-Json([string]$Name, [string]$Text) { [IO.File]::WriteAllText((Join-Path $Destination $Name), $Text, [Text.UTF8Encoding]::new($false)) }

$ownedOtp = $null
try {
    $otpUrl = $ExistingOtpUrl
    if (!$otpUrl) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
        try { $listener.Start() } finally { $listener.Stop() }
        $isolated = Join-Path $Destination 'isolated-otp'
        New-Item -ItemType Directory -Path $isolated | Out-Null
        try { New-Item -ItemType HardLink -Path (Join-Path $isolated 'graph.obj') -Target (Join-Path $DataDirectory 'graph.obj') -ErrorAction Stop | Out-Null }
        catch { Copy-Item -LiteralPath (Join-Path $DataDirectory 'graph.obj') -Destination (Join-Path $isolated 'graph.obj') }
        [IO.File]::WriteAllText((Join-Path $isolated 'router-config.json'), '{}', [Text.UTF8Encoding]::new($false))
        $arguments = @("-Xmx$($MemoryMb)M", '-jar', ('"' + $OtpJar + '"'), '--load', '--serve', '--bindAddress', '127.0.0.1', '--port', "$Port", ('"' + $isolated + '"'))
        $ownedOtp = Start-Process -FilePath $Java -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Destination 'otp.log') -RedirectStandardError (Join-Path $Destination 'otp-errors.log')
        $otpUrl = "http://127.0.0.1:$Port/otp/gtfs/v1"
        $readyBody = Json-Text @{ query = '{ feeds { feedId } }' }
        $deadline = [DateTime]::UtcNow.AddSeconds(180)
        $ready = $false
        while ([DateTime]::UtcNow -lt $deadline -and !$ownedOtp.HasExited) {
            try { $result = Invoke-RoutingGraphql $otpUrl $readyBody | ConvertFrom-Json; if ($result.data) { $ready = $true; break } } catch { }
            Start-Sleep -Milliseconds 500
        }
        if (!$ready) { throw 'The isolated OTP did not become ready. Review its retained logs; existing servers were not changed.' }
    }
    # Alias introspection names: PowerShell 5 treats a JSON __type property as serializer metadata.
    $metadataBody = Json-Text @{ query = 'query { directModes:__type(name:"PlanDirectMode") { enumValues { name } } serviceTimeRange { start end } feeds { feedId version publisher { name url } agencies { timezone routes { mode } } } }' }
    $metadataText = Invoke-RoutingGraphql $otpUrl $metadataBody
    Save-Json 'router-metadata.json' $metadataText
    $metadata = $metadataText | ConvertFrom-Json
    if ($metadata.errors -or $null -eq $metadata.data.feeds) { throw 'Live router metadata could not be verified.' }
    $infoUrl = ([Uri]$otpUrl).GetLeftPart([UriPartial]::Authority) + '/otp/transmodel/v3'
    $infoText = Invoke-RoutingGraphql $infoUrl (Json-Text @{ query = 'query { serverInfo { version internalTransitModelTimeZone } }' })
    Save-Json 'server-info.json' $infoText
    $info = $infoText | ConvertFrom-Json
    if ($info.errors -or $info.data.serverInfo.version -ne '2.10.0') { throw 'Live router is not the verified OTP 2.10.0.' }
    # ReadAllText returns an ordinary string; Get-Content -Raw extended properties cause deep PS5 JSON recursion.
    if (!$QueryFile) { $QueryFile = Join-Path $repo 'backend\src\main\resources\traveltracker-plan.graphql' }
    $query = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $QueryFile).Path)
    $cases = @(
        @{ mode = 'WALK'; origin = @{ lat = 45.5088; lon = -73.5686 }; destination = @{ lat = 45.5312; lon = -73.6150 } },
        @{ mode = 'BICYCLE'; origin = @{ lat = 45.5088; lon = -73.5686 }; destination = @{ lat = 45.5312; lon = -73.6150 } },
        @{ mode = 'CAR'; origin = @{ lat = 45.5088; lon = -73.5686 }; destination = @{ lat = 45.5312; lon = -73.6150 } },
        @{ mode = 'BUS'; origin = @{ lat = 45.5088; lon = -73.5686 }; destination = @{ lat = 45.5312; lon = -73.6150 } },
        @{ mode = 'RAIL'; origin = @{ lat = 45.4735; lon = -73.6035 }; destination = @{ lat = 45.4953; lon = -73.5705 } }
    )
    if ($CasesFile) { $cases = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $CasesFile).Path) | ConvertFrom-Json }
    $caseModes = @($cases | ForEach-Object { $_.mode })
    if ($caseModes.Count -eq 0 -or @($caseModes | Sort-Object -Unique).Count -ne $caseModes.Count) {
        throw 'Provide at least one case, with each mode occurring once so evidence files cannot overwrite one another.'
    }
    $results = @()
    $loadedTransitModes = @($metadata.data.feeds | ForEach-Object { $_.agencies } | ForEach-Object { $_.routes } | ForEach-Object { $_.mode } | Sort-Object -Unique)
    foreach ($case in $cases) {
        if ($case.mode -notin @('WALK', 'BICYCLE', 'CAR', 'BUS', 'RAIL')) { throw 'CasesFile contains an unsupported mode.' }
        $transit = $case.mode -in @('BUS', 'RAIL')
        $supported = if ($transit) { $loadedTransitModes -contains $case.mode } else { @($metadata.data.directModes.enumValues | ForEach-Object { $_.name }) -contains $case.mode }
        if (!$supported) {
            $results += [pscustomobject]@{ mode = $case.mode; status = 'unsupported'; reason = $(if ($transit) { 'mode_not_in_dataset' } else { 'unsupported_mode' }); origin = $case.origin; destination = $case.destination; departureTime = $DepartureTime; candidates = 0 }
            Write-Host ($case.mode + ': unsupported by the observed router/dataset')
            continue
        }
        $modes = if ($transit) { @{ direct = @(); directOnly = $false; transitOnly = $true; transit = @{ access = @('WALK'); egress = @('WALK'); transfer = @('WALK'); transit = @(@{ mode = $case.mode }) } } }
            else { @{ direct = @($case.mode); directOnly = $true; transitOnly = $false } }
        $request = Json-Text @{ query = $query; variables = @{
            originLat = $case.origin.lat; originLon = $case.origin.lon; destinationLat = $case.destination.lat; destinationLon = $case.destination.lon
            departure = $departure.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); modes = $modes; filters = $null; excludeRealtime = $true; walkSpeed = 1.3333333333
        } }
        Save-Json ($case.mode + '-request.json') $request
        $text = Invoke-RoutingGraphql $otpUrl $request
        Save-Json ($case.mode + '-response.json') $text
        $response = $text | ConvertFrom-Json
        if ($response.errors) { throw ('OTP rejected the ' + $case.mode + ' query. Inspect the retained response; this is not a valid no-route result.') }
        $connection = $response.data.planConnection
        if ($null -eq $connection -or $null -eq $connection.edges -or $null -eq $connection.routingErrors) {
            throw 'OTP omitted route candidates or routing errors; this is not verified routing evidence.'
        }
        $knownNoRoute = @('NO_STOPS_IN_RANGE', 'NO_TRANSIT_CONNECTION', 'NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW', 'OUTSIDE_SERVICE_PERIOD', 'LOCATION_NOT_FOUND', 'WALKING_BETTER_THAN_TRANSIT', 'NO_ROUTES_FOUND')
        if (@($connection.routingErrors | Where-Object { $_.code -notin $knownNoRoute }).Count) {
            throw 'OTP returned an unknown routing error; do not interpret provider failure as no route.'
        }
        $edges = @($response.data.planConnection.edges)
        $node = if ($edges.Count) { $edges[0].node } else { $null }
        $legs = @($node.legs | Where-Object { $_ })
        $actualModes = @($legs | ForEach-Object { $_.mode })
        if ($edges.Count -and ($actualModes -notcontains $case.mode -or @($legs | Where-Object { !$_.legGeometry.points }).Count)) {
            throw ('OTP response does not prove real ' + $case.mode + ' mode/geometry.')
        }
        $results += [pscustomobject]@{
            mode = $case.mode; status = $(if ($edges.Count) { 'verified_sample' } else { 'no_route' })
            origin = $case.origin; destination = $case.destination; departureTime = $DepartureTime
            candidates = $edges.Count; durationSeconds = $node.duration; distanceMeters = ($legs | Measure-Object -Property distance -Sum).Sum
            actualModes = $actualModes; routingErrors = @($response.data.planConnection.routingErrors); scheduledOnly = $true
        }
        Write-Host ($case.mode + ': ' + $results[-1].status + ', ' + $node.duration + ' seconds')
    }
    $assets = @()
    foreach ($asset in @($OtpJar, (Join-Path $DataDirectory 'graph.obj'), $OsmFile, (Join-Path $DataDirectory 'stm-gtfs.zip'), (Join-Path $DataDirectory 'exo-trains-gtfs.zip')) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }) {
        $item = Get-Item -LiteralPath $asset
        $assets += [pscustomobject]@{
            file = $item.Name; size = $item.Length; sha256 = (Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash
            role = $(if ($asset -eq $OtpJar) { if ($ownedOtp) { 'loaded_router_binary' } else { 'supplied_router_binary_unverified' } }
                elseif ($item.Name -eq 'graph.obj') { if ($ownedOtp) { 'loaded_graph' } else { 'supplied_graph_unverified' } }
                else { 'available_source_input_not_build_proof' })
        }
    }
    Save-Json 'mode-verification.json' (Json-Text ([ordered]@{
        schemaVersion = 1; verifiedUtc = [DateTime]::UtcNow.ToString('o'); isolatedRouter = [bool]$ownedOtp
        router = $info.data.serverInfo; assets = $assets; samples = $results
        notice = 'Samples prove only these endpoints/date/graph. The service range is a global union, not per-feed service. Schema enums are not sample journey proof.'
        provenance = 'Only an isolated run ties the selected JAR/graph directly to this process. Neighboring OSM/GTFS files are available inputs, not proof of original graph-build provenance.'
        runtimeAssetIdentityVerified = [bool]$ownedOtp
        attribution = 'OpenStreetMap contributors; STM and exo GTFS publishers.'
    }))
    Write-Host "Evidence retained: $Destination"
} finally {
    if ($ownedOtp -and !$ownedOtp.HasExited) { $ownedOtp.Kill(); $ownedOtp.WaitForExit() }
    if ($ownedOtp) { $ownedOtp.Dispose() }
}
