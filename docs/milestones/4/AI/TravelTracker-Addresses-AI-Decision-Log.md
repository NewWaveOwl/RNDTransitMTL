# AI Decision Log — TravelTracker shared backend

## Overview and coverage

Records the available conversation and observed work on 2026-11-22 and 2026-11-23. The milestone-4
placement is inferred from the requested `WEB_3/ML_4` workspace and linked M4 issues;
it does not establish completion of a separate RNDTransit course milestone.

| Contributor | Scope | AI use | Entry |
| --- | --- | --- | --- |
| NewWaveOwl (requesting account; personal attribution not supplied) | TravelTracker addresses, OTP previews, authentication, six linked features and Windows update | Codex research, implementation, tests and read-only review | A1, A2, A3 |

## Session index

| Entry | Date | Tool / source | Purpose |
| --- | --- | --- | --- |
| A1 | 2026-11-22 | Codex; [Conversation link needed] | Select address provider and implement shared integration |
| A2 | 2026-11-22 | Codex; [Conversation link needed] | Diagnose signed-in address rejection; implement and commit six routing/address issues |
| A3 | 2026-11-23 | Codex; [Conversation link needed] | Diagnose missing tables and live routing Auth setup; verify fixes and prepare requested publication |

## A1 — Public Nominatim and shared Windows backend

**AI tool/model:** Codex; exact model variant [Not documented].
**Conversation:** [Conversation link needed]. No conversation was published to obtain a link.
**Attachments:** No media attachments supplied. The user supplied repository instructions and paths.

### Full prompt 1

```text
work her K:\CLASSES 26 FALL\WEB_3\ML_4\TravelTracker create a sepparate branch in https://github.com/NewWaveOwl/RNDTransitMTL called (backedn for TravelTracker) you can clone it here K:\CLASSES 26 FALL\WEB_3\ML_4\RNDbackend find docs that are reflec tplan to add actual addres to points on the map and tell before doing if you found them
```

### Full prompt 2

```text
Found the exact address lookup plan. It proposes Photon to suggest editable street addresses for map markers through RNDTransitMTL. Related issues: #104, #113 and #120. waht aother options ? Current pooosal updated esiting server app  to ahve a sepprate window for TravelTracker request for routing, but we still need adresses is the proton only optio is htere any ohehret  free options? Reply first
```

### Full prompt 3

```text
waht will be lighter Nominatim or Pelias? Public Nominatim?  we can stick with Public Nominatim reply in the chat
```

### Full prompt 4

```text
Yes, we can choose Public Nominatim so while adres waits for 1 sec couldown we nned laoding spin animtioninside the addrees box  |  The rest implemt ti , and tell me where i should put dev tunnel adrees and how should i update the backedn server windwos
```

### Full prompt 5

```text
PS D:\progs\VS> cd 'D:\SERVER 2026\update'
PS D:\SERVER 2026\update> powershell -NoProfile -ExecutionPolicy Bypass -File .\Apply-TravelTrackerUpdate.ps1 -ServerDirectory "D:\SERVER 2026\rnd_tranzit\RNDTransitServer"
D:\SERVER 2026\update\Apply-TravelTrackerUpdate.ps1 : Не удается привязать аргумент к параметру "LiteralPath", так как
он представляет собой пустую строку.
    + CategoryInfo          : InvalidData: (:) [Apply-TravelTrackerUpdate.ps1], ParameterBindingValidationException
    + FullyQualifiedErrorId : ParameterArgumentValidationErrorEmptyStringNotAllowed,Apply-TravelTrackerUpdate.ps1

PS D:\SERVER 2026\update>
```

**Updater correction after prompt 5:** Reproduced the exact empty-path error in a
fresh Windows PowerShell 5.1 process using `-File` with omitted `UpdateDirectory`.
The earlier in-process tests passed and missed this entry-point difference.
`$PSScriptRoot` was empty during parameter-default binding; moving the omitted
default initialization into the script body fixes it. Explicit `-UpdateDirectory`
also works with the original downloaded helper. The failure is before backup or
target-write steps. Eight fixture checks now include standalone CheckOnly, actual
apply and rollback; a corrected ZIP was regenerated without changing any JARs or
the real server. No successful target-PC deployment is claimed.

