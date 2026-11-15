# AI Technique Log - ML3

See the [decision log](ML3-AI-Decision-Log.md) for full prompts and source coverage. Shared-session dates and exact models are [Not documented]; 2026-11-01 is the retrieval date. Related UI iterations are grouped into one challenge.

M3-A13 and M3-A14 cover a separate current Codex conversation logged on 2026-11-14. Its three available human prompts are in the [PR 75 archive](ML3-PR75-Review-Fixes-Chat-Prompts.md); its verified conversation URL remains [Conversation link needed].

## M3-A1 - PR 53 review and Android saved-state explanations

**Conversations:** [Shared chat](https://chatgpt.com/s/cx_6ae7b432fc4081919e59a08ce2eaf35d)

**Decision record / full prompts:** [M3-A1](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | PR 53 review and Android saved-state explanations |
| Contributor | Requester; Artiom attribution supported by branch/planning context, not independently confirmed for every share. |
| AI(s) used | ChatGPT shared sessions; Codex repository assistance described in outputs. Exact models [Not documented]. |
| Observable prompting pattern | Project links/context, follow-up clarification and constraints; these observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | 4 visible human prompts in this record; duplicate share not counted twice. |
| Debugging / refinement | AI reported a potentially merge-blocking History lazy-list key using boxed TripId and recommended it.id.value. Explained Android Bundle compatibility in German, then English, and explained boxing. The response explicitly described source review without build/device tests. |
| Failed / rejected approaches | M3-A5: user rejected the initial placement and requested closer Figma alignment. M3-A3: AI withdrew the legacy-trip blocker after user clarification. Other rejection history: [Not documented]. |
| Strategy changes | User requested English and further explanation. Adoption of the suggested fix: [Not documented]. |
| Verification | Historical source-review and test limitations are recorded in the decision log. No fresh application test results established here. |
| Code impact | Student-estimated percentage [Not documented]. Proposals are not counted as implemented code. |
| Time | Student-estimated active AI time [Not documented]. |
| Outcome | User requested English and further explanation. Adoption of the suggested fix: [Not documented]. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A2 - TripRepository contract alternatives

**Conversations:** [Shared chat](https://chatgpt.com/s/cx_6ae7b43e5c248191bbd063a55e6e6b6d)

**Decision record / full prompts:** [M3-A2](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | TripRepository contract alternatives |
| Contributor | Requester; Artiom attribution supported by branch/planning context, not independently confirmed for every share. |
| AI(s) used | ChatGPT shared sessions; Codex repository assistance described in outputs. Exact models [Not documented]. |
| Observable prompting pattern | Project links/context, follow-up clarification and constraints; these observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | 2 visible human prompts in this record; duplicate share not counted twice. |
| Debugging / refinement | Proposed simple, reactive, and feedback-bundle contracts; recommended the reactive version. Follow-up compared the design document and called for owner-scoped access, independent optional stars/text, explicit section/place feedback, separate review storage, preserved history and trip status. AI said issue retrieval and direct PDF verification were incomplete. |
| Failed / rejected approaches | M3-A5: user rejected the initial placement and requested closer Figma alignment. M3-A3: AI withdrew the legacy-trip blocker after user clarification. Other rejection history: [Not documented]. |
| Strategy changes | User requested alignment with project plans. Final contract selection or implementation: [Not documented]. |
| Verification | Historical source-review and test limitations are recorded in the decision log. No fresh application test results established here. |
| Code impact | Student-estimated percentage [Not documented]. Proposals are not counted as implemented code. |
| Time | Student-estimated active AI time [Not documented]. |
| Outcome | User requested alignment with project plans. Final contract selection or implementation: [Not documented]. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A3 - Artiom branch cleanup before merge

**Conversations:** [Shared chat](https://chatgpt.com/s/cx_6ae7b453343081918d8421325681107e); [Shared chat](https://chatgpt.com/s/cx_6ae7b462ac2081919d7b6bd2d4cfa016)

**Decision record / full prompts:** [M3-A3](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | Artiom branch cleanup before merge |
| Contributor | Requester; Artiom attribution supported by branch/planning context, not independently confirmed for every share. |
| AI(s) used | ChatGPT shared sessions; Codex repository assistance described in outputs. Exact models [Not documented]. |
| Observable prompting pattern | Project links/context, follow-up clarification and constraints; these observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | 4 visible human prompts in this record; duplicate share not counted twice. |
| Debugging / refinement | AI reported deleting six arithmetic starter tests, removing unused/redundant elements, reusing the simulation constant, and retaining meaningful tests. Reported inspecting 102 changed files, XML/SVG parsing, PNG headers and whitespace. Initially flagged old zero-minute saved trips; after the user said none exist, withdrew that compatibility blocker. Gradle failed before compilation with a loopback-connection error; changes were reported uncommitted. |
| Failed / rejected approaches | M3-A5: user rejected the initial placement and requested closer Figma alignment. M3-A3: AI withdrew the legacy-trip blocker after user clarification. Other rejection history: [Not documented]. |
| Strategy changes | User explicitly stated there are no old trips and requested a commit message. A merge, passing tests, and subsequent acceptance are not established. Both supplied shares display the same visible conversation and are counted once. |
| Verification | Historical source-review and test limitations are recorded in the decision log. No fresh application test results established here. |
| Code impact | Student-estimated percentage [Not documented]. Proposals are not counted as implemented code. |
| Time | Student-estimated active AI time [Not documented]. |
| Outcome | User explicitly stated there are no old trips and requested a commit message. A merge, passing tests, and subsequent acceptance are not established. Both supplied shares display the same visible conversation and are counted once. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A4 - PR 52 review and saved-feedback visibility

**Conversations:** [Shared chat](https://chatgpt.com/s/cx_6ae7b468dce88191ab264db7cf8ae4df)

**Decision record / full prompts:** [M3-A4](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | PR 52 review and saved-feedback visibility |
| Contributor | Requester; Artiom attribution supported by branch/planning context, not independently confirmed for every share. |
| AI(s) used | ChatGPT shared sessions; Codex repository assistance described in outputs. Exact models [Not documented]. |
| Observable prompting pattern | Project links/context, follow-up clarification and constraints; these observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | 2 visible human prompts in this record; duplicate share not counted twice. |
| Debugging / refinement | AI reported two P2 regressions at reviewed commit 1aef17d: TripDetailsV3 bypassed review editing and omitted Quality/Interesting/Fun/photo feedback. Explained that hidden ratings were still saved. JVM tests could not run because of the Gradle loopback failure. |
| Failed / rejected approaches | M3-A5: user rejected the initial placement and requested closer Figma alignment. M3-A3: AI withdrew the legacy-trip blocker after user clarification. Other rejection history: [Not documented]. |
| Strategy changes | User sought clarification. Fix adoption and student verification: [Not documented]. |
| Verification | Historical source-review and test limitations are recorded in the decision log. No fresh application test results established here. |
| Code impact | Student-estimated percentage [Not documented]. Proposals are not counted as implemented code. |
| Time | Student-estimated active AI time [Not documented]. |
| Outcome | User sought clarification. Fix adoption and student verification: [Not documented]. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A5 - ML3 Figma alignment, trip screens and issue 30 planning

**Conversations:** [Shared chat](https://chatgpt.com/s/cx_6ae7b476302081919f02267cac33adfb)

**Decision record / full prompts:** [M3-A5](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | ML3 Figma alignment, trip screens and issue 30 planning |
| Contributor | Requester; Artiom attribution supported by branch/planning context, not independently confirmed for every share. |
| AI(s) used | ChatGPT shared sessions; Codex repository assistance described in outputs. Exact models [Not documented]. |
| Observable prompting pattern | Project links/context, follow-up clarification and constraints; these observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | 16 visible human prompts in this record; duplicate share not counted twice. |
| Debugging / refinement | Proposed Auto layout placement for the pace label, bold GO text, label/value fields, loading/preview, active-map/error/cancel states, and complete/review/feedback/history/details UI. Compared issue 30 with reported local code, suggested History delete confirmation and a five-minute minimum, and reported those fixes present at 1a0a07d (source inspection, not new interaction tests). Proposed issue 32 manual origin/recenter and issue 33 failure fixtures, cancellation guards, Back handling, confirmations, previews and tests. Follow-up corrected design mismatches after the user required alignment with their Figma. The final code was explicitly proposed and uncompiled; real routing/GPS/cancelled-history storage remained separate integrations. |
| Failed / rejected approaches | M3-A5: user rejected the initial placement and requested closer Figma alignment. M3-A3: AI withdrew the legacy-trip blocker after user clarification. Other rejection history: [Not documented]. |
| Strategy changes | User corrected placement, clarified that GO bolding concerned Kotlin rather than Figma generation code, requested code in chat, and insisted on the supplied design. Subsequent manual application and final visual acceptance: [Not documented]. |
| Verification | Historical source-review and test limitations are recorded in the decision log. No fresh application test results established here. |
| Code impact | Student-estimated percentage [Not documented]. Proposals are not counted as implemented code. |
| Time | Student-estimated active AI time [Not documented]. |
| Outcome | User corrected placement, clarified that GO bolding concerned Kotlin rather than Figma generation code, requested code in chat, and insisted on the supplied design. Subsequent manual application and final visual acceptance: [Not documented]. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A6 - Documentation organization

**Date:** 2026-11-01. **Contributor:** Requester. **AI used:** Codex desktop, no subagents. **Conversation:** [Conversation link needed]. **Full prompt:** [M3-A6](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | Organize logs by milestone and document ML3 use from six supplied shares. |
| Observable pattern | User supplied paths, logging reference, source links and an explicit no-commit/no-push constraint. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | One visible task request; repository inspection, evidence retrieval, drafting and checks. |
| Debugging / refinement | Web reader failed; browser successfully retrieved the conversations. Duplicate shares grouped. |
| Failed / rejected approaches | Web-reader cache misses; no student-rejected documentation recorded. |
| Strategy changes | Browser fallback and milestone-specific folders with a central index. |
| Verification | Local documentation/path/whitespace checks; no application testing for documentation edits. |
| Code impact | Documentation only; student-estimated percentage [Not documented]. |
| Time | [Not documented]. |
| Outcome | Milestone logs relocated, ML3 logs and guide updated; initially left uncommitted, then committed on a documentation branch after explicit follow-up authorization. See M3-A6 follow-up in the decision log. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A7 - Issue #29 gap analysis

**Date:** 2026-11-02. **Contributor:** Caio Nunes. **AI used:** Claude Code CLI (Claude Opus 5.5), no subagents. **Conversation:** [Conversation link needed]. **Full prompts:** [M3-A7](ML3-AI-Decision-Log.md).

| Field | Record |
| --- | --- |
| Challenge / context | Sync the branch with main, then find which parts of issue #29 (accounts, settings, group data definitions) remain unimplemented. |
| Observable pattern | User supplied the issue link and asked for a gap list before implementation; also asked for logging per `docs/HowToLog_AI.md`. These observations do not prove deliberate technique selection. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Twenty-six visible prompts so far. |
| Debugging / refinement | `gh` CLI unavailable; AI used the public GitHub REST API instead. |
| Failed / rejected approaches | AI first assumed issue #27 was the typed-ID work; corrected after the user asked about #27 and the issue was read. |
| Strategy changes | None recorded. |
| Verification | Merge checked with `git status` / `git log`; gap analysis by source review; calibration by Python calculation; implementation by `:shared:jvmTest` (110 passed) and Android compile. One AI-written test assertion had the Mercator direction backwards and was corrected. |
| Code impact | New geo, trip and DTO source files plus test files in `shared`; `Trip`, `TripsStore`, generator, map screens and 14 existing test files migrated. Student-estimated percentage [Not documented]. |
| Time | [Not documented]. |
| Outcome | Branch merged with main; #29 and #27 gaps identified; PNG map calibrated from three student-supplied landmarks; `GeoPoint`, `GeoBounds`, `MapProjection` and sample-map calibration implemented; trip models (`Place`, `RouteLeg` â€” first named `RouteSegment`, `TripStatus`, `TripRequest`) and backend DTOs added; 22 new JVM tests passing; `Trip` migrated to places, legs, status and owner with 134 JVM tests passing; `TripDto` added (137 tests) and the trip flow verified in the running desktop app. |
| Productivity / learning reflection | [Student reflection needed]. |

### M3-A8 — Iterative backend integration and verification

**Challenge:** Replace fixed OTP demonstrations with request-driven generation while preserving confirmed origins, routed geometry, failure distinctions and the existing trip lifecycle.

**Approach:** Inspected the branch and official STM/exo/OTP documentation; split work into the six user-approved stages with separate commits. Used seeded candidates, controlled provider responses, HTTP request streams, captured bridge regression fixtures and headless Compose interactions.

**Iteration:** Found that agency GTFS zone IDs do not directly represent ARTM A/B/C/D. Added official polygon loading and checked intermediate transit geometry. Retained the bridge's `excludeRealtime` contract and scoped alerts. Corrected an incorrectly encoded test polyline and restored input validation before backend availability checks.

**Environment:** Standard Gradle execution could not write its cache or connect to daemon/test-worker loopback sockets. Used a writable copied dependency cache and an in-process Gradle launch for compilation/lint, then ran compiled JUnit tests directly. Set a writable Skiko data directory for headless UI tests. These are verification-environment adjustments, not app features.

**Use of output:** Implementation accepted for validation under the user's selected architecture. The final test results and remaining live-device checks are recorded in `docs/BackendTripGeneration.md`.

**Actual interaction time / AI percentage:** Not measured; no estimate claimed. Conversation reference: this issue #67 integration conversation.

### M3-A8 follow-up - User correction of the generation algorithm

**Iteration:** The user rejected stop-only candidates and specified arbitrary points,
fixed walking paces and a distance-based orientation reserve. Updated coordinate
sampling first, then backend timing/OTP preferences and shared trip persistence.
Updated the design and integration guide to describe the changed behavior and the
remaining scoring features honestly.

**Verification:** Seeded geographic/park fixtures, controlled OTP responses at the
time-budget boundary, mixed walking/transit distances, pace conversion checks and
shared DTO restoration. Official OTP docs confirm walking speed is in metres/second
and describes flat-ground maximum speed. Reused the existing in-process compilation
and direct JUnit workflow; no subagents or live deployment. Final results are in
`docs/BackendTripGeneration.md`; time and AI percentage were not measured.

### M3-A8 follow-up - Replacing the server runtime

Ported the generation policy and HTTP boundary to Kotlin/Ktor while retaining the
existing phone contract and tested GraphQL query. Used controlled routes, seeded
coordinates, zone holes/crossings, cancellation, timeout and concurrency fixtures
to validate the replacement. Removed the direct OTP demo client and the associated
fixed-position tests while retaining tests of the real backend and feed bridge.
Updated CI from Node to Kotlin tests. Used existing writable caches, in-process
Gradle and direct JUnit execution because normal worker sockets remain restricted.
A stale demo reference in the decoder and a locally unescaped SDK path were caught
and corrected during compilation/lint. No subagents were used.

The independent server build also passed. Its offline Gradle plugin classpath used
a validation-only annotation dependency substitution (cached 2.28.0 instead of
2.27.0); this was confined to the temporary init script, not committed application
dependencies. Live device/tunnel checks remain unverified.

## M3-A9 - Portable Windows server and public access

**Contributor:** Requester; individual attribution [Not documented]. **Logged:** 2026-11-14; individual session dates [Not documented].
**Conversation:** [Current shared chat](https://chatgpt.com/share/6af7f15e-aa4c-83ea-b62e-5c7ed9db2532).
**Decision record:** [M3-A9](ML3-AI-Decision-Log.md#m3-a9---portable-windows-server-and-public-access). **Available prompts:** [P01-P24](ML3-Server-Realtime-Chat-Prompts.md#p01), [P27-P39](ML3-Server-Realtime-Chat-Prompts.md#p27); source/privacy limits are in the archive.

| Field | Student record |
| --- | --- |
| Challenge addressed / context | Run the complete backend on a second Windows PC and expose the API to Android through launcher-managed public access. |
| AI(s) used | ChatGPT/Codex; exact model and use of additional AIs [Not documented]. |
| Observable prompting pattern | Repeated clarification of folders, packaging and Git; real configuration/console/screenshot examples; comparison of tunnel options; follow-up request to integrate the selected option. |
| Techniques deliberately used | [Student reflection needed]; observable patterns do not establish intentional technique selection. |
| Iterations | Several setup/debugging exchanges are preserved in order; exact work-iteration count [Not documented]. |
| Debugging / refinement | The user supplied startup logs, CLI installation/PATH failure and tunnel connection-refusal screenshots, then confirmed working operation. AI guided configuration and reported a Windows classpath fix. |
| Failed / rejected approaches | Newly installed CLI was not recognized in the existing shell. Other tunnel/hosting options were compared; they are not all failed experiments. |
| Strategy changes | Moved from manual Cloudflare/Microsoft commands toward a portable launcher with integrated tunnel settings after the user's explicit request. |
| Verification | User screenshots and "works" support server/manual tunnel operation. AI reported unit, GUI and package checks; these historical reports were not rerun for the log. Use of the integrated Microsoft controls on the second PC [Not documented]. |
| Code impact | Launcher, packaging scripts, configuration and run guides; commits c8ecf8f, 7dd5706, 2c66fb3, 43bbe64 and 1461fd4. Student-estimated percentage [Not documented]. |
| Time | Active AI interaction/debugging time [Not documented]. |
| Outcome | Portable server distribution and Microsoft Dev Tunnels controls implemented; server readiness remains separate from route generation success. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A10 - Realtime feed visibility and exo bus integration

**Contributor:** Requester; individual attribution [Not documented]. **Logged:** 2026-11-14; individual session dates [Not documented].
**Conversation:** [Current shared chat](https://chatgpt.com/share/6af7f15e-aa4c-83ea-b62e-5c7ed9db2532).
**Decision record:** [M3-A10](ML3-AI-Decision-Log.md#m3-a10---realtime-feed-visibility-and-exo-bus-integration). **Available prompts:** [P40-P45](ML3-Server-Realtime-Chat-Prompts.md#p40), including the Chrono screenshot context.

| Field | Student record |
| --- | --- |
| Challenge addressed / context | Show per-feed freshness/credentials and extend existing STM/exo train integration to exo bus sectors. |
| AI(s) used | ChatGPT/Codex; exact model and additional AI use [Not documented]. |
| Observable prompting pattern | Asked what already exists, supplied an API screenshot, requested a specific panel, then requested bus implementation and commits. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Related panel and bus requests grouped as one challenge; exact engineering iteration count [Not documented]. |
| Debugging / refinement | Kept process readiness separate from agency health; matched feed entities to sector schedule IDs, trip/route pairs and graph inputs. AI reported regression fixtures and preparation/package checks. |
| Failed / rejected approaches | Live download/network limits prevented a complete live bus demonstration. Screenshot/API shape alone was insufficient evidence of working bus access. |
| Strategy changes | Extended train-only exo handling with separate bus namespaces, credentials and prepared graph inputs; added an explicit health panel instead of relying on Bridge Ready. |
| Verification | AI reported launcher/backend tests, bus/STM/train fixtures, Windows graph-preparation checks and GUI/package checks. Restricted Gradle cache/sockets required alternate compilation/test runners. User acceptance of an authenticated live bus trip [Not documented]; historical checks were not rerun for logging. |
| Code impact | Feed panel/credentials in 1461fd4; bus bridge, graph preparation, launcher support and generation health handling in 92b9efe. Percentage [Not documented]. |
| Time | [Not documented]. |
| Outcome | Source integration committed. Real bus graph preparation, authorized fresh feeds and Android end-to-end deployment remain unverified by this conversation. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A11 - Duration matching and generation loading proposal

**Contributor:** Requester; individual attribution [Not documented]. **Logged:** 2026-11-14; individual session dates [Not documented].
**Conversation:** [Current shared chat](https://chatgpt.com/share/6af7f15e-aa4c-83ea-b62e-5c7ed9db2532).
**Decision record:** [M3-A11](ML3-AI-Decision-Log.md#m3-a11---duration-matching-and-generation-loading-proposal). **Available prompts:** [P25-P26](ML3-Server-Realtime-Chat-Prompts.md#p25), [P46-P47](ML3-Server-Realtime-Chat-Prompts.md#p46), and [partial S01](ML3-Server-Realtime-Chat-Prompts.md#s01---additional-earlier-prompt-excerpt-visible-only-in-the-shared-rendering).

| Field | Student record |
| --- | --- |
| Challenge addressed / context | Failed 30/60-minute bus requests; prefer valid routes closer to the requested maximum while respecting selected alternative modes; improve loading feedback. |
| AI(s) used | ChatGPT/Codex; exact model [Not documented]. The next-chat prompt is a handoff draft, not evidence another AI executed it. |
| Observable prompting pattern | Concrete failure reports and desired behavior; correction of mode semantics; request for discussion first, then a complete implementation prompt for a new chat. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Failure clarification followed by design discussion and handoff request; exact engineering iterations [Not documented]. |
| Debugging / refinement | AI discussed first-valid search, disconnected geographic candidates and the search cap as possible causes. Exact cause of those failed requests was not established. |
| Failed / rejected approaches | User rejected forcing every selected mode. Existing arbitrary geographic destinations must remain; this is not authorization to revert to stop-only selection. |
| Strategy changes | Proposed retaining/comparing valid candidates, scaling search time by requested duration, and replacing the loading display with an animated bar and casual status text. |
| Verification | Suggested deadline, cancellation, duration, mode and UI-state checks are future work. No completed tests for this proposal are claimed. |
| Code impact | No implementation attributed to this chat; proposed source locations were part of the handoff. Code percentage not applicable to the unimplemented proposal. |
| Time | [Not documented]. No measured benefit from starting a new chat. |
| Outcome | Review-first proposal and future implementation prompt supplied. The 20-second base and optional 95% early exit are AI suggestions, not independently confirmed requirements. A later repository commit, 1aef8de, implements duration/loading work; its implementation conversation and checks are outside the supplied chat evidence. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A12 - Current conversation logging

**Contributor:** Requester; individual attribution [Not documented]. **Date:** 2026-11-14 logging update.
**Conversation:** [Current shared chat](https://chatgpt.com/share/6af7f15e-aa4c-83ea-b62e-5c7ed9db2532).
**Decision record:** [M3-A12](ML3-AI-Decision-Log.md#m3-a12---current-conversation-logging). **Available prompts:** [P48-P49](ML3-Server-Realtime-Chat-Prompts.md#p48).

| Field | Student record |
| --- | --- |
| Challenge addressed / context | Add this exact chat to the existing milestone decision and technique logs without executing its historical requests. |
| AI(s) used | ChatGPT/Codex; exact model [Not documented]. |
| Observable prompting pattern | Explicit logging request followed by the exact share URL identifying the current chat. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Two visible logging requests; this is a prompt count, not a measured development-iteration count. |
| Debugging / refinement | Web retrieval failed; browser verified the share and partially exposed its contents. Used available conversation text, preserved privacy omissions and separated implemented features from future suggestions. |
| Failed / rejected approaches | Web reader access failed. Partial browser rendering cannot establish complete raw transcript coverage. |
| Strategy changes | Stored available prompts once in a separate archive and cross-linked matching challenge IDs in both existing logs. |
| Verification | Checked documentation numbering, prompt references, fences, relative links and whitespace; preserved existing local gradle.properties edits. No app tests needed or rerun. |
| Code impact | Documentation only; application code percentage not applicable. |
| Time | [Not documented]. |
| Outcome | Four linked challenge records, session-index rows and an archive of 49 available human messages plus one labeled earlier excerpt; logging changes remain uncommitted and unpushed. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A13 - Independent PR 75 review and reproduction

**Contributor:** Requester; individual attribution [Not documented]. **Date:** 2026-11-14. **Conversation:** Current Codex chat; [Conversation link needed].
**Decision record / full prompts:** [M3-A13](ML3-AI-Decision-Log.md#m3-a13---independent-pr-75-review-and-reproduction); [P01](ML3-PR75-Review-Fixes-Chat-Prompts.md#p01---independent-pr-review).

| Field | Student record |
| --- | --- |
| Challenge addressed / context | Independently assess PR #75's generation, cancellation, OTP fallback, client restoration and server packaging without disturbing the running second-PC backend or local changes. |
| AI(s) used | Codex coding agent. Exact model version and use of another AI in this session [Not documented]. No subagents were used. |
| Observable prompting pattern | Detailed repository/revision context; explicit safety and scope constraints; risk-based verification instructions; requirement to reproduce actionable regressions and separate CI, local and live evidence. |
| Techniques deliberately used | [Student reflection needed]. Observable prompting does not prove a deliberate technique choice. |
| Iterations | One initial human review prompt in this challenge; many inspection/reproduction steps were agent-driven. Exact engineering-iteration count [Not documented]. |
| Debugging / refinement | Compared the PR with its merge base, traced backend-to-client behavior, and used isolated HTTP and saved-state fixtures to reproduce bugs not detected by the pre-existing passing tests. |
| Failed / rejected approaches | GitHub GraphQL authentication failed, so the agent used CLI REST reads. Original-worktree fetches hit metadata write restrictions; isolated Git storage was used. Missing SDK and external-cache permissions blocked Android/Web coverage. |
| Strategy changes | Continued independent local checks despite blocked build paths. Replaced unsafe live outage/cancellation experiments with fixtures. Skipped authenticated trip sampling because no valid existing account token was available. |
| Verification | Agent-run: 22 backend, 45 launcher and 182 shared JVM tests, Desktop compilation, STM/exo fixtures, 25 bus checks and graph-preparation/script checks passed. Separate live observations were one health 200 and two anonymous generation 401 responses; live build identity remained unknown. Student-run evaluation [Not documented]. |
| Code impact | Review phase changed no tracked application source. Four reproduced findings were supplied for a later decision; no implementation percentage is attributed to the review. |
| Time | Student-estimated active interaction/debugging time [Not documented]. |
| Outcome | Four actionable findings and a request-changes recommendation. No exact-head CI runs/checks/statuses were available, and current-main merge conflicts were confirmed. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A14 - PR 75 fixes, merge resolution and commit handoff

**Contributor:** Requester; individual attribution [Not documented]. **Date:** 2026-11-14. **Conversation:** Same current Codex chat; [Conversation link needed].
**Decision record / full prompts:** [M3-A14](ML3-AI-Decision-Log.md#m3-a14---pr-75-fixes-merge-resolution-and-commit-handoff); [P02-P03](ML3-PR75-Review-Fixes-Chat-Prompts.md#p02---remediation-and-git-authorization).

| Field | Student record |
| --- | --- |
| Challenge addressed / context | Fix the four verified regressions, add production-path regression coverage, commit the fixes, resolve current-main conflicts in another commit, and hand off the blocked push. The final follow-up requests AI logging and a log commit. |
| AI(s) used | Codex coding agent. Exact model version and additional AI use [Not documented]. No subagents were used. |
| Observable prompting pattern | Explicit follow-up changed the authorized scope from review-only to fixes/commits/push, then requested this chat be documented and a push command supplied. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Two available human follow-ups are recorded here; this prompt count is not an exact implementation/debugging-iteration count. |
| Debugging / refinement | Added real CIO disconnect coverage, empty-result bridge HTTP coverage, healthy-feed fallback filtering and Bus-only restoration through Trip/DTO serialization. Thread-dump evidence identified test waits in JVM entropy seeding; a test-process-only entropy source allowed a valid rerun. |
| Failed / rejected approaches | Initial shared test runs had two waiting-for-proposal failures before the entropy issue was identified. Original worktree Git metadata remained unwritable. Push/authentication attempts failed: credential-store persistence was unavailable, the CLI token was invalid and the helper supplied no credential. |
| Strategy changes | Used a clean isolated clone in build/pr75-fixes. Kept source fixes and current-main conflict resolution in separate commits. Retained the offline map-picker fix and database setup while keeping the new generation workflow. Supplied a manual push command after authentication blocked publication. |
| Verification | Agent-run merged Gradle build: 23 backend + 45 launcher + 183 shared JVM tests = 251 passing tests; Desktop compilation passed. Fresh bridge fixtures included 31 bus checks. Conflict-marker, whitespace and documentation-link checks passed. Student-run/manual device verification [Not documented]. |
| Code impact | Fix commit 03e640d changes 10 files, including regression tests. Merge commit dc80fe4 integrates main at 29af854. Student-estimated assignment-code percentage [Not documented]. This logging follow-up changes documentation only. |
| Time | Student-estimated active interaction time [Not documented]; build duration is not used as an estimate of student AI time. |
| Outcome | Four fixes and the resolved main merge exist as local commits. The existing gradle.properties modification and running server were preserved. A remote push or GitHub PR merge is not established. The current request adds linked log records and a prompt archive for a separate commit. |
| Productivity / learning reflection | [Student reflection needed]. |

## M3-A15 - PR 75 follow-up review and verification handoff

**Contributor:** Requester; Artiom / NewWaveOwl supported by repository context. **Logged:** 2026-11-14. **Conversation:** [Current chat](https://chatgpt.com/share/6af88f56-58f0-83ea-aa4b-739d9e77f32f).
**Decision/full prompts:** [M3-A15](ML3-AI-Decision-Log.md#m3-a15---pr-75-follow-up-review-and-verification-handoff), [archive](ML3-PR75-Followup-Chat-Prompts.md).

| Field | Student record |
| --- | --- |
| Challenge/context | Review PR #75 despite connectivity/cache restrictions; hand off to local CLI while preserving the running server. |
| AI(s) used | ChatGPT/Codex; exact model [Not documented]. Separate CLI execution is documented in M3-A13/A14. No subagents used here. |
| Observable pattern | Supplied repo/PR context, actual shell failures, server constraints, issue criteria and a device screenshot; requested progressively refined review prompts. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Multiple follow-ups preserved in order; exact engineering iteration count [Not documented]. |
| Debugging/refinement | GitHub CLI failed; browser verified PR scope; cache-lock error and PC disconnect interrupted review. CLI handoff avoided pretending blocked checks passed. |
| Failed/rejected approaches | Initial local review could not complete. codex command was not installed/on PATH; npx and installation guidance were supplied. Successful launch is not established. |
| Strategy changes | Shifted execution to PowerShell; retained sandbox with never approvals; prioritized bounded live checks and isolated outage fixtures. |
| Verification | Local source reads/search; user-reported #64 Android success plus visible proposal screenshot. Full acceptance and remote closure not independently established. |
| Code impact | Time-log-only commit 63a63ce; no source implementation from review handoff. Percentage [Not documented]. |
| Time | [Not documented]; student reflection/estimate needed. |
| Outcome | Review prompt and safe Git handoffs supplied; branch deletion deferred pending uniqueness checks. Push failed in this session. |
| Productivity/learning reflection | [Student reflection needed]. |

## M3-A16 - Spatial sampling and concurrent candidate discovery investigation

**Contributor:** Requester. **Logged:** 2026-11-14. **Conversation:** [Current chat](https://chatgpt.com/share/6af88f56-58f0-83ea-aa4b-739d9e77f32f).
**Decision/full prompts:** [M3-A16](ML3-AI-Decision-Log.md#m3-a16---spatial-sampling-and-concurrent-candidate-discovery-investigation), [P07-P10](ML3-PR75-Followup-Chat-Prompts.md#p07---existing-geohash-use).

| Field | Student record |
| --- | --- |
| Challenge/context | Explore reduced redundant OTP work for overlapping requests while preserving random, varied, reachable destinations. |
| AI(s) used | ChatGPT/Codex; exact model [Not documented]. No subagents used. |
| Observable pattern | Proposed geohash; asked whether it helps; selected benefits and challenged corridor diversity/fixed park endpoints before authorizing an issue. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Four visible relevant requests; not a measured implementation-iteration count. |
| Debugging/refinement | Distinguished spatial identifiers from distance and routing; compared proposed grids to existing proximity filtering. |
| Failed/rejected approaches | Fixed per-cell endpoints and assumed geohash performance benefits were not adopted. No algorithm was implemented or benchmarked. |
| Strategy changes | Reframed implementation idea as measured investigation; added per-request constraints, cache expiry, concurrency and diversity criteria. |
| Verification | Repository search for geohash; browser confirmed issue #78 and Artiom/NewWaveOwl assignment. |
| Code impact | Issue only; no application source changes. Percentage not applicable. |
| Time | [Not documented]. |
| Outcome | Investigation issue created; implementation choice deferred to evidence. |
| Productivity/learning reflection | [Student reflection needed]. |

## M3-A17 - Peer findings deployment simplification and App.kt merge guidance

**Contributor:** Requester. **Logged:** 2026-11-14. **Conversation:** [Current chat](https://chatgpt.com/share/6af88f56-58f0-83ea-aa4b-739d9e77f32f).
**Decision/full prompts:** [M3-A17](ML3-AI-Decision-Log.md#m3-a17---peer-findings-deployment-simplification-and-appkt-merge-guidance), [P15-P29](ML3-PR75-Followup-Chat-Prompts.md#p15---peer-review-findings).

| Field | Student record |
| --- | --- |
| Challenge/context | Validate peer findings; simplify deployment and repair text; reconcile generation and cloud-trip persistence conflicts. |
| AI(s) used | ChatGPT/Codex; peer review findings supplied by user, peer's AI use [Not documented]. No subagents used here. |
| Observable pattern | Supplied concrete findings, questioned unused technology, selected removal, requested commits, then iterated through code excerpts/screenshots and a full text attachment. |
| Techniques deliberately used | [Student reflection needed]. |
| Iterations | Related fixes and merge-guidance follow-ups grouped as one challenge; exact engineering iteration count [Not documented]. |
| Debugging/refinement | Confirmed missing launcher COPY and corrupted characters. Initial documentation replacement failed on line endings; adjusted matching and checked final diff. Initial conflict excerpt lacked full context; complete attachment was subsequently read. |
| Failed/rejected approaches | Docker repair was implemented temporarily then superseded by user-requested removal. Accepting only the feature side would discard tripRepository support; guidance preserved both current features. |
| Strategy changes | Supported Windows launcher instead of maintaining an unused Docker path; provided full merged code only after reading the attachment. |
| Verification | UTF-8/corruption checks, launcher-input/link existence, reference search and git diff --check. Docker absent; no container build or App.kt compilation/device verification claimed. |
| Code impact | f0d2f9d changes eight files: Docker setup removed, docs adjusted, text repaired. App.kt code supplied in chat, not applied locally. Percentage [Not documented]. |
| Time | Student-estimated active interaction time [Not documented]. |
| Outcome | Local commit created; pushes failed and manual command provided. Current follow-up adds linked logs/archive, with Git outcome reported separately. |
| Productivity/learning reflection | [Student reflection needed]. |
