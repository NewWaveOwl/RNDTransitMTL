# AI Technique Log — TravelTracker shared backend

## A1 — Provider selection and shared integration

**Date:** 2026-11-22.
**Contributor:** NewWaveOwl requesting account; personal attribution [Not documented].
**Conversation:** [Conversation link needed].
**Full prompts/decisions:** [A1 decision record](TravelTracker-Addresses-AI-Decision-Log.md#a1--public-nominatim-and-shared-windows-backend).

| Field | Evidence / student record |
| --- | --- |
| Challenge | Add editable map addresses and route previews without another local address database or duplicated OTP process; package safe Windows updates. |
| AI used | Codex primary agent, frontend and launcher agents, read-only review agent. Exact model variants [Not documented]. |
| Observable prompting | Supplied repositories/paths; requested docs discovery before implementation; asked for alternatives and weight comparison; selected public service and specified a spinner. |
| Deliberate technique names and reasons | [Student reflection needed]. Observable prompt patterns do not establish deliberate technique choice. |
| Iterations | Five relevant human prompts are preserved in the linked record; exact total internal implementation/review iterations [Not documented]. |
| Debugging/refinement | AI-authored fixture tests/review fixed transport follow-ups, Auth admission, settings validation and marker edit-buffer retention. The student then ran the updater on the server and supplied its actual PowerShell error; Codex reproduced the -File default-path difference and added standalone process tests. Successful deployment is not documented. |
| Rejected approaches | Photon was initially proposed, then superseded by the owner's Public Nominatim choice. No Photon installation was attempted. Pelias/Nominatim self-hosting were compared, not installed. |
| Strategy changes | Reused one backend/tunnel; explicit Search instead of autocomplete; in-memory cache and a shared limiter; update JARs with hashes/backups instead of distributing another OTP graph/runtime. |
| Verification | AI ran builds and fixture tests and inspected launcher screenshots; see exact evidence in the decision/implementation records. Student's own acceptance test [Not documented]. |
| Code impact | Student-estimated percentage and basis [Not documented]. |
| Time | Student-estimated active interaction time [Not documented]. |
| Outcome | Working-tree implementation and transferable tested update ZIP; source uncommitted and real server untouched. |
| Productivity / learning | [Student reflection needed]. |

## A2 - Session diagnosis and routing feature delivery

**Date:** 2026-11-22. **Contributor:** NewWaveOwl requesting account.
**Conversation:** [Conversation link needed].
**Full prompt:** A2 in the companion decision log.

| Field | Evidence / student record |
| --- | --- |
| Challenge | A signed-in traveller receives a false sign-in prompt; implement six linked routing/address issues with separate commits. |
| AI used | Codex primary agent, frontend/backend layer agents and read-only reviewer. |
| Observable prompting | User supplied the exact symptom, six issue links, separate-commit and server-update requirements. |
| Deliberate techniques | [Student reflection needed]. |
| Debugging | AI traced downstream401 to global session revocation and found missing/project-mismatched backend Auth returns the wrong error class; added focused regressions. |
| Strategy | Separate foundational integration from auth fix and dependency-ordered feature commits; use existing Montreal OSM/GTFS assets for real OTP checks. |
| Verification | A2 records 1,756 ordinary .NET tests, 132 backend, 60 launcher, eight updater checks, five real modes/11 replayed alternatives and 31 desktop/phone browser checks. Browser Auth/providers were simulated; remote deployment remains unverified. |
| Outcome | Auth fix and six requested features are committed locally, with a narrow-screen CSS follow-up after real pointer clicks exposed an overlay defect. A verified 34-JAR update ZIP records the runtime source commit and checksum. |
| Iterations / time / code impact | [Not documented]; student estimates required. |
| Productivity / learning | [Student reflection needed]. |

## A3 - Distinguish schema, validation and remote Auth failures

**Date:** 2026-11-23. **Contributor:** NewWaveOwl requesting account.
**Conversation:** [Conversation link needed].
**Full prompts:** A3 in the companion decision log.

| Field | Evidence / student record |
| --- | --- |
| Challenge | Similar 503 UI failures came from absent Users/Trips tables and an incompatible routing Auth project, while 413 was a separate image-size rejection. |
| AI used | Codex primary agent plus existing frontend, backend and read-only review agents. |
| Observable prompting | User supplied actual logs, added a save failure, confirmed update/tunnel state and requested verified PR/branch publication. |
| Deliberate techniques | [Student reflection needed]. |
| Debugging | Read database logs before proposing schema changes; inspect committed migrations; compare anonymous health with one invalid-credential issuer probe; leave real account tokens and providers untouched. |
| Strategy | Apply existing migrations through the operator launcher; fix frontend feedback and early dimensions rather than increasing deadlines or changing routing algorithms without evidence. |
| Verification | A3 records 1,781 application/727 frontend tests, 55 Node cases, fresh 132 backend/60 launcher cases and five-mode/11-alternative offline OTP replay. |
| Outcome | Two focused frontend fixes committed; operator recovery documented; Kotlin runtime unchanged. User requested TravelTracker PR and separate backend branch push. No human review or completed live repair is invented. |
| Iterations / time / code impact | [Not documented]; student estimates required. |
| Productivity / learning | [Student reflection needed]. |
