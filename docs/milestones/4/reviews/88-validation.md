# Validation report: Issue #88 (ML4-02.S1 Create and join groups through the backend)

Branch: `feature/88-create-join-groups` @ `3942b2a` | Base: `main` @ `4954dac`

```
Build:          ✅ :androidApp:assembleDebug :desktopApp:classes :webApp:compileKotlinJs :webApp:compileKotlinWasmJs
Lint:           ✅ :androidApp:lintDebug
Baseline tests: jvmTest 341/341 · testAndroidHostTest 279/279
Final tests:    jvmTest 379/379 · testAndroidHostTest 300/300 · backend 66/67 (1 skipped, untouched module) · launcher 45/45
SQL test:       ⚠️ supabase/tests/groups.sql written, NOT RUN (Docker Desktop not running; no Supabase CLI or psql)
Live Realtime:  ⚠️ NOT RUN (needs the migration applied to Supabase and two signed-in devices)
```

## Acceptance criteria coverage
| Criterion | Test(s) | Result |
|---|---|---|
| S1: two clients create/join → membership and shared trip consistent | `groups.sql` §1, 2, 5; `TogetherViewModelTest` (create, join, live updates, share, start); `SupabaseGroupRepositoryTest.joinSendsTheNormalizedCodeAndReturnsMembersInJoinOrder`, `startingAGroupTripReturnsTheMembersOwnGeneratedCopy`, `liveUpdatesRefetchOnEverySignal…`; `SupabaseTripRepositoryTest.groupSaveSendsNoRouteAndReadsTheSharedRouteBack` | Unit ✅ · SQL ⚠️ not run · two-device ⚠️ not run |
| S2: expired code → rejected with clear feedback | `groups.sql` §3, 7; `SupabaseGroupRepositoryTest.anExpiredCodeIsRejectedWithAClearMessage`, `everyServerErrorCodeMapsToItsFailure`; `TogetherViewModelTest.anExpiredCodeShowsTheServersMessage` | Unit ✅ · SQL ⚠️ not run |
| S2: unauthorized update → rejected | `groups.sql` §2 (direct insert/update/delete denied, non-host share), §6 (outsider start/read/History insert) | ⚠️ **only covered by the SQL test, which has not run** |
| Host exit | `groups.sql` §7, 8; `TogetherViewModelTest.aClosedGroupIsClearedWhileTheActiveTripContinues`, `theHostLeavingIsNotReportedAsTheGroupEnding` | Unit ✅ · SQL ⚠️ |
| Code expiry / generation failure | `GroupModelsTest.joinCodeAlphabetMatchesTheServerCheck`; `groups.sql` §1, 7, 8; `failureFor` maps `GROUP_CODE_GENERATION_FAILED` (the server retry loop itself is not exercised) | Unit ✅ · SQL ⚠️ |
| Reconnect | `SupabaseGroupRepositoryTest.liveUpdatesRefetch…`, `aFailedLiveReadReportsReconnectingInsteadOfStopping`; `TogetherViewModelTest.liveUpdatesReplaceTheMembersAndShowReconnecting`; `groups.sql` §2 idempotent rejoin | Unit ✅ · SQL ⚠️ |
| No History regression | `SupabaseTripRepositoryTest.soloSaveStillSendsItsRouteAndNoGroupLink`, `historySkipsGroupRowsWhoseSharedRouteIsNotReadable`, `retryRejectsADifferentGroupTripLink`; existing suites green | ✅ |

## Tests added (38 JVM / 21 Android host, plus the SQL script)
- `GroupModelsTest` (5), `IdsTest`/`IdsBundleTest` (GroupTripId), `TripDtoTest` (+2)
- `SupabaseTripRepositoryTest` (+4), `SupabaseGroupRepositoryTest` (8)
- `TripsStoreGroupTest` (3), `TogetherViewModelTest` (11), `TogetherContentTest` (5, writes screenshots to `shared/build/together-previews/`)
- `supabase/tests/groups.sql` (8 sections, rollback-only)

## Do the tests test something?
Red-then-green happened naturally in two places: a missing comma in `TripDto.kt` failed compilation, and a broken placeholder in `TogetherViewModelTest` was fixed before the first run. The plan's break-and-restore checks for the security rules (`GROUP_NOT_HOST`, the restrictive trips insert policy) need the SQL test to run, so **they haven't been done**.

## Open concerns
- The RLS policies and functions are verified only by review until `groups.sql` runs. Start Docker Desktop and the agent can run it against `postgres:17` with `supabase/tests/support/plain_postgres_auth_shim.sql`.
- The migration must be applied to the Supabase project **before** this app version ships: the History read now embeds `group_trips`.

Overall: **PASS for the Gradle gate; the database gate is still open.**
