# Android development and APK builds

This guide is for developers building RNDTransitMTL from source. For the app's
features and availability, see the [main README](../README.md). Server setup,
tunnels, data imports and backend tests live in [backend/README.md](../backend/README.md).
All commands run from the repository root.

## Requirements

- Android Studio with support for the project's Android Gradle Plugin version, listed in `gradle/libs.versions.toml`.
- JDK 21 selected as the Gradle JDK.
- Android SDK Platform 37 and Android SDK Platform-Tools.
- An Android emulator or device running Android 7.0 / API 24 or newer.
- Internet access for dependencies, sign-in, map data and remote trip generation.
- A running configured routing server for Android trip generation.

## Get the source

```powershell
git clone https://github.com/NewWaveOwl/RNDTransitMTL.git
cd RNDTransitMTL
```

Open this repository folder in Android Studio, let Gradle sync, and install any requested SDK components. Use the included Gradle wrapper; a separate Gradle installation is not required.

## Build and launch Android in Android Studio

In the repository root's **`gradle.properties`**, add your current server URL:

```properties
# Local backend endpoint; replace YOUR-HOST with the launcher's Public HTTPS host
generationEndpoint=https://YOUR-HOST/v1/trips/generate
```

Keep the existing Kotlin, Gradle and Android settings. Save and **Sync Gradle**.
The endpoint is embedded at build time; an installed APK does not pick up later
property edits. Leave personal/temporary endpoint edits uncommitted. Use the current
launcher address rather than another developer's old `trycloudflare.com` URL.

Without this setting, Android debug builds default to `http://10.0.2.2:18088` on
the emulator's host PC. That default does not connect to a server on a second PC.

Select the `androidApp` run configuration and an emulator or connected device, then click **Run**. This builds and installs the debug version from source.

Alternatively, from the repository root in PowerShell, with a device or emulator connected:

```powershell
.\gradlew.bat :androidApp:installDebug '-PgenerationEndpoint=https://YOUR-HOST/v1/trips/generate'
adb shell am start -n com.example.rnd_transit_mtl/.MainActivity
```

For a physical device, enable USB debugging. If `adb` is not on your PATH, run it from your Android SDK's `platform-tools` folder.

## Run against a local development server

Use the [manual backend startup instructions](../backend/README.md#manual-development-startup)
to start OTP, the bridge and Ktor. Then, with an emulator running:

```powershell
.\backend\scripts\Install-GeneratedTripAndroid.ps1 -Emulator
```

Use `-Phone` for an authorized USB phone. The helper embeds a local endpoint and
uses `adb reverse`; it is separate from connecting to a server on another PC.

## Build and launch a release

Build the release variant from source:

```powershell
.\gradlew.bat :androidApp:assembleRelease '-PgenerationEndpoint=https://YOUR-HOST/v1/trips/generate'
```

The output is in `androidApp/build/outputs/apk/release/`. The project currently has no release signing configuration, so this command produces an unsigned APK.

To create an installable release, use Android Studio's **Build → Generate Signed Bundle / APK**, choose **APK** and the `androidApp` module, create or select a signing key, and choose the **release** variant. Install the resulting signed APK on your device, then launch RNDTransitMTL from the app launcher. You can also install and launch it using:

```powershell
adb install -r "PATH_TO_SIGNED_RELEASE_APK"
adb shell am start -n com.example.rnd_transit_mtl/.MainActivity
```

Replace `PATH_TO_SIGNED_RELEASE_APK` with the actual signed APK path. When switching from a debug build to a release signed with a different key, uninstall the debug app first; uninstalling removes its local app data.

Before distributing an APK, configure a stable reachable HTTPS generation endpoint
and verify sign-in, generation, cancellation and realtime fallback on an Android
device. Keep signing keys and passwords outside Git. Producing a signed APK does
not start or host the routing backend; its server must remain available.

## Other platforms

From the repository root:

- Desktop: `.\gradlew.bat :desktopApp:run`
- Web with Wasm: `.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun`
- Web with JavaScript: `.\gradlew.bat :webApp:jsBrowserDevelopmentRun`
- iOS: on macOS, open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator or device, and run the app.

On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`.

## Regression tests and compilation checks

```powershell
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug :shared:compileKotlinJs :shared:compileKotlinWasmJs
```

Tests cover trip/review state, navigation, History, simulation and map geometry.
Backend and launcher checks are documented in
[backend/README.md](../backend/README.md#regression-tests).
iOS compilation requires macOS and Xcode.
