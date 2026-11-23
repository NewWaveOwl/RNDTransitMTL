# AI Decision Log - ML4

## Overview and coverage

This log records AI use for ML4 in RNDTransitMTL. The requester explicitly
assigned this conversation to ML4. Entry M4-A1 covers the review and authorized
implementation of issue #93, the completion-status clarification, and the
request to document that work.

The source is the visible current Codex conversation, logged on 2026-11-22
(America/Toronto), together with locally verified Git evidence. The conversation
URL has not been supplied: [Conversation link needed]. No conversation was
published to obtain a link. Full available human task prompts are preserved
below. Tool and build results from the implementation are historical evidence
for this documentation update; application tests were not rerun while writing
the logs.

## Contributor and milestone coverage

| Contributor | Requirement / milestone | AI use | Entry IDs |
| --- | --- | --- | --- |
| Requester; individual prompt authorship not independently confirmed | ML4-04.S2, issue #93, parent #45 | Issue review, explicitly authorized implementation, regression tests, commit, status clarification, and logging | M4-A1 |

The implementation commit identifies **Artem Sova** as its Git author. The
supplied issue text identifies **@NewWaveOwl** as its owner. These are source
attributions, not independent confirmation of the author of every prompt.

## Linked AI sessions

| Entry | Date | Tool / conversation link | Purpose | Requirement / milestone |
| --- | --- | --- | --- | --- |
| M4-A1 | 2026-11-22 | Codex; [Conversation link needed] | Review issue #93, preserve failed trip/review input across restarts, test retries, commit, and clarify remaining sign-off | ML4-04.S2 / #93 |

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
