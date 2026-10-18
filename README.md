# RNDTransitMTL

## Goal

RNDTransitMTL is a Kotlin Multiplatform discovery app for Greater Montréal. Its goal is to generate semi-random adventures based on the user's available time, transportation choices, and interests, helping them explore unfamiliar places without choosing a destination first.

The current prototype includes a nickname introduction, a map-based trip planner, simulated trips, reviews with optional image links, and History with review editing and trip details. Profile, About, and Settings remain available. See the [main design document](docs/MainDesignDoc.md) for the planned features and [team practices](docs/TeamPractices.md) for the team's working agreements.

## Trip and review flow

1. Enter a nickname for the current session.
2. Use GO to choose your time, transportation, and attraction intensity, then generate a trip. You can resume an active trip after navigating away.
3. Follow the simulated route on the sample map. The trip information popup shows its details; the map supports pan and zoom.
4. At completion, tap the 100% progress bar to open the review. Give an overall rating, optionally answer the other questions, add a comment or an HTTPS image link, then save or skip.
5. Open History to filter completed trips, expand saved feedback, view details, edit a review, or remove a trip. Review photos load when saved feedback is displayed, with retry and browser fallback controls.

The map and route are a simulation; this prototype does not use GPS or live street routing. Trips and review drafts support saved-state restoration, but there is no permanent database. A fresh session without restored state starts with empty History. The nickname stays only in process memory.

This flow is imported from [Artiom's Assignment 3 repository](https://github.com/NewWaveOwl/As3_APPDEV2_RND_Tranzit_Sova/tree/8b1b538), including its shared state, navigation, resources, previews, and regression tests. The destination's design documents and earlier screenshots remain historical planning material.

## Quick-start

### Requirements

- Android Studio with support for the project's Android Gradle Plugin version, listed in `gradle/libs.versions.toml`.
- JDK 21 selected as the Gradle JDK.
- Android SDK Platform 37 and Android SDK Platform-Tools.
- An Android emulator or device running Android 7.0 / API 24 or newer.
- Internet access for the first dependency download.

### Get the source

```powershell
git clone https://github.com/NewWaveOwl/RNDTransitMTL.git
cd RNDTransitMTL
```

Open this repository folder in Android Studio, let Gradle sync, and install any requested SDK components. Use the included Gradle wrapper; a separate Gradle installation is not required.

### Build and launch Android

Select the `androidApp` run configuration and an emulator or connected device, then click **Run**. This builds and installs the debug version from source.

Alternatively, from the repository root in PowerShell, with a device or emulator connected:

```powershell
.\gradlew.bat :androidApp:installDebug
adb shell am start -n com.example.rnd_transit_mtl/.MainActivity
```

For a physical device, enable USB debugging. If `adb` is not on your PATH, run it from your Android SDK's `platform-tools` folder.

### Build and launch a release

Build the release variant from source:

```powershell
.\gradlew.bat :androidApp:assembleRelease
```

The output is in `androidApp/build/outputs/apk/release/`. The project currently has no release signing configuration, so this command produces an unsigned APK.

To create an installable release, use Android Studio's **Build → Generate Signed Bundle / APK**, choose **APK** and the `androidApp` module, create or select a signing key, and choose the **release** variant. Install the resulting signed APK on your device, then launch RNDTransitMTL from the app launcher. You can also install and launch it using:

```powershell
adb install -r "PATH_TO_SIGNED_RELEASE_APK"
adb shell am start -n com.example.rnd_transit_mtl/.MainActivity
```

Replace `PATH_TO_SIGNED_RELEASE_APK` with the actual signed APK path. When switching from a debug build to a release signed with a different key, uninstall the debug app first; uninstalling removes its local app data.

### Other platforms

From the repository root:

- Desktop: `.\gradlew.bat :desktopApp:run`
- Web with Wasm: `.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun`
- Web with JavaScript: `.\gradlew.bat :webApp:jsBrowserDevelopmentRun`
- iOS: on macOS, open `iosApp/iosApp.xcodeproj` in Xcode, select a simulator or device, and run the app.

On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`.

### Regression tests and compilation checks

```powershell
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug :shared:compileKotlinJs :shared:compileKotlinWasmJs
```

The imported tests cover trip generation and completion, review state, navigation and Back behavior, History presentation, simulation, and map geometry. iOS compilation requires macOS and Xcode.

## Screenshots of application

### Main map and trip controls

![Main map with GOBox, transport selection, and attraction intensity](screenshots/MileStone_1_MainScreen.png)

### User profile

![User profile with account placeholders and the About app button](screenshots/MileStone_1_user.png)

### About

![About page introducing Caio, Artiom, and Jimmy](screenshots/MileStone_1_about.png)

These screenshots show the earlier milestone UI. They have not been refreshed for the imported trip and review flow.

## Team members

- Artiom Sova
- Caio Nunes
- Jimmy Rashid
