param([int]$Port, [switch]$Emulator)
$ErrorActionPreference = 'Stop'
if (!$Port) { $Port = (Get-Content (Join-Path $PSScriptRoot 'stm-stack-processes.json') -Raw | ConvertFrom-Json).adapterPort }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb -PathType Leaf)) {
    throw "ADB was not found at $adb. Install Android SDK Platform-Tools and set ANDROID_HOME to your SDK directory."
}
$device = if ($Emulator) { '-e' } else { '-d' }
& $adb $device reverse "tcp:$Port" "tcp:$Port"
if ($LASTEXITCODE -ne 0) {
    if ($Emulator) { throw 'Start one Android emulator before installing.' }
    throw 'Connect and authorize one USB phone before installing.'
}
$previousJavaHome = $env:JAVA_HOME
Push-Location -LiteralPath $repo
try {
    if (!$env:JAVA_HOME -and !(Get-Command java.exe -ErrorAction SilentlyContinue)) {
        $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        if (!(Test-Path -LiteralPath (Join-Path $studioJdk 'bin\java.exe') -PathType Leaf)) {
            throw 'Set JAVA_HOME to the JDK used by Android Studio for Gradle.'
        }
        $env:JAVA_HOME = $studioJdk
    }
    & .\gradlew.bat :androidApp:assembleDebug "-PstmOtpEndpoint=http://127.0.0.1:$Port/otp/gtfs/v1"
    if ($LASTEXITCODE -ne 0) { throw 'Android debug build failed.' }
    $apk = Join-Path $repo 'androidApp\build\outputs\apk\debug\androidApp-debug.apk'
    & $adb $device install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the Android debug APK.' }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
& $adb $device shell am start -n 'com.example.rnd_transit_mtl/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Could not open the Android app.' }
