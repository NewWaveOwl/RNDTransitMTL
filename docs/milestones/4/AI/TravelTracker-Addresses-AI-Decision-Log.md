# AI Decision Log — TravelTracker shared backend

## Overview and coverage

Records the available conversation and observed work on 2026-11-22. The milestone-4
placement is inferred from the requested `WEB_3/ML_4` workspace and linked M4 issues;
it does not establish completion of a separate RNDTransit course milestone.

| Contributor | Scope | AI use | Entry |
| --- | --- | --- | --- |
| NewWaveOwl (requesting account; personal attribution not supplied) | TravelTracker addresses, OTP previews, Windows controls/update | Codex research, implementation, tests and read-only review | A1 |

## Session index

| Entry | Date | Tool / source | Purpose |
| --- | --- | --- | --- |
| A1 | 2026-11-22 | Codex; [Conversation link needed] | Select address provider and implement shared integration |

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

## Reflection

- Observable AI assistance: source research, provider comparison, source generation,
  fixture tests, independent AI review, Windows package and documentation.
- Owner-controlled decision: Public Nominatim and the requested spinner/update scope.
- Personal learning, usefulness and what to change: [Student reflection needed].
