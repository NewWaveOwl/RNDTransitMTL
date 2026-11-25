# AI Decision Log - ML4

## Overview and coverage

This log records AI use for ML4 in RNDTransitMTL. The requester explicitly
assigned the work in M4-A1 to ML4. That entry covers issue #93, its
completion-status clarification, and its documentation request. M4-A2 covers
the GO/settings-sheet handle and confirmed map origin in #94. M4-A3 covers the
Figma arrival, return, and round-trip flow in #92/#107 and the subsequent chat
clarification. Placement of M4-A2/M4-A3 in ML4 is inferred from the existing
milestone records and related trip features; the latest logging request did
not name a milestone again.

The source is the visible current Codex conversation, logged on 2026-11-22
(America/Toronto), together with locally verified Git evidence. The conversation
URL has not been supplied: [Conversation link needed]. No conversation was
published to obtain a link. Full available human task prompts are preserved
below. Tool and build results from the implementation are historical evidence
for this documentation update; application tests were not rerun while writing
the logs. For M4-A2/M4-A3, earlier implementation details also come from the
conversation's retained continuation record; exact available user prompts are
copied below. That record is distinguished from freshly inspected Git and
saved build logs. M4-A1's original evidence and attribution remain unchanged.

## Contributor and milestone coverage

| Contributor | Requirement / milestone | AI use | Entry IDs |
| --- | --- | --- | --- |
| Requester; individual prompt authorship not independently confirmed | ML4-04.S2, issue #93, parent #45 | Issue review, explicitly authorized implementation, regression tests, commit, status clarification, and logging | M4-A1 |
| Requester; individual prompt authorship not independently confirmed | Issue #94; ML4 placement inferred above | Implement confirmed map origin, refine GO into the settings-sheet drag handle, preserve unrelated changes, test, and commit | M4-A2 |
| Requester; individual prompt authorship not independently confirmed | Issues #92/#107; ML4 placement inferred above | Implement supplied Figma flow, commit each feature, inspect the resulting UI flow, and document this chat | M4-A3 |

The implementation commit identifies **Artem Sova** as its Git author. The
supplied issue text identifies **@NewWaveOwl** as its owner. These are source
attributions, not independent confirmation of the author of every prompt.

## Linked AI sessions

| Entry | Date | Tool / conversation link | Purpose | Requirement / milestone |
| --- | --- | --- | --- | --- |
| M4-A1 | 2026-11-22 | Codex; [Conversation link needed] | Review issue #93, preserve failed trip/review input across restarts, test retries, commit, and clarify remaining sign-off | ML4-04.S2 / #93 |
| M4-A2 | 2026-11-22 | Codex; [Conversation link needed] | GO/settings-sheet drag behavior and map-tap origin confirmation, including the user's correction | #94 |
| M4-A3 | 2026-11-22 | Codex; [Conversation link needed] | Figma arrival statistics/celebration, optional return, round-trip planning, UI-flow clarification, and this log update | #92 / #107 |

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
