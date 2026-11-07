param([int]$Port, [switch]$Emulator)
$ErrorActionPreference = 'Stop'
if (!$Port) { $Port = (Get-Content (Join-Path $PSScriptRoot 'stm-stack-processes.json') -Raw | ConvertFrom-Json).adapterPort }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$adb = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk\platform-tools\adb.exe'
$device = if ($Emulator) { '-e' } else { '-d' }
& $adb $device reverse "tcp:$Port" "tcp:$Port"
if ($LASTEXITCODE -ne 0) {
    if ($Emulator) { throw 'Start one Android emulator before installing.' }
    throw 'Connect and authorize one USB phone before installing.'
}
Push-Location -LiteralPath $repo
try {
    & .\gradlew.bat :androidApp:assembleDebug "-PstmOtpEndpoint=http://127.0.0.1:$Port/otp/gtfs/v1"
    if ($LASTEXITCODE -ne 0) { throw 'Android debug build failed.' }
    $apk = Join-Path $repo 'androidApp\build\outputs\apk\debug\androidApp-debug.apk'
    & $adb $device install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the Android debug APK.' }
} finally { Pop-Location }
& $adb $device shell am start -n 'com.example.rnd_transit_mtl/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Could not open the Android app.' }
