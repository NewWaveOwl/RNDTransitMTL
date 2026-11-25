# AI Decision Log - ML4

## Overview and coverage

This log records AI use for ML4 in RNDTransitMTL. The requester explicitly
assigned the work in M4-A1 to ML4. That entry covers issue #93, its
completion-status clarification, and its documentation request. M4-A2 covers
the GO/settings-sheet handle and confirmed map origin in #94. M4-A3 covers the
Figma arrival, return, and round-trip flow in #92/#107 and the subsequent chat
clarification. Placement of M4-A2/M4-A3 in ML4 is inferred from the existing
milestone records and related trip features; the latest logging request did
not name a milestone again. M4-A4 covers issue #97 (`ML4-09`), including the
codebase maintainability review, composable splitting, long-function extraction,
documentation refresh, PR #113 creation/header update, and this AI log update.

The source for M4-A1–M4-A3 is the visible Codex conversation logged on
2026-11-22 (America/Toronto), together with locally verified Git evidence. The
source for M4-A4 is the local Antigravity conversation
`0e723272-e84d-42bb-b32d-e91731ad011e` logged on 2026-11-25 (America/Toronto),
together with locally verified Git, Gradle, and GitHub CLI evidence. Public
conversation URLs have not been supplied: [Conversation link needed]. No
conversation was published to obtain a link. Full available human task prompts
are preserved below. Tool and build results from the implementation are
historical evidence for documentation updates; application tests were not rerun
while writing the logs. For M4-A2/M4-A3, earlier implementation details also
come from the conversation's retained continuation record; exact available user
prompts are copied below. That record is distinguished from freshly inspected
Git and saved build logs. M4-A1's original evidence and attribution remain
unchanged.

## Contributor and milestone coverage

| Contributor | Requirement / milestone | AI use | Entry IDs |
| --- | --- | --- | --- |
| Requester; individual prompt authorship not independently confirmed | ML4-04.S2, issue #93, parent #45 | Issue review, explicitly authorized implementation, regression tests, commit, status clarification, and logging | M4-A1 |
| Requester; individual prompt authorship not independently confirmed | Issue #94; ML4 placement inferred above | Implement confirmed map origin, refine GO into the settings-sheet drag handle, preserve unrelated changes, test, and commit | M4-A2 |
| Requester; individual prompt authorship not independently confirmed | Issues #92/#107; ML4 placement inferred above | Implement supplied Figma flow, commit each feature, inspect the resulting UI flow, and document this chat | M4-A3 |
| Requester; individual prompt authorship not independently confirmed | ML4-09, issue #97, PR #113 | Full-codebase maintainability review, composable splitting, function extraction, guideline enforcement, doc cleanup, PR creation/update, and AI logging | M4-A4 |

The implementation commits identify **Artem Sova** as their Git author. The
supplied issue text identifies **@NewWaveOwl** as its owner. These are source
attributions, not independent confirmation of the author of every prompt.

## Linked AI sessions

| Entry | Date | Tool / conversation link | Purpose | Requirement / milestone |
| --- | --- | --- | --- | --- |
| M4-A1 | 2026-11-22 | Codex; [Conversation link needed] | Review issue #93, preserve failed trip/review input across restarts, test retries, commit, and clarify remaining sign-off | ML4-04.S2 / #93 |
| M4-A2 | 2026-11-22 | Codex; [Conversation link needed] | GO/settings-sheet drag behavior and map-tap origin confirmation, including the user's correction | #94 |
| M4-A3 | 2026-11-22 | Codex; [Conversation link needed] | Figma arrival statistics/celebration, optional return, round-trip planning, UI-flow clarification, and this log update | #92 / #107 |
| M4-A4 | 2026-11-25 | Google Antigravity (`0e723272-e84d-42bb-b32d-e91731ad011e`, local app-only ID); [Conversation link needed] | Codebase maintainability review, composable splitting, function extraction, doc refresh, PR #113 `[AI-Cleanup]` / `Closes #97`, and this log update | ML4-09 / #97 |

## M4-A1 - Completed and cancelled trip persistence with review recovery

**Contributor:** Requester; Git commit author Artem Sova, as qualified above.

**Date:** 2026-11-22 (America/Toronto). The implementation commit records
2026-11-22T18:39:23-05:00; this is a commit timestamp, not an AI interaction-time
estimate.

**AI tool / model:** Codex; exact model version [Not documented]. The agent
inspected files, edited code, ran tests/builds, and created the explicitly
requested commit. No subagents were used.

**Source:** Visible current conversation; [Conversation link needed].

