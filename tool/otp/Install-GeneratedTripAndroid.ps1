[CmdletBinding(DefaultParameterSetName='Emulator')]
param(
    [Parameter(ParameterSetName='Emulator')][switch]$Emulator,
    [Parameter(Mandatory,ParameterSetName='Phone')][switch]$Phone,
    [ValidateRange(1024,65535)][int]$Port = 18088
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'Set ANDROID_HOME to your Android SDK.' }
Invoke-RestMethod "http://127.0.0.1:$Port/health" -TimeoutSec 5 | Out-Null
$device = if ($Phone) { '-d' } else { '-e' }
& $adb $device reverse "tcp:$Port" "tcp:$Port"
if ($LASTEXITCODE -ne 0) { throw 'Start one emulator or connect and authorize one USB phone.' }
Push-Location -LiteralPath $repo
try {
    & .\gradlew.bat :androidApp:assembleDebug "-PgenerationEndpoint=http://127.0.0.1:$Port/v1/trips/generate"
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    & $adb $device install -r (Join-Path $repo 'androidApp\build\outputs\apk\debug\androidApp-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    & $adb $device shell am start -n 'com.example.rnd_transit_mtl/.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw 'Could not open the app.' }
} finally { Pop-Location }
