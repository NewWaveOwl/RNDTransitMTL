# Issue #93: final trip and review recovery

Verified on 22 November 2026 on `fixes_fro_monday_23_nov_sova`.
Issue: [ML4-04.S2 Save completed or cancelled trips and optional reviews without duplicates](https://github.com/NewWaveOwl/RNDTransitMTL/issues/93).

## Implemented behavior

The existing account-scoped final-trip outbox now also retains unsaved review
drafts and their completed parent trips. Ratings, comments, unfinished image
links, and the current question survive recreation from persistent storage,
including when the parent trip has already synced. These retained parents are
not queued for another trip insert.

Trip acknowledgement preserves any unsaved review. Successful review saves,
explicit skips, and trip removal clear the associated recovery data. Pending
Completed and Cancelled records remain queued until acknowledged or explicitly
removed. Earlier trip-only outboxes remain readable, including the legacy
unscoped format.

## Automated evidence

The complete validation command passed:

```powershell
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinJs :shared:compileKotlinWasmJs :desktopApp:classes --no-build-cache --console=plain
```

| Check | Result |
| --- | --- |
| JVM tests, including Compose screen interactions | 262 passed, 0 skipped |
| Android host tests | 210 passed, 0 skipped |
| Android debug APK | Built successfully |
| Desktop classes | Compiled successfully |
| JavaScript and Wasm shared sources | Compiled successfully |

`FinalTripOutboxTest` adds nine regression tests. Recovery tests write to an
isolated settings file and load fresh Settings, outbox, and TripsStore objects;
they do not restore a Compose snapshot. They cover:

- Lost trip and review responses followed by disk recovery and retry with one
  trip ID and one review, preserving ownership, route, and timestamps.
- A review server failure after the parent trip has already synced.
- Skipping a review while Completed and Cancelled trips still await upload.
- Clearing drafts on skip or trip deletion, without resurrecting deleted trips.
- Account isolation across sign-out and sign-in recovery.
- Migration of earlier trip-only outboxes, preserving Cancelled history.
- Preserving a newer Compose draft when merging disk recovery.
- Acknowledged review recovery superseding an older Active trip snapshot without
  queuing its completed parent for upload again.
- The real review screen showing a save failure, restoring its draft into a new
  scene from disk, and saving once when Save is tapped twice on retry.

Generated local artifacts:

- `shared/build/reports/tests/jvmTest/index.html`
- `shared/build/reports/tests/testAndroidHostTest/index.html`
- `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- `build/issue93-work/validation.log`

## Remaining release verification

These results verify local tests and a compiled test build. Live staging,
physical-device force-stop testing, external code review, and merging were not
performed by this validation. The server failure tests use a test repository;
existing Supabase repository tests also passed.