**Requirement / milestone:** [Issue #93](https://github.com/NewWaveOwl/RNDTransitMTL/issues/93),
ML4-04.S2, parent #45: save Completed or Cancelled history with stable ownership
and trip IDs, preserve failed-save input for retry, avoid duplicates, and retain
routes and timestamps.

**Task/context:** Existing storage already supported account-scoped final-trip
retry and idempotent cloud writes. The review identified that the durable outbox
retained trips but not unsaved review drafts. Compose saved state alone did not
cover a cold relaunch.

**Purpose of AI use:** Check whether the issue was implemented, then finish the
identified gap after the requester authorized implementation and a commit on
the existing branch.

**Attachments:** No uploaded files reported. Prompt 1 supplied an issue URL and
pasted issue text; the local repository was available to the agent.

### Full prompt 1 - Review and current-branch constraint

```text
revei this issues https://github.com/NewWaveOwl/RNDTransitMTL/issues/93 is it impmeted ? ML4-04.S2 Save completed or cancelled trips and optional reviews without duplicates
 #93
Open
Parent:
ML4-04 Track real trips and save completion or cancellation
Description
@NewWaveOwl
NewWaveOwl
opened 1d ago
Owner
User Story
As a traveller
I want to save completed or cancelled trips with optional reviews
So that I can revisit my real adventures.
Context / Background
Owner: @NewWaveOwl. Parent: #45. Source: Feedback list.md.

Schedule: 21 November 2026 → 23 November 2026. Sprint ends 30 November 2026.

Acceptance Criteria (Given / When / Then)
Scenario 1: Given an active trip, when I finish or confirm cancellation, then one Completed/Cancelled history record saves with stable owner and trip ID.
Scenario 2: Given the server is unavailable, when saving fails, then trip and review input remain available for retry without duplicate records.
Technical Notes / Subtasks
Reuse existing trip/review storage and test restart behavior.
Preserve route and timestamps; never discard Cancelled history.
Definition of Done

Code reviewed and merged

Relevant unit and integration tests pass

Verified in staging / test build; evidence attached work in the asame bracnh as activarighn ow in the folder
```

### Full prompt 2 - Implementation and commit authorization

```text
let impmet this this isses so it will b e copletted, commit after
```

### Full prompt 3 - Completion-status check

```text
so #93 is done?
```

### Full prompt 4 - ML4 logging request

```text
log this ti iai log to Ml4 folder
```

### Full prompt 5 - Documentation commit authorization

```text
commit
```

**AI output summary:** The initial review reported that the signed-in Completed
and Cancelled save paths and duplicate prevention were present, but flagged
review-draft loss across cold restarts. It ran 48 targeted tests successfully
after disabling the build cache. Following prompt 2, the agent extended the
existing account-scoped outbox to persist drafts and their completed parent
snapshots, including parents already acknowledged by the server. It added
cleanup on successful review save, skip, or removal, kept earlier outbox formats
readable, and added disk-recovery and Compose interaction tests. Final review
also identified and fixed an older Active platform snapshot taking precedence
over a newer completed record and its retained draft. The status answer
distinguished implemented/committed code from remaining release sign-off.

**AI suggestion:** Keep the issue open until the remaining staging/test-build
verification and external review/merge requirements are satisfied. Extend the
existing storage rather than introduce a separate persistence service.

**Student decision / how output was used:** Prompt 1 required work on the
currently active branch. Prompt 2 explicitly authorized finishing the issue
and committing the result. Prompt 3 requested clarification of completion
status. Prompt 4 authorized this ML4 record, and prompt 5 explicitly authorized
committing the documentation. The student's independent code
review, physical-device testing, personal learning assessment, and final merge
decision are [Not documented]. Agent-run tests are not presented as manual
student verification.

**Implemented result / evidence:** Git confirms commit
`8a68c0db87d06351301245aa7cff68ad63e38ad5` (`8a68c0d`),
`Preserve trip review drafts across restarts (#93)`, on the branch used for the
work, `fixes_fro_monday_23_nov_sova`. The commit contains five files:

- [FinalTripOutbox.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/data/trips/FinalTripOutbox.kt): persist account-scoped pending trips, review drafts, and draft parent snapshots; restore and merge recovery data.
- [TripsStore.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/state/trip/TripsStore.kt): notify storage when drafts change and let durable final records supersede stale Active snapshots.
- [App.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/App.kt): connect the account's store to durable recovery.
- [FinalTripOutboxTest.kt](../../../../shared/src/jvmTest/kotlin/com/example/rnd_transit_mtl/data/trips/FinalTripOutboxTest.kt): nine regression tests for recovery, retries, cleanup, account isolation, migration, saved-state precedence, and screen interaction.
- [Issue93Verification.md](../../../Issue93Verification.md): implementation and local validation evidence.

Only the five issue files were included in that commit; unrelated local work
was left outside it. These links open the current working files, which may
have subsequent edits. Use `git show 8a68c0d -- <path>` to inspect the recorded
implementation. No push is established by this session's evidence.

**Verification evidence:** The implementation session's final Gradle command
completed successfully:

```powershell
.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinJs :shared:compileKotlinWasmJs :desktopApp:classes --no-build-cache --console=plain
```

The XML test reports showed **262 JVM tests and 210 Android host tests passed**,
with zero failures, errors, or skipped tests: **472 tests total**. The Android
debug APK built, and desktop, JavaScript, and Wasm compilation passed. New
recovery tests loaded fresh Settings/outbox/store objects from an isolated disk
file without a Compose snapshot. The screen test showed failed-save feedback,
recreated the review scene from recovered input, and verified one save after
two retry taps. This is not a physical-device force-stop test or a live staging
server test. Full details and local artifact paths are in the
[verification record](../../../Issue93Verification.md).

**Limitations:** GitHub CLI returned HTTP 401 and the web reader returned a
cache miss, so live issue/PR state was not verified. An initial Gradle cache
packing failure was worked around with `--no-build-cache`; sandbox lock-file
and Git-index permission failures were followed by approved elevated commands.
Live staging, physical-device force-stop testing, external code review, and
merge were not performed. The implementation is committed, but the full issue
Definition of Done was not claimed complete. The conversation link, exact
model version, student time/code-percentage estimates, and personal reflection
remain missing.

**Technique record:** [M4-A1](ML4-AI-Technique-Log.md#m4-a1---completed-and-cancelled-trip-persistence-with-review-recovery).

## M4-A2 - GO settings-sheet handle and confirmed map origin

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git records Artem Sova as author of the implementation commits.

**Date:** Logged 2026-11-22 (America/Toronto); both commits below also record
that local date. Commit timestamps are not interaction-time estimates.

**AI tool / model:** Codex; exact model version [Not documented]. Agent-led
source edits, tests, diff review, and commits are recorded. No subagents were
used in this conversation.

**Source:** Current conversation and its retained implementation record;
[Conversation link needed]. Git and saved build logs were read for this update.

**Requirement / milestone:** [Issue #94](https://github.com/NewWaveOwl/RNDTransitMTL/issues/94).
Make GO reachable and draggable with accessible controls, match the existing
design, confirm map-tap origins, and keep the work separate from route-leg
presentation. ML4 placement is inferred as described in the overview.

**Task/context and purpose of AI use:** Implement the issue on the existing
branch while preserving all uncommitted/debug changes. The user then clarified
that GO should enlarge the teal preferences sheet's drag hitbox.

**Attachments:** No uploaded files reported; local repository supplied.

### Full prompt 1 - Implementation scope and preservation constraints

```text
Implement issue #94 on the current fixes_fro_monday_23_nov_sova branch in K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL.

Make the GO box draggable, keeping it reachable when the window resizes or space is limited. Add a map-tap start-point picker with “Start here” confirmation. Update the origin only after confirmation; cancelling must preserve the previous origin. Preserve accessible controls and match the existing design. Keep these changes separate from route-leg presentation.

Inspect and preserve all existing uncommitted work, including the debug-mode changes. Run relevant tests, review the diff, and commit only the changes for #94. Do not push.
```

### Full prompt 2 - Correct the drag interaction

```text
deagable nent is just part ofthe hitbox of theteal drugbale thing so user have bigger thing to hold pull settings out , rever back and make as it makes sence
```

### Full prompt 3 - Commit authorization

```text
commit
```

**AI output summary:** The first implementation treated GO as independently
movable and added the map-tap confirmation flow. After prompt 2, the agent
removed the separate movement helper and attached GO to the existing anchored
preferences-sheet drag state. GO's button controls remained independent of
dragging. The map picker kept its candidate separate from the saved origin
until confirmation; cancelling retained the previous origin.

**AI suggestion:** The initial independent floating placement was implemented
but superseded after the user's clarification. The final approach uses GO as
the larger grab area for the existing teal sheet.

**Student decision / how output was used:** Prompt 1 specified branch, design,
accessibility, origin confirmation, preservation, tests, commit scope, and no
push. Prompt 2 rejected the independent movement interpretation and clarified
the shared sheet hitbox. Prompt 3 authorized the correction's commit.
Independent student testing and personal learning are [Not documented].

**Implemented result / evidence:** Locally inspected Git confirms:

| Commit | Recorded change |
| --- | --- |
| `0400f6e` | `Make GO box draggable and confirm map-tap origins (#94)`; seven files, including origin confirmation and interaction tests. |
| `cabce97` | `Use GO box as a larger preferences sheet drag handle (#94)`; three files; removed `PlannerGoMovement.kt`, changed the shared drag behavior, and updated its tests. |

Both commits are on the history of `fixes_fro_monday_23_nov_sova`. Current source
references include [MainPlannerContent.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/main/MainPlannerContent.kt)
and [PlannerOriginUi.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/main/PlannerOriginUi.kt).
These links may include later edits; use the recorded commits for historical
scope. Unrelated debug work was kept outside these commits. No push was made.

**Verification evidence:** The saved `build/issue94-work/focused-tests.log`
records an early failure in
`movingGoKeepsItsControlsIndependentAndReachableAfterResize` (36 tests, one
failure). Later `movement-tests.log`, `final-checks.log`, `staged-checks.log`,
and `compile.log` end in `BUILD SUCCESSFUL`. The final check log includes JVM
and Android host tests, Android debug assembly, JavaScript and Wasm compilation.
The continuation record reports interaction checks for the corrected shared
sheet handle and map confirmation/cancellation. Those checks were not rerun
for this documentation update; the initial failure is not represented as a
successful run.

**Limitations:** The full earlier tool transcript is not retained in this
context. A physical-device demonstration, live release, and independent student
verification are [Not documented]. Conversation link, exact model, time,
assignment-code percentage, and personal reflection remain missing.

**Technique record:** [M4-A2](ML4-AI-Technique-Log.md#m4-a2---go-settings-sheet-handle-and-confirmed-map-origin).

## M4-A3 - Figma arrival, return, and round-trip flow

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git records Artem Sova as author of the four feature commits below.

**Date:** Logged 2026-11-22 (America/Toronto); feature commits also record that
local date. This establishes neither active interaction time nor time saved.

**AI tool / model:** Codex; exact model version [Not documented]. The agent
used repository/Git/Gradle tools, GitHub CLI, Figma design and motion tools,
local image rendering/inspection, and a local PostgreSQL-compatible PGlite
runtime. No subagents were used. This was agent-led implementation, not a
record of the student manually writing the generated code.

**Source:** Current conversation and retained implementation record;
[Conversation link needed]. Supplied sources are the Figma and GitHub URLs in
prompt 1. Git commits, source, and saved logs provide local evidence.

**Requirement / milestone:** [Issue #92](https://github.com/NewWaveOwl/RNDTransitMTL/issues/92)
and [issue #107](https://github.com/NewWaveOwl/RNDTransitMTL/issues/107), using
the supplied [Figma design](https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9).
The inspected “round trip” frame was `391:4353` within the supplied page.
ML4 placement is inferred as described above.

**Task/context and purpose of AI use:** Implement the Figma completion and
return journey, route back to the stored origin using fresh routing, offer
one-way/round-trip planning, and commit after each feature. Preserve unrelated
uncommitted work and the earlier no-push constraint.

**Attachments:** No uploaded files reported. The user supplied design/issue
links and the existing repository. Original Figma icons were exported into
Compose resources during implementation.

### Full prompt 1 - Figma implementation and feature commits

```text
imppemt this issue using this figma designs : https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9 frame : round trip (isiues https://github.com/NewWaveOwl/RNDTransitMTL/issues/92 and https://github.com/users/NewWaveOwl/projects/4/views/1?sliceBy%5BcolumnId%5D=Assignees&sliceBy%5Bvalue%5D=NewWaveOwl&pane=issue&itemId=313571690&issue=NewWaveOwl%7CRNDTransitMTL%7C107 | commit after adding each feature
```

### Full prompt 2 - Verify the intended visible flow in chat

```text
DO THE APP HAS THIS FLOW NOW The work like this you reach the final point, the congrats you with particles and opnes dragable teal box with treip stat and option to return back with a split button fastaets way back tha will chaek all posble scenarious  and same ( it will use same trsport as user seclted for theactual trip  and under is the buttin to review and it fuinshes the the trip after you walked aback propmt user with ammll teal box at bootum to reveiw/skeip revei and if skips forwasrds user t pmain screen (enshure that ui relcets this)? REPLY IN THE CAHT
```

### Full prompt 3 - Document this chat

```text
ai log this chat
```

### Full prompt 4 - Commit the log

```text
coomit
```

### Full prompt 5 - Push authorization

```text
and push
```

**AI output summary:** The agent inspected the Figma arrival, searching,
ready, unavailable, returning, and back-at-start screens and their motion.
It implemented a once-per-arrival particle celebration and a draggable,
resize-safe teal statistics sheet with accessible controls. The split button
offers **Fastest way back** and **Same transport**, with **Trip Review** below.
Return search, retry, cancellation, and preview preserve the completed outbound
trip; **Start return trip** accepts a separately identified linked journey.
Back at the original start, a smaller teal sheet offers review of the original
adventure or **Skip**, which returns to the main planner.

Fastest compares supported Walk, Bike, Bus, Metro, and combined transit
alternatives using fresh routed arrival times. It does not claim to compare
every conceivable scenario; Train and REM remain unavailable. Same transport
uses the outbound selected modes and zone restrictions, not reversed geometry
or a promise to reuse the same bus line. Routing failure keeps the original
record and provides retry. The planner also gained **One way** / **Round trip**;
a planned round trip routes each direction independently within the selected
total time budget and finishes at the back-at-start review/skip sheet.

For prompt 2, the agent read the current UI/ViewModel/navigation code and
answered in chat with the implemented sequence, including the explicit return
preview/start step. It qualified that the source implementation requires the
updated app/backend and database migration to appear in a running deployment.
The log update itself changes documentation only.

**AI suggestion:** Use fresh fixed-target routing to the exact stored start,
retain the completed adventure while a return is being considered, and reuse
existing design tokens and Figma assets/motion. Keep the two return policies
explicit and require acceptance before starting the return.

**Student decision / how output was used:** The user selected the Figma design,
named the two issues, and required one commit per feature. The follow-up
described the expected UI sequence and requested a chat response; it is
requirements clarification, not evidence of a successful student-run device
test. Prompt 3 authorized this log update; prompts 4 and 5 then authorized
committing it and pushing the current branch. Independent student code
review, deployment approval, manual testing, and learning assessment are
[Not documented].

**Implemented result / evidence:** Git confirms four separate feature commits
on `fixes_fro_monday_23_nov_sova`:

| Commit | Recorded feature |
| --- | --- |
| `3d5fb18` | `Route return requests to the stored origin with fresh mode comparison (#92, #107)` |
| `6bbe570` | `Match Figma arrival sheet with completion statistics and celebration (#107)` |
| `bee21b9` | `Add confirmed return journey flow and preserve completed trips (#92, #107)` |
| `2bc0ed2` | `Add one-way and freshly routed round-trip planning (#92)` |

Key current files are [TripCompletionContent.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/current_trip/TripCompletionContent.kt),
[TripJourneySheet.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/current_trip/TripJourneySheet.kt),
[TripReturnFlowContent.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/current_trip/TripReturnFlowContent.kt),
[ReturnTripViewModel.kt](../../../../shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/ViewModels/current_trip/ReturnTripViewModel.kt),
[ReturnRouting.kt](../../../../backend/src/main/kotlin/transit/ReturnRouting.kt),
and [RoundTripRouting.kt](../../../../backend/src/main/kotlin/transit/RoundTripRouting.kt).
The nullable journey metadata, DTO/storage changes, migration, tests, and
[deployment notes](../../../ReturnTrips.md) are included in the recorded commits.
Unrelated #93, location, and debug work was preserved and is not attributed to
these features. Nothing had been pushed at the implementation/logging
handoff. The later prompt 5 authorizes pushing the current branch, including
its already committed history; the Git operation's outcome is reported in chat.

**Verification evidence:** The implementation handoff reported 50 backend,
298 JVM, and 239 Android host test executions: **587 total**, with zero
failures/errors/skips. Shared tests execute on more than one platform; this is
an execution count, not 587 unique tests. The saved
`build/round-trip-work/final-checks.log`, inspected again while logging, ends in
`BUILD SUCCESSFUL` for:

```powershell
.\gradlew.bat :backend:test :shared:jvmTest :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinJs :shared:compileKotlinWasmJs :desktopApp:classes --console=plain
```

The retained implementation record also reports passing local PGlite checks
of the real migrations and SQL fixtures, ownership isolation, protected grants,
anonymous access denial, and rollback. The local runner remains at
`build/round-trip-work/test-migration.cjs`. Compose interaction tests covered
failure/retry/cancel, route acceptance, arrival, and original-trip review.
Saved arrival, distance-popup, return-ready/unavailable, and planner screenshots
were inspected against the design. These are agent-run checks, not evidence of
independent student testing. No application/database tests were rerun for the
documentation update, and the historical test counts were not recounted.

**Refinement and failed approaches:** The retained record describes replacing
background-bearing icon exports with transparent renders of the original
Figma SVGs, correcting map cropping around the sheet, and moving the trip-type
selector into its own band after the added row shifted an existing coordinate
interaction test. Later checks passed. GitHub GraphQL authentication failed;
the implementation used GitHub CLI REST retrieval. Mixed-file return changes
were staged selectively so concurrent location/debug changes stayed separate.

**Limitations and release status:** The complete earlier tool transcript is
not retained in this context. Exact model, conversation URL, student time/code
percentage estimates, and personal reflection remain missing. iOS was not
tested on this Windows host. No hosted migration, backend deployment, live
staging verification, physical-device acceptance, external review, merge, or
push had been performed at the implementation handoff. The later publication
authorization is recorded in prompt 5. Apply
[the journey migration](../../../../supabase/migrations/20261122234044_add_trip_journey_metadata.sql)
and deploy the updated backend/app before treating the feature as live.
Itinerary statistics include estimates; fastest GPS speed is not recorded.

**Technique record:** [M4-A3](ML4-AI-Technique-Log.md#m4-a3---figma-arrival-return-and-round-trip-flow).

## M4-A4 - Codebase maintainability cleanup, composable splitting, and documentation refresh (#97)

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git commits `2aac1bb` and `67b3a36` record **Artem Sova** as Git
author, and GitHub CLI is authenticated as `@NewWaveOwl`.

**Date:** 2026-11-25 (America/Toronto). Local transcript timestamps span
`2026-11-25T10:17:32-05:00` through `2026-11-25T11:41:02-05:00`, and PR branch
commits record `2026-11-25T11:02:39-05:00` (`2aac1bb`, cherry-picked from
`5b8ed89`) and `2026-11-25T11:29:08-05:00` (`67b3a36`, cherry-picked from
`3440511`). These are log and commit timestamps, not an estimate of active
student work time.

**AI tool / model:** Google Antigravity (`Antigravity` agentic assistant);
exact model version [Not documented]. The agent inspected workspace files, ran
a local scratch audit script, refactored Kotlin/Compose source files, updated
project documentation, ran local Gradle builds/tests, executed Git commits and
worktree cherry-picks/push, and created/updated PR #113 via `gh`. No subagents
were used (`0` `invoke_subagent` calls in the conversation transcript).

**Source:** Local Antigravity conversation ID
`0e723272-e84d-42bb-b32d-e91731ad011e` (app-only local transcript, not a
publicly shared URL); [Conversation link needed].

**Requirement / milestone:** [Issue #97](https://github.com/NewWaveOwl/RNDTransitMTL/issues/97)
(`ML4-09 Split composables into separate files, extract long functions and review maintainability with AI`),
tracked in [PR #113](https://github.com/NewWaveOwl/RNDTransitMTL/pull/113).

**Task/context:** Fulfill issue #97 across the KMP shared UI and backend
codebase while enforcing `docs/HowToWriteCode_InTheProject.md` (file-per-composable
structure, extracted helpers, KDoc with `@param`, top-of-file layout/animation
tuning sections, and zero `!!` assertions), flag and refresh outdated repository
documentation, preserve pre-existing uncommitted work in the local workspace,
and publish a clean pull request targeting `main`.

**Purpose of AI use:** Confirm repository/doc readability first, audit the full
codebase for maintainability and guideline compliance, perform the authorized
refactors and documentation updates in separate commits, open/update PR #113
with `[AI-Cleanup]` and `Closes #97`, and log this conversation in ML4 AI logs.

**Attachments:** No binary or image uploads reported. Prompt 1 supplied the
local workspace path, repository URL, and issue #97 URL; Prompt 3 referenced
`docs/HowToWriteCode_InTheProject.md` via `@[...]` file mention; Prompt 5
supplied the issue #97 URL.

### Full prompt 1 - Readiness check and issue #97 scope

```text
Reveiw all code base K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL | https://github.com/NewWaveOwl/RNDTransitMTL to fullfill :https://github.com/NewWaveOwl/RNDTransitMTL/issues/97 Split composables into separate files, extract long functions and review maintainability with AI also ishure tha code base follow HowTowrite code md instuctions | before you start reply in  caht that found docs and can read all the files
```

### Full prompt 2 - Authorize issue #97 review, refactor, and doc audit

```text
YOu can start to revei the code and complet issue 97. aslo flag outdated docs and propose in the caht after possbile fixes
```

### Full prompt 3 - Commit code cleanup first, apply selected doc/housekeeping fixes, and explain KDoc gaps

```text
1) Stray root artifacts (main.zip, pr-74.diff, pr72-clean/) are untracked in the workspace and should be cleaned up or added to .gitignore. add them to git ignore. 2) Lets remove Unused legacy Android template folder app/ (com.example.emptyactivity, not included in settings.gradle.kts) can be removed in a housekeeping PR. 3) Fix Readme.md Jimmy Rashed is poper name.  4) fix MainDesignDoc.md 5)@[k:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL\docs\HowToWriteCode_InTheProject.md] fix it 6) Fix how log Ai 7)update Teram Practices 7) explain Pre-existing missing KDoc remains on several data/location/store classes (AccountRepository, LocationJumpFilter, CloudTrips, TripNavigation, TripsStore) — good candidate for a focused documentation-only follow-up. in the chat | !but before commit cahgnes to code base as code_cleanup_separation_of_composabel e.t.c
```

*(Interface note: a `/compact` context-compaction command was triggered at
`2026-11-25T11:14:48-05:00` between Prompt 3 and Prompt 4.)*

### Full prompt 4 - Commit housekeeping/docs and create PR

```text
commit and create a PR 
```

### Full prompt 5 - Close issue #97 and add `[AI-Cleanup]` to PR header

```text
https://github.com/NewWaveOwl/RNDTransitMTL/issues/97 close this iisue in the PR IN HEader mentoin [AI-Cleanup]
```

### Full prompt 6 - Add AI log for this chat, commit, and update PR

```text
also add AI logof this chat  (0e723272-e84d-42bb-b32d-e91731ad011e) commit and update the pr
```

**AI output summary:**
- After Prompt 1, the agent confirmed it located `docs/HowToWriteCode_InTheProject.md`, read issue #97, and could access the repository files before making edits.
- After Prompt 2, the agent audited the codebase, split multi-composable files (`TripScreensV3.kt`, `PlannerOriginUi.kt`, `LocationAccuracyControls.kt`, `DebugModeControls.kt`, `ActiveMapContent.kt`, `TripReturnFlowContent.kt`, `TripCompletionContent.kt`, `TripProposalContent.kt`, `SettingsScreen.kt`, `CompassScreen.kt`, `IntensityPanel.kt`, `GOBox.kt`, `ReviewImage.kt`, `TripReferenceImage.kt`, `AndroidTransitMap.kt`, `Router.kt`), extracted long functions (`TransitOpeningGeneration.kt`, `CurrentTripEffects.kt`, `CurrentTripUnavailableContent.kt`, `TripReviewStatusViews.kt`, plus focused helpers in planner/review/history/details/mock/backend files), removed all `!!` assertions in `shared/src/commonMain` (`6 → 0`) and 2 in `backend/src/main/kotlin/transit/Generation.kt`, verified builds/tests, and reported outdated documentation and workspace housekeeping candidates in chat.
- After Prompt 3, the agent committed the code cleanup first as `5b8ed89` (`code_cleanup_separation_of_composables (#97)`), while leaving `App.kt`, `HistoryScreen.kt`, and other pre-existing uncommitted files untouched to avoid mixing unrelated WIP; then added `/main.zip`, `/pr-74.diff`, and `/pr72-clean/` to `.gitignore`, removed the unused legacy `app/` template (28 files), updated `README.md`, `docs/MainDesignDoc.md`, `docs/HowToWriteCode_InTheProject.md`, `docs/HowToLog_AI.md`, and `docs/TeamPractices.md`, and explained the exact missing KDoc gaps across `AccountRepository`, `LocationJumpFilter`, `CloudTrips`, `TripNavigation`, and `TripsStore` in chat.
- After Prompt 4, the agent removed the link to uncommitted `docs/AndroidDistribution.md` from `README.md` so no broken links would land on `main`, committed the housekeeping/doc updates as `3440511`, discovered `fixes_fro_monday_23_nov_sova` had already been squash-merged into `origin/main` in PR #111, created clean branch `refactor/97-code-cleanup-and-docs` off `origin/main` (`610ea1f`) in a temporary worktree, cherry-picked both commits (`2aac1bb`, `67b3a36`) while resolving two small overlaps with `origin/main` (`Generation.kt` and `TransitOpeningGeneration.kt`), ran the full multi-platform Gradle test/build suite, pushed the branch, opened PR #113, and removed the temporary worktree.
- After Prompt 5, the agent discovered that wrapping `` `Closes #97` `` in inline backticks left GitHub's `closingIssuesReferences` empty, updated PR #113's title to `[AI-Cleanup] Refactor composables, extract long functions, and refresh docs (#97)` and placed unescaped `Closes #97` at the top of the body, and verified via `gh pr view 113 --json closingIssuesReferences` that issue #97 is linked.
- After Prompt 6, the agent recorded entry `M4-A4` in this decision log and the separate technique log, committed it on `refactor/97-code-cleanup-and-docs`, pushed to origin, and updated PR #113's summary.

**Student decision / how output was used:**
- The requester explicitly gated implementation behind a readiness check (Prompt 1), authorized the issue #97 refactor and doc audit (Prompt 2), chose which proposed housekeeping/doc fixes to apply and specified keeping the code cleanup in its own prior commit plus a chat-only explanation of the 5-class KDoc follow-up (Prompt 3), authorized committing and opening a PR (Prompt 4), directed adding `[AI-Cleanup]` to the PR header and closing issue #97 in the PR (Prompt 5), and requested logging this chat and updating the PR (Prompt 6).
- Individual prompt authorship, personal review reasoning for each refactored file, and any manual edits outside this chat are [Not documented].

**Implemented result / evidence:**
- Branch `refactor/97-code-cleanup-and-docs` (based on `origin/main` `610ea1f`) with PR [#113](https://github.com/NewWaveOwl/RNDTransitMTL/pull/113):
  - `2aac1bb` — `code_cleanup_separation_of_composables (#97)` (66 files changed, +9381 / -6760; cherry-picked from local `5b8ed89` with `origin/main` conflict resolution in `backend/src/main/kotlin/transit/Generation.kt` and `shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/main/TransitOpeningGeneration.kt`).
  - `67b3a36` — `chore(docs): remove unused app template, ignore local artifacts, and refresh project docs` (34 files changed, +253 / -662; cherry-picked from local `3440511`).
  - Subsequent AI log commit on `refactor/97-code-cleanup-and-docs` adding `M4-A4` to `docs/milestones/4/AI/ML4-AI-Decision-Log.md` and `docs/milestones/4/AI/ML4-AI-Technique-Log.md`.
- Pre-existing uncommitted workspace files (release workflow, `AndroidDistribution.md`, `AchievementLauncher`, etc.) remained untouched in the local main working directory.

**Verification:**
- Pre-commit verification on `fixes_fro_monday_23_nov_sova` (`5b8ed89`): `.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :backend:test :androidApp:assembleDebug :desktopApp:classes :shared:compileKotlinJs :shared:compileKotlinWasmJs --console=plain` → `BUILD SUCCESSFUL` (`338` JVM tests, `274` Android host tests, `61` backend tests; `0` failures/errors/skips).
- Post-`app/` removal compile check: `.\gradlew.bat :shared:compileKotlinJvm :androidApp:compileDebugKotlin --console=plain` → `BUILD SUCCESSFUL in 14s`.
- PR branch verification on `refactor/97-code-cleanup-and-docs` (`67b3a36`, rebased onto `origin/main` `610ea1f`): `.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :backend:test :androidApp:assembleDebug :desktopApp:classes :shared:compileKotlinJs :shared:compileKotlinWasmJs --console=plain` → `BUILD SUCCESSFUL in 3m 5s` (`340` JVM tests, `276` Android host tests, `65` backend tests with `0` failures/errors and `1` skipped opt-in live smoke test; Android debug APK, Desktop, JS, and WasmJS targets built cleanly).
- GitHub PR verification: `gh pr view 113 --json title,closingIssuesReferences` confirmed title `[AI-Cleanup] Refactor composables, extract long functions, and refresh docs (#97)` and linked closing issue `#97`.
- Application tests were not rerun for the documentation-only AI log commit. Independent student-run testing or manual device inspection is [Not documented].

**Limitations:**
- Local conversation ID `0e723272-e84d-42bb-b32d-e91731ad011e` is app-only and not publicly accessible via URL; exact model version, active interaction time, code-impact percentage, and student reflection are [Not documented] / [Student reflection needed].
- `App.kt` and `HistoryScreen.kt` were excluded from `2aac1bb` to avoid mixing pre-existing uncommitted `AchievementLauncher` edits into PR #113.
- PR #113 is open and awaiting team review/merge; issue #97 remains open until PR #113 merges.

**Technique record:** [M4-A4](ML4-AI-Technique-Log.md#m4-a4---codebase-maintainability-cleanup-composable-splitting-and-documentation-refresh-97).

## Reflection

- What AI helped with: tracing persistence and retry behavior, identifying the
  missing durable draft, implementing recovery, adding regression coverage,
  running local checks, and creating the authorized commit.
- Decisions influenced by AI: the requester authorized implementation after
  the gap review; the specific storage design was implemented by the agent.
  The student's reasoning for choosing that design is [Student reflection needed].
- Problems and limitations: authentication and sandbox/build-cache failures
  limited tooling; local test success did not establish staging, device, or
  merge completion.
- What I learned / would change: [Student reflection needed].
- Active AI interaction time, productivity gains, and assignment code percentage:
  [Not documented].

For M4-A2/M4-A3:

- Observable student decisions: correct GO into the shared settings-sheet
  hitbox; use the supplied Figma round-trip design; commit each feature; and
  check the arrival/return/review sequence explicitly in chat.
- What AI helped with: implementing the interaction and routing/storage flow,
  preserving unrelated changes, running local checks, comparing rendered UI,
  and explaining the difference between source implementation and live rollout.
- Personal usefulness, learning, time saved or wasted, assignment-code
  percentage, and what the student would change: [Student reflection needed].

For M4-A4:

- Observable student decisions: require a read-only confirmation before starting
  changes, request an outdated-doc audit alongside issue #97, keep the code
  refactor in its own commit before housekeeping/doc fixes, defer the 5-class
  KDoc pass to a chat explanation rather than mixing it into the cleanup PR,
  and enforce the `[AI-Cleanup]` PR header and `Closes #97` link.
- What AI helped with: auditing multi-composable files and long functions
  against `docs/HowToWriteCode_InTheProject.md`, splitting UI files and
  removing `!!` assertions, refreshing outdated project docs, rebasing cleanly
  onto `origin/main` in an isolated worktree without disturbing local WIP,
  running multi-platform Gradle verification, and preparing PR #113.
- Problems and limitations: three compile/warning issues occurred during the
  initial refactor (`ReviewV3.kt`, `TripReturnFlowContent.kt`,
  `AndroidTransitMap.kt`) and were fixed before committing; the temporary
  worktree initially missed gitignored `local.properties`; and backticked
  `Closes #97` did not link the issue until unescaped.
- Personal usefulness, learning, time saved or wasted, assignment-code
  percentage, and what the student would change: [Student reflection needed].