**AI output summary:** Located the existing shared OTP/Photon proposal on remote
TravelTracker `milestone_4`; compared self-hosted Photon/Nominatim/Pelias and hosted
options using official documentation. Explained the public Nominatim restrictions.
Created/published the requested empty backend branch using hyphens because Git
branch names cannot contain spaces. After prompt 4, implemented app-specific lookup,
chosen-endpoint routes, editable UI suggestions and a separate launcher window.

**AI suggestion:** A hosted free tier or a regional Photon trial were alternatives.
Public Nominatim was presented as a low-traffic choice requiring explicit searches,
global spacing, attribution, caching, provider switching and manual fallback.

**Student decision:** Explicitly selected Public Nominatim, requested the in-field
waiting spinner, implementation, tunnel configuration and Windows update instructions.
Final source review, deployment and personal learning reflection are not documented.

**Implemented result:** `backend/src/main/kotlin/transit/Nominatim*.kt` and
`TravelTracker*.kt`; the secondary `backend/launcher` window/settings; safe update
scripts under `backend/scripts`; counterpart Blazor changes in the TravelTracker
checkout. No database schema or provider-account configuration was changed.
Backend branch: `backedn-for-TravelTracker`; frontend: `feature/public-nominatim`.
The implementation remains uncommitted; the remote backend branch created earlier
does not yet contain these working-tree changes.

**Agent disclosure:** Codex used separate frontend and launcher implementation
agents and a read-only reviewer under the repository's layer/review guidance. The
primary agent owned Kotlin backend/contracts, coordination and final checks. This
is AI-agent work, not a claim of independent human teammate approval.

**Debugging/refinement evidence:** Local HTTP fixtures exposed the need to disable
OkHttp connection retries and its special 503/zero Retry-After follow-up. Review
also identified admission occurring too late before Auth, a launcher/backend
User-Agent validation mismatch and an unconfirmed marker-name edit being lost on
drag. These were fixed with regression coverage. An initial whole-suite `--no-build`
command could not find Journey.Tests because it is only in the test solution; the
normal prescribed test-solution command builds it before execution.

**Verification evidence:** Backend baseline 44 tests; final 73. Launcher baseline
45; final 60. Both standalone Gradle tests/installDist passed. Frontend baseline
625; final 662, with zero build warnings/errors. The full TravelTracker Release
solution restored and built with zero warnings/errors. Seven isolated updater
checks passed, including path/hash validation, apply and rollback. Launcher main,
secondary and settings windows were rendered with fixtures and visually inspected.
The broader non-PostgreSQL .NET test-solution run discovered/executed/passed 1,716
cases with 0 failures/skips. PostgreSQL was not configured and its marked group was
not run. Final source review found no remaining confirmed actionable findings.

**Artifact evidence:** The corrected update ZIP is under the ignored
`backend/launcher/build/updates/TravelTracker-Nominatim-20261122-powershell-fix/` folder. It contains
34 hash-verified application/dependency JARs plus update instructions and helper;
no graph, private configuration or credentials. SHA-256 is
`7F60C006A3F6EE34DEC2F8A8978208E56A6C436050206E3DDE81DBB9E3D72515`.
It has not been applied to the
user's server. Packaging is not deployment evidence.

**Limitations:** No live public geocoder queries, authenticated tunnel journey,
target graph mode/date verification, physical-device UI test or real target-server
update was performed. Human review and any code/time/productivity estimates remain
[Student reflection needed]. The older pre-existing TravelTracker README edit was
saved in a named Git stash before updating the checkout to current source.

