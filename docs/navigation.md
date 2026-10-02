# Navigation alignment with the course slides

Reference decks in `APP_DEV/powerpoints`:
- `AppDev2_Day_14and15_Multiplatform_Navigation_Group-TeamDecisionMaking_Roles_Norms_before.pptx`
- `AppDev2_Day_18_SharedNavigationBars_Resources_HoistedLayout_after.pptx`

The earlier enum-based `TransitNavigation` and conditional screen switch were replaced with the Navigation 3 pattern from Day 14/15. Day 18's final hoisted layout is applied. The Figma top navigation remains, as requested; the optional bottom navigation example is not used.

All Kotlin paths below are relative to `shared/src/commonMain/kotlin/com/example/rnd_transit_mtl`.

| Slide requirement | Implementation |
| --- | --- |
| Day 14/15 slide 28: Navigation 3 dependency and Kotlin serialization plugin | `gradle/libs.versions.toml`: `multiplatform-nav3-ui = "1.1.1"`, `androidx-navigation3-ui`, `kotlinSerialization`; `shared/build.gradle.kts` applies both |
| Slides 31–33: serializable `NavKey` keys, explicit polymorphic registrations, `rememberNavBackStack` | `Router.kt` defines and registers all five keys; `App.kt` remembers the single stack using `backStackConfig` |
| Slides 38–39: navigation helper | `Navigator.kt`: `current`, `navigate`, `pop`, `hasPrevious`, `popUntil`, `replace`; root is protected and `popUntil` uses the last matching entry |
| Slides 40–43: shared navigator provider and typed destination entries | `LocalNavigator` in `Router.kt`; `CompositionLocalProvider` in `App.kt`; `NavDisplay` and `entryProvider` in `Router.kt` |
| Slide 44: separate screen files | `MainScreen.kt`, `AboutScreen.kt`, plus app-specific `ProfileScreen.kt`, `SettingsScreen.kt`, `HistoryScreen.kt`; each has one screen composable |
| Slide 44: back action only when a previous entry exists | `layout/SharedTopBar.kt` checks `navigator.hasPrevious()`; `NavDisplay.onBack` calls the same `pop()` |
| Slide 47: forward, back, and predictive-back transitions | `Router.kt` uses horizontal slide transitions for all three |
| Day 18 slides 5–8, 23–26: single shared layout hoisted above the router | `App.kt` → provider → `MainLayout { Router(...) }`; `layout/MainLayout.kt` owns the only navigation Scaffold and applies its content padding |
| Day 18 slides 19, 25–26: shared top bar and title metadata | `layout/SharedTopBar.kt`; `ScreenKey.screenTitle` in `Router.kt`; header and titles remain outside screen transitions |

The slide example's `ContactScreen.kt` is replaced by the actual app's Profile, Settings, and History destinations. No unused Contact page or bottom bar was added. `MainScreen.kt` hosts the trip planner, and the former planner `ui/SettingsScreen.kt` is now `ui/TripPlannerContent.kt`, leaving `SettingsScreen.kt` for the actual settings destination.

## Page behavior

- Profile icon → User profile → **about app** → About.
- Receipt icon → History placeholder with TRIP A, TRIP C, and TRIP D.
- Gear icon → Settings; **TRIP SETTINGS** pops back to the existing home entry.
- **Back** pops one entry. Navigation 3 handles Android system and predictive Back through the same router callback.
- Repeated taps on the currently open header destination are ignored by the header.
- About uses `caio_profile.png`, `artiom_profile.png`, and `jim_profile.png`.
- The original palette, cream icons, and Figma top navigation are retained.
- Email, password, theme, language, and history remain placeholders.

Navigation 3's default saveable entry decorator retains trip selections and saved trips while opening other pages. All route keys are registered explicitly for multiplatform restoration. Screen previews are separate files in `ui/previews`, each using the shared layout and navigator provider.

## Validation

Source structure was compared with the slide examples, and obsolete navigation references were removed. `NavigatorTest.kt` covers Profile → About → Back, root protection, returning home, missing and repeated `popUntil` targets, and replacement behavior.

Build/test execution is not yet verified: the available Java/Gradle process fails before compilation with `java.io.IOException: Unable to establish loopback connection`. Visual and device Back verification must therefore be run after the project can build in Android Studio.
