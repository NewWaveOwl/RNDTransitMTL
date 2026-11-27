# Issue #99 verification

Implemented locally on 2026-11-27. This records local
tests, not a staging deployment, physical-phone check, external review or merge.

## Behavior checked

- Later candidate fetches refresh departure time; accepted estimates use that timestamp.
- Server destination histories are independent per verified identity. Fixed return
  destinations remain routable. Failed searches do not add destinations.
- Anonymous guests get five successful proposals per America/Toronto calendar day.
  The sixth request returns `guest_daily_limit`, even during cooldown, without routing.
  Another guest has an independent allowance; accounts have no daily cap.
- Guest counts survive server restart, reset at Montréal midnight and respect both
  daylight-saving transitions. Corrupt quota files fail closed. Failures cost nothing.
- A local HTTP fixture with the actual Supabase SDK confirms concurrent requests
  create one anonymous identity, restore it on restart and retain Guest UI state.
- The real planner coordinator retains its exact accepted trip after fetch errors or
  daily-limit rejection. Start Trip activates that same trip. Existing routing,
  stale-result, cancellation, cooldown and HTTP-disconnect regressions still pass.

## Passed commands

Run from the repository root with the configured JDK 21 and Android SDK:

```powershell
.\gradlew.bat -p backend test
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :shared:compileKotlinJs :shared:compileKotlinWasmJs
.\gradlew.bat :androidApp:assembleDebug
```

The first command includes launcher tests. Reports are under
`backend/build/reports/tests/test/`, `backend/launcher/build/reports/tests/test/`,
`shared/build/reports/tests/jvmTest/` and `shared/build/reports/tests/testAndroidHostTest/`.

The XML reports record 67 backend, 45 launcher, 341 shared JVM and 275 Android host
test executions: **728 total, zero failures, errors or skips**. Common tests execute
on both JVM and Android host; this is an execution count, not a distinct-test count.
The Android debug APK also built successfully under `androidApp/build/outputs/apk/debug/`.

The planner test writes `shared/build/planner-previews/Issue99-retained-offline.png`
and `shared/build/planner-previews/Issue99-retained-guest_daily_limit.png`.
The latter was visually inspected after the entrance animation settled: the full
limit message and Start Trip action are visible at 412 × 716. These are Compose
fixture renders with a stubbed live map, not real-device screenshots.

## Deployment checks still needed

Enable anonymous sign-ins in deployed Supabase Auth and update server and client
together. Follow the [identity and quota-file policy](../backend/README.md#guest-generation-and-per-user-tracks-99).
Preserve the private quota file during updates; run one backend process per file.
Guest identity follows SDK session storage, not a physical person or IP address.

Staging acceptance requires Supabase, the OTP graph/feed bridge, server/tunnel and
phone: test account and guest generation, five guest successes, rejection with retained
preview, fetch failure/retry and live feed estimates. No deployment or credential
settings were changed. iOS execution requires macOS and was not run.
