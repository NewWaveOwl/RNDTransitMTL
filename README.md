This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

### Transit interface migrated from As2

The transit interface from `As2_RNDTranzit` now lives in
`shared/src/commonMain/kotlin/com/example/rnd_transit_mtl`:

- `App.kt` loads the shared transport data and applies the transit theme.
- `TransitOpeningScreen.kt` owns trip selections, validation, and saved trips.
- `ui` contains settings, time controls, transport and route choices, intensity, and trip results.
- `model` contains transport types and routes; `data` contains JSON-backed fake repositories.
- `ui/theme` contains the transit palette and shared LINE Seed JP typography.

The map, fonts, and JSON data are packaged in `shared/src/commonMain/composeResources`.
Repositories use suspend functions to load resources across platforms. The existing
Android, desktop, web, and iOS entry points already call `App()`.
The original As2 project is retained as a working reference.

`FakeTransportRepositoriesTest` under `shared/src/jvmTest` checks packaged JSON loading,
transport ordering, route ownership, unique route IDs, and bus labels.
File checks confirmed that the migrated resources match the originals and contain
six transport types and eighteen routes. The build/test attempt in this environment
stopped before compilation because Gradle could not establish a loopback connection;
compilation, tests, and interactive behavior still require verification in Android Studio.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
