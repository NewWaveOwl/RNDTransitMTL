# AI Technique Log - ML3

See the [decision log](ML3-AI-Decision-Log.md) for full prompts and source coverage. Shared-session dates and exact models are [Not documented]; 2026-11-01 is the retrieval date. Related UI iterations are grouped into one challenge.

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
