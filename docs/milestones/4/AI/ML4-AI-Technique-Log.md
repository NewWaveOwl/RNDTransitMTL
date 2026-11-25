# AI Technique Log - ML4

See the [decision log](ML4-AI-Decision-Log.md) for the session index, full
available prompts, student decisions, commit evidence, and source limitations.
Related iterations are grouped by significant challenge: M4-A1 covers review
recovery, M4-A2 covers GO/origin interactions, M4-A3 covers the Figma
arrival/return/round-trip flow, and M4-A4 covers the issue #97 maintainability
cleanup, composable splitting, and documentation refresh. Personal judgments
remain with the student.

## M4-A1 - Completed and cancelled trip persistence with review recovery

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git records Artem Sova as implementation commit author.

**Date:** 2026-11-22 (America/Toronto).

**Conversation(s):** Current visible Codex conversation; [Conversation link needed].

**Decision record / full prompts:** [M4-A1](ML4-AI-Decision-Log.md#m4-a1---completed-and-cancelled-trip-persistence-with-review-recovery).

| Field | Record |
| --- | --- |
| Challenge addressed / context | ML4-04.S2 / issue #93: retain Completed and Cancelled trip records, preserve review input after server failures and restarts, and retry without duplicate records. The existing outbox persisted final trips but omitted unsaved review drafts. |
| AI(s) used | Codex for source review, implementation, tests/builds, Git operations, and documentation. Exact model version [Not documented]. No subagents or second AI used in this recorded conversation. |
| Observable prompting pattern | Prompt 1 supplied the issue URL, full acceptance criteria, and a current-branch constraint. Prompt 2 authorized implementation and a commit after the review. Prompt 3 checked whether the result met completion requirements. Prompt 4 requested the ML4 log, and prompt 5 authorized committing the documentation. These observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. The student has not named techniques or explained their intended purpose. |
| Iterations | Five visible human task prompts are preserved in the decision log. The agent's related source review, implementation, regression tests, and stale-snapshot refinement are grouped here rather than counted as separate challenges. |
| Debugging / refinement | The agent traced the outbox, draft store, account binding, review save path, and idempotent trip/review storage. It retained completed parent snapshots for drafts even after trip acknowledgement, connected draft-change callbacks, and cleared recovery on save, skip, or removal. Final review found an older Active platform snapshot could prevent recovery; the agent added final-record precedence and a regression test. Manual student code edits during this challenge are [Not documented]. |
| Failed / rejected approaches | The initial Gradle run failed while packing a cache entry; a run with `--no-build-cache` passed. Later Gradle wrapper lock-file and Git-index access failed inside the sandbox; approved elevated commands succeeded. GitHub issue retrieval returned HTTP 401, and the web reader returned a cache miss. No student-rejected implementation approach is documented. |
| Strategy changes | The work moved from a read-only implementation check to code changes only after prompt 2. Recovery was expanded from pending trip data to drafts plus their parent trips, including already-synced parents. Disk-backed tests and a recreated Compose scene were used to check recovery without relying on a saved Compose snapshot. |
| Verification | Agent-run checks in the implementation session: 262 JVM tests and 210 Android host tests passed with zero failures, errors, or skips; Android debug APK built; desktop, JavaScript, and Wasm compilation passed. Nine new tests cover response-loss retries, server unavailability, cleanup, Cancelled history, account isolation, old formats, state precedence, and screen retry/double-tap behavior. Student-run independent verification is [Not documented]. Tests were not rerun for this logging update. |
| Code impact | Commit `8a68c0d` contains three production Kotlin files, one test file, and one verification document. The student's estimated percentage of assignment code affected and its basis are [Not documented]. |
| Time | Student-estimated active AI interaction time [Not documented]. The commit timestamp does not establish elapsed working time. |
| Outcome | The requested implementation was committed on `fixes_fro_monday_23_nov_sova` as `8a68c0d`, `Preserve trip review drafts across restarts (#93)`. The completion answer left staging/test-build sign-off and external review/merge pending. No push, staging deployment, physical-device force-stop check, or merge is established. |
| Productivity / learning reflection | [Student reflection needed]: usefulness, time saved or wasted, what was learned about durable storage versus saved UI state, and what the student would change next time. |

## M4-A2 - GO settings-sheet handle and confirmed map origin

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git records Artem Sova as implementation commit author.

**Date:** Logged 2026-11-22 (America/Toronto); same local date as the commits.

**Conversation(s):** Current visible Codex conversation and retained
implementation record; [Conversation link needed].

**Decision record / full prompts:** [M4-A2](ML4-AI-Decision-Log.md#m4-a2---go-settings-sheet-handle-and-confirmed-map-origin).
ML4 placement is inferred in that log's overview.

| Field | Record |
| --- | --- |
| Challenge addressed / context | Issue #94: reachable GO drag area, accessible responsive settings-sheet interaction, and map-tap origin selection committed only after confirmation. Preserve existing uncommitted/debug work and keep route-leg presentation separate. |
| AI(s) used | Codex for source edits, tests, diff review, Git commits, and documentation. Exact model version [Not documented]. No subagents used in this conversation. |
| Observable prompting pattern | Prompt 1 supplied functional constraints, branch/path, verification, preservation, and no-push requirements. Prompt 2 corrected an implemented behavior: GO should enlarge the teal sheet's drag hitbox. Prompt 3 explicitly requested a commit. |
| Techniques deliberately used | [Student reflection needed]. Context/constraints and iterative correction are observable; deliberate technique selection was not stated. |
| Iterations | Three visible task prompts are preserved. The original movement implementation and corrected shared-sheet interaction are grouped as one challenge; internal edit/test iteration count [Not documented]. |
| Debugging / refinement | Agent replaced independent GO positioning with the existing anchored preferences-sheet drag state and retained separate button controls. Map tap uses a candidate origin until “Start here”; cancelling keeps the previous origin. Manual student edits are [Not documented]. |
| Failed / rejected approaches | User rejected GO moving independently from the teal sheet. Commit `cabce97` removed the separate movement helper. Saved focused-test output records one failing resize/reachability test before later successful checks; the initial failure is retained in the decision record. |
| Strategy changes | Shift from a floating movable control to a larger hitbox for the existing sheet, following the user's clarification. Confirmation behavior remained part of the issue scope. |
| Verification | Agent-run checks: later movement, final, staged, and compile logs end in `BUILD SUCCESSFUL`; final log includes JVM/Android host tests, Android debug assembly, JavaScript and Wasm compilation. Current documentation work inspected historical logs and Git only. Independent student/device verification [Not documented]. |
| Code impact | `0400f6e` initially changed seven files; `cabce97` changed three files and removed the independent movement helper. These are Git file counts, not a percentage of assignment code. Student percentage estimate [Not documented]. |
| Time | Student-estimated active interaction/debugging time [Not documented]. Build durations and commit timestamps are not substituted. |
| Outcome | Both commits are in the current branch history. Final GO behavior is the shared settings-sheet handle, with map-origin confirmation retained. Unrelated debug work preserved; no push made by this conversation. |
| Productivity / learning reflection | [Student reflection needed]: usefulness of the first interpretation, the reason for the corrected interaction, time saved or wasted, and what was learned about gesture hitboxes and confirmation state. |

## M4-A3 - Figma arrival, return, and round-trip flow

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git records Artem Sova as author of the four feature commits.

**Date:** Logged 2026-11-22 (America/Toronto); same local date as the commits.

**Conversation(s):** Current visible Codex conversation and retained
implementation record; [Conversation link needed].

**Decision record / full prompts:** [M4-A3](ML4-AI-Decision-Log.md#m4-a3---figma-arrival-return-and-round-trip-flow).
ML4 placement and source-coverage limits are qualified there.

| Field | Record |
| --- | --- |
| Challenge addressed / context | Issues #92/#107: match the supplied Figma arrival/return flow, preserve the completed trip while comparing return routes, navigate freshly to the stored origin, and offer one-way/round-trip planning with separate feature commits. |
| AI(s) used | Codex with local repository/Gradle/Git, GitHub CLI, Figma design/motion tools, local image rendering, and a local PGlite database test runtime. Exact model version [Not documented]. No subagents used. These are agent actions, not student-run verification. |
| Observable prompting pattern | Prompt 1 supplies an external design, issue references, and a per-feature commit rule. Prompt 2 states the expected user journey in concrete UI terms and requests a chat response. Prompt 3 requests AI logging; prompts 4 and 5 authorize the log commit and branch push. |
| Techniques deliberately used | [Student reflection needed]. Design grounding, constrained feature implementation, and follow-up behavior checking are observable patterns; intentional technique names/purpose have not been supplied by the student. |
| Iterations | Five visible prompts are preserved. The implementation produced four feature commits; commit count is not prompt/response iteration count. Internal iteration count [Not documented]. |
| Debugging / refinement | Agent inspected design/motion and reused Figma assets/theme tokens; fixed export transparency and map cropping; separated the trip-type band after it shifted an existing coordinate test. Added tests for asymmetric fresh routing, cancellation races, preserved outbound records, metadata restoration, and UI navigation. Mixed-file changes were staged selectively to preserve concurrent work. Manual student debugging [Not documented]. |
| Failed / rejected approaches | Background-bearing icon renders and map cropping were revised. An existing coordinate test failed after a new row shifted its target, then passed after the layout refinement. GitHub GraphQL authentication failed; REST retrieval via GitHub CLI was used. No explicit student rejection of the resulting return implementation is documented. |
| Strategy changes | Work was split into routing, Figma arrival UI, confirmed return/storage, and round-trip planning commits. Prompt 2 led to read-only code checking and a chat explanation, rather than a new implementation. Documentation groups these related changes under one significant design/coding challenge. |
| Verification | Historical handoff reports 50 backend + 298 JVM + 239 Android host executions = 587, zero failures/errors/skips; some common tests run on multiple platforms. Saved final log confirms the successful test/build/compile command for backend, shared, Android, desktop, JS, and Wasm. Retained implementation evidence reports local migration/RLS/grant/rollback checks and Compose screenshot/interaction inspection. No tests rerun for this log; student-run testing [Not documented]. |
| Code impact | Four commits: `3d5fb18` routing, `6bbe570` arrival UI, `bee21b9` confirmed return and metadata, `2bc0ed2` round-trip planning. They include Kotlin, Figma-derived PNG resources, SQL migration/tests, and deployment notes. Student-estimated assignment-code percentage and basis [Not documented]. |
| Time | Student-estimated active interaction/debugging time [Not documented]. Local build durations and commit times are not estimates of total work. |
| Outcome | Current source has confetti/statistics, the Fastest/Same transport split button, review below it, return preview/start, failure/retry/cancel, and a small back-at-start Review/Skip sheet; Skip goes to the planner. A planned loop routes both directions freshly within its total time budget. Separate commits preserved unrelated work. Nothing had been pushed at the implementation/logging handoff; prompt 5 later authorizes publishing the current branch and its committed history, with the operation's outcome reported in chat. Backend/app rollout and the database migration remain deployment steps. |
| Productivity / learning reflection | [Student reflection needed]: usefulness of Figma grounding, trust in route comparisons, independent evaluation of the UI, time saved or wasted, and learning about cancellation, routing budgets, or durable journey metadata. |

## M4-A4 - Codebase maintainability cleanup, composable splitting, and documentation refresh (#97)

**Contributor:** Requester; individual prompt authorship not independently
confirmed. Git records **Artem Sova** as author of commits `2aac1bb` and
`67b3a36`.

**Date:** 2026-11-25 (America/Toronto).

**Conversation(s):** Local Antigravity conversation
`0e723272-e84d-42bb-b32d-e91731ad011e` (app-only local ID); [Conversation link needed].

**Decision record / full prompts:** [M4-A4](ML4-AI-Decision-Log.md#m4-a4---codebase-maintainability-cleanup-composable-splitting-and-documentation-refresh-97).

| Field | Record |
| --- | --- |
| Challenge addressed / context | ML4-09 / issue #97: split multi-composable files into focused files, extract long functions across shared UI and backend code, enforce `docs/HowToWriteCode_InTheProject.md` (MVVM boundaries, KDoc, tuning sections, zero `!!` in `commonMain`), refresh outdated repository docs, and open a clean PR against `main` without disturbing concurrent local WIP. |
| AI(s) used | Google Antigravity (`Antigravity` agentic assistant) with local workspace read/edit/command tools, a temporary Python audit script, Gradle, Git/worktrees, and GitHub CLI (`gh`). Exact model version [Not documented]. No subagents used (`0` invocations). |
| Observable prompting pattern | Prompt 1 gated edits behind confirming doc/file access and supplied repo/issue URLs. Prompt 2 authorized the issue #97 refactor plus an audit of outdated docs with proposed fixes. Prompt 3 selected specific doc/housekeeping items, requested a chat-only explanation of remaining KDoc gaps, and required committing the code refactor separately beforehand. Prompts 4–6 directed committing/opening the PR, updating the header with `[AI-Cleanup]` and closing #97, and adding this AI log. |
| Techniques deliberately used | [Student reflection needed]. Read-first gating, guideline-grounded refactoring, staged commit separation, and PR metadata verification are observable patterns; intentional technique names and purpose have not been supplied by the student. |
| Iterations | Six visible human task prompts (plus one `/compact` UI command between Prompts 3 and 4) are preserved in the decision log. Related file splits, function extractions, compile fixes, doc refreshes, and PR updates are grouped as one maintainability challenge; internal tool-call iteration count [Not documented]. |
| Debugging / refinement | Agent fixed three compile/warning issues during refactoring (`ReviewV3.kt` parameter names `onRatingChange`/`label`, redundant null check in `TripReturnFlowContent.kt`, and `LocationUpdate.Fix` in `AndroidTransitMap.kt`). Because `fixes_fro_monday_23_nov_sova` was already squash-merged into `origin/main` in PR #111, the agent cherry-picked both commits onto a fresh branch from `origin/main` in a temporary worktree, resolved two small overlaps (`Generation.kt` and `TransitOpeningGeneration.kt`), copied gitignored `local.properties` after the initial worktree Gradle run failed, removed a broken link to untracked `docs/AndroidDistribution.md` in `README.md`, and unescaped `` `Closes #97` `` in PR #113 so GitHub linked `#97`. Manual student edits are [Not documented]. |
| Failed / rejected approaches | Initial worktree Gradle verification failed when `local.properties` (SDK path) was missing and succeeded once copied. Wrapping `` `Closes #97` `` in backticks prevented GitHub from populating `closingIssuesReferences` until replaced with plain `Closes #97`. Windows `git worktree remove` hit `Filename too long` on Gradle build outputs, so the temporary directory was cleaned up via Python with extended-path prefix `\\?\`. Refactored `App.kt` and `HistoryScreen.kt` were kept out of the PR commits to avoid colliding with pre-existing uncommitted `AchievementLauncher` changes. |
| Strategy changes | Work was sequenced into (1) read-only verification, (2) codebase refactor + doc audit, (3) separate code-cleanup commit before housekeeping/doc fixes, and (4) an isolated worktree branch off `origin/main` instead of pushing the already-merged `fixes_fro_monday_23_nov_sova` branch. The 5-class KDoc gap (`AccountRepository`, `LocationJumpFilter`, `CloudTrips`, `TripNavigation`, `TripsStore`) was explained in chat for a future docs-only PR rather than folded into #97. |
| Verification | Agent-run verification on `refactor/97-code-cleanup-and-docs` (`67b3a36`, based on `origin/main` `610ea1f`): `.\gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :backend:test :androidApp:assembleDebug :desktopApp:classes :shared:compileKotlinJs :shared:compileKotlinWasmJs --console=plain` ended `BUILD SUCCESSFUL` (`340` JVM tests, `276` Android host tests, `65` backend tests with `0` failures/errors and `1` skipped opt-in live smoke test; Android debug APK, Desktop, JS, and WasmJS targets built). `gh pr view 113 --json title,closingIssuesReferences` verified `[AI-Cleanup]` title and `#97` closing link. Tests were not rerun for this AI log commit; independent student testing is [Not documented]. |
| Code impact | `2aac1bb`: 66 files changed (+9381 / -6760), including 35 new focused Kotlin/Compose files and 3 deleted monolithic files (`TripScreensV3.kt`, `PlannerOriginUi.kt`, `LocationAccuracyControls.kt`), plus `commonMain` `!!` reduced `6 → 0`. `67b3a36`: 34 files changed (+253 / -662), removing 28 unused legacy `app/` template files and updating `.gitignore`, `README.md`, and 4 `docs/` files. Student-estimated assignment-code percentage and basis [Not documented]. |
| Time | Student-estimated active interaction and debugging time [Not documented]. Transcript and commit timestamps are not estimates of total student work time. |
| Outcome | PR [#113](https://github.com/NewWaveOwl/RNDTransitMTL/pull/113) (`[AI-Cleanup] Refactor composables, extract long functions, and refresh docs (#97)`) is open on `refactor/97-code-cleanup-and-docs` targeting `main` with `Closes #97` verified via GitHub API, plus this ML4 AI log commit. Pre-existing local WIP remained intact. PR review, merge, and issue #97 auto-close remain pending. |
| Productivity / learning reflection | [Student reflection needed]: usefulness of AI-assisted multi-file refactoring and doc auditing, trust in automated splits, time saved or wasted, and what the student would change next time. |
