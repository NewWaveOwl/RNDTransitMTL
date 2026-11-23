# AI Technique Log - ML4

See the [decision log](ML4-AI-Decision-Log.md) for the session index, full
available prompts, student decisions, commit evidence, and source limitations.
This log groups the related review, recovery implementation, and debugging into
one coding challenge. Personal judgments remain with the student.

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