**Technique record:** [A1 in the technique log](TravelTracker-Addresses-AI-Technique-Log.md#a1--provider-selection-and-shared-integration).

### Full prompt 6

```text
we don have a sepparaet interface for the Taravel tracker part of the server it is the same?
```

### Full prompt 7

```text
where should i pu tunnel?
```

### Full prompt 8

```text
lets run full aapp privde commads to do it in the chat
```

**AI output summary for prompts6-8:** Explained the shared EXE with a separate
TravelTracker window, existing Microsoft Dev Tunnel settings, the frontend's
`Services:Routing` base URL, and full-stack launch commands. Supplying commands is
not evidence that the user successfully started or updated either application.

## A2 - Auth diagnosis and six routing/address issues

**Date:** 2026-11-22. **Contributor:** NewWaveOwl requesting account.
**AI tool:** Codex with frontend, backend and read-only review agents; exact model
variants [Not documented]. **Conversation:** [Conversation link needed].

### Full prompt

```text
when I am loogged in i still get this Sign in to look up addresses. Manual entry remains available. | and lets impmemt this issuues https://github.com/NewWaveOwl/TravelTracker/issues/108, https://github.com/NewWaveOwl/TravelTracker/issues/107, https://github.com/NewWaveOwl/TravelTracker/issues/113 , https://github.com/NewWaveOwl/TravelTracker/issues/120 , https://github.com/NewWaveOwl/TravelTracker/issues/127 , https://github.com/NewWaveOwl/TravelTracker/issues/106 |commit after adding each feature and after provide instruction if backed needs to be updated (server)
```

**Student decision:** Explicitly authorized the six issues and separate feature
commits; retained the earlier Public Nominatim choice. Deployment remains a manual
target-PC operation, not an observed result.

**AI output summary / diagnosis:** A downstream Routing401 revoked the whole app
session, and the UI conflated wrong/missing routing Auth settings with local
sign-out. The fix preserves local sessions for optional routing rejection and
returns a safe configuration failure for missing/mismatched backend projects.
The unverified JWT issuer can reject only; matched requests still require live
Supabase user verification. Exact public project-pair setup instructions were
added. The user's actual server configuration is unavailable, so its precise
misconfiguration is not claimed as observed.

**Verification so far:** Baseline .NET1,716 passed, zero build warnings/errors;
backend73 and launcher60 before the auth change. Focused frontend38 and backend8
auth/HTTP checks passed; full suite evidence will be recorded after delivery.

**Remaining evidence:** Per-feature changes, live local OTP results, final tests
and update artifact will be recorded after they are observed. Human review,
student time/code estimates and learning reflections [Student reflection needed].
**Technique record:** A2 in the companion technique log.

**#106 evidence:** Real isolated OTP 2.10.0/Java25 queries returned actual WALK,
BICYCLE, CAR, BUS and RAIL itineraries, with scheduled transit access/egress walks.
The new reproduction script passed both explicit-existing and owned-isolated
PowerShell entry points; the owned run stopped its own process. Graph/JAR hashes
are tied to the isolated process; neighboring source inputs are marked as available
references, not proven original graph-build inputs. A fresh full graph build was
not rerun. Capability source tests and the full Kotlin gate passed 88 backend and
60 launcher tests without compiler warnings. See `backend/RoutingVerification.md`.

**#107 evidence:** The chosen-route adapter now validates optional IANA zones and
explicit offsets, geometry order/counts and connected duration spans. Existing
omitted/null-zone requests remain compatible. The full gate passed 111 backend
and 60 launcher tests without warnings; 23 new adapter/HTTP cases include
cancellation, timeout recovery, malformed-provider data and unequal realtime
delays. Tagged OTP model source establishes actual-time duration semantics; this
is not a claim of live prediction verification. Read-only AI review found no
remaining issues. Human acceptance review remains [Student reflection needed].

**#108 evidence:** Dedicated TravelTracker query returns car/bus/rail coverage and
no-route reasons, boarding/alighting positions and stop IDs, intermediate stops,
transfers/interline counts, original scheduled and effective times, initial wait
and elapsed totals. Android's query remains unchanged. The full Kotlin gate passed
132 backend and 60 launcher tests without failures/skips/compiler warnings. The
new TT query succeeded against the isolated real graph for all five modes. Opt-in
offline replay normalized all 11 recorded alternatives through production code;
bus alternatives contain 15–17 intermediate stops. The samples had no transfers;
fixtures cover transfer/interline and unequal-delay behavior. Live predictions,
authenticated tunnel requests and actual target-PC deployment remain unverified.

**Frontend evidence (#113/#120/#127):** Local commits add keyboard candidate
search/cancel, address/map forms with gray coordinate readouts, and explicit
route selection/confirmation with exact seconds, Windows/IANA compatibility,
Undo and session-local geometry. The .NET Release build had zero warnings/errors;
1,756 ordinary application tests passed, including 702 frontend tests. Source
review findings about optional Routing401, wrong catalogue ID, endpoint geometry
and parent rendering were fixed and covered. Browser and package results are
recorded below; no human acceptance result is invented.

**Final server package:** Runtime source commit
`58d68ff4f66e56479944d8c1acfe073c2d922ab0`; ZIP in
`backend/launcher/build/updates/TravelTracker-Routing-M4-20261122/`.
Size 15,228,905 bytes; SHA-256
`68415AB3152B52D91D36EFFB889D15685DDCEB74B38D7741ED5890127AC9DECF`.
The archive contains 34 JARs and four updater/manifest/instruction files. All JAR
hashes/sizes were checked; source commit is embedded in README and recorded in
sidecars. The packaged current query/auth classes were inspected. Both installDist
builds passed without warnings, eight isolated updater checks passed, and
CheckOnly with genuine previous portable JARs copied to a private fixture left
that fixture unchanged. No real target-PC update or source push was performed.

**Responsive-browser evidence:** An isolated actual-component fixture passed 31
normal-click/keyboard checks at 1440×1000 desktop and 390×844 touch/reduced-motion
phone emulation, with zero application page errors. It used simulated Auth,
address, catalogue and router responses; it did not validate a live user or
provider. Normal public basemap tiles loaded. Search/spinner/focus/cancel,
marker Apply/Undo and route Cancel/Confirm/Save retained exact scheduled seconds.
The phone check exposed a genuine absolute toolbar/status overlay blocking
Transport and address input. A narrow-screen-only CSS fix put controls in normal
flow; the same real-click regression passed. Frontend remained 702 passed, zero
build warnings/errors. Physical-device and authenticated target-server checks
remain unverified. Fixture/browser processes were stopped, and temporary scripts,
logs/screenshots stayed in ignored frontend bin/TestResults directories. The
primary agent inspected representative screenshots. Human review is still not
claimed by an AI review or simulated UI check.

## A3 - Runtime failures and publication checks

**Contributor:** NewWaveOwl requesting account; personal attribution [Not documented].
**Conversation:** [Conversation link needed]. **AI tool:** Codex and the existing
frontend/backend/review agents. Exact model variants [Not documented].

### Full prompt 1

```text
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on GET api/v1/users/me/picture (trace 0HNQ93SM61D7D:00000004).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      GET service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unexpected (status 413, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unexpected (status 413, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unexpected (status 413, failure type (null))
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on PUT api/v1/users/me/picture (trace 0HNQ93SM61D7D:00000008).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on PUT api/v1/users/me/picture (trace 0HNQ93SM61D7D:00000009).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unexpected (status 413, failure type (null))
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on PUT api/v1/users/me/picture (trace 0HNQ93SM61D7D:0000000B).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on PUT api/v1/users/me/picture (trace 0HNQ93SM61D7D:0000000C).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on PUT api/v1/users/me/picture (trace 0HNQ93SM61D7D:0000000D).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      PUT service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
fail: TravelTracker.Users.Api[0]
      Users request failed (PostgresException) on GET api/v1/users/me/picture (trace 0HNQ93SM61D7D:0000000E).
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      GET service request to /api/v1/users failed as Unavailable (status 503, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      GET service request to /v1/places/reverse failed as Unavailable (status 503, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      GET service request to /v1/places/search failed as Unavailable (status 503, failure type (null))
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      GET service request to /v1/routes/capabilities failed as Network (status (null), failure type TaskCanceledException)
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      GET service request to /v1/routes/capabilities failed as Network (status (null), failure type TaskCanceledException) when tring to add route to bike
```

### Full prompt 2

```text
also cannotsave Action could not be completed
Your draft is still here. Correct the details or retry.

The requested service is unavailable. Your entries are retained; try again shortly.
The previous attempt may have saved this. Check your saved list before retrying so you do not create a duplicate. info: TravelTracker.Api.Trips.TripReferenceValidator[0]
      trip check passed: 1 leg(s) snapshotted and ready to save.
fail: TravelTracker.Api.Trips.TripSaveService[0]
      trip write failed (DbUpdateException); this service cannot confirm what the repository kept.
fail: TripFailures[0]
      Trip request failed with HTTP 503 (dependency failure); exception type DbUpdateException.
warn: TravelTracker.Frontend.Api.ServiceClient[0]
      POST service request to /api/v1/trips failed as Unavailable (status 503, failure type (null))
warn: TravelTracker.Frontend.Components.Pages.NewTrip[0]
      Trip request failed as Unavailable (status 503)
```

### Full follow-up answer

Question: Was the latest RNDTransitServer update ZIP installed on the server PC,
and is its current Public HTTPS address still the configured tunnel?

```text
Updated; that tunnel address is current
```

### Full prompt 3

```text
After testing taht all featuer that realted to mpleted ises work. create aq PR to main travel and close iisue in pr thatwe complet tin this caht tracker the ahnges to RND trasit bakend keep inthe seprate brunch and push
```

**AI output summary:** Read-only project database logs identified absent
`UserPictures` and `TripStops`; their existing EF migrations need applying on the
local app stack. The operator received the migration command and explicit browser
draft Save/Restore instructions. The current tunnel's two anonymous health checks
returned 200; one deliberately invalid synthetic credential carrying the app's
expected issuer returned `503 authentication_unavailable` in 360 ms. This confirms
missing/mismatched TravelTracker Auth settings before provider work, not a
successful authenticated journey. No real account token was used for the probe.

**Implemented result:** The app rejects recognizable oversized image dimensions
before PUT and shows fixed, bounded/allowlisted routing setup and connection
guidance. It does not expose arbitrary backend diagnostic text or change the
eight-second client deadline. Separate frontend commits are `5e0db39` and
`cd27913`. Recovery and migration-inventory docs explain the operator steps.
No Kotlin runtime change or replacement update ZIP was needed for these failures.

**Verification:** Release build zero warnings/errors; 1,781 application tests
passed, including 727 frontend cases, plus 55 Node interaction regressions.
Fresh Kotlin gate on runtime-equivalent documentation HEAD `5188f7b` passed
132 backend and 60 launcher cases with zero failures/errors/skips/warnings.
Offline production-adapter replay passed five modes and all 11 recorded real OTP
alternatives. AI source review found no remaining blockers. Updated browser and
publication results belong to the final PR delivery record; this log does not
invent a merge or human review.

**Student decision:** The user confirmed the deployed update/tunnel and explicitly
requested testing, a TravelTracker PR with completed issue references, and pushing
the backend on its separate branch. Execution of database migrations and correction
of server Auth settings have not been observed. Personal reflection, time estimates
and final teammate review remain [Student reflection needed].

**Technique record:** A3 in the companion technique log.

## Reflection

- Observable AI assistance: source research, provider comparison, source generation,
  fixture tests, independent AI review, Windows package and documentation.
- Owner-controlled decision: Public Nominatim and the requested spinner/update scope.
- Personal learning, usefulness and what to change: [Student reflection needed].
