# AI Technique Log (ATL) — Jim — Milestone 3

**Contributor:** Jim (JimRashed), attributed as requested by Jim in the documentation request.
**Prepared:** November 15, 2026 (America/Toronto).
**Scope:** Milestone 3 / Sprint 3. This placement follows the chats’ Sprint 3 board, #59–#63 and ML3 feature context; the October project-location check is context only.

These records summarize conversations by significant challenge, following [HowToLog_AI.md](../../../HowToLog_AI.md) and Jim’s supplied ADR/ATL examples. Prompt-by-prompt transcripts are omitted at Jim’s explicit request. Conversation links are public, globally shareable ChatGPT snapshots created with Jim’s explicit authorization on November 15, 2026. They do not require local Codex access. Shares are immutable snapshots; later chat updates are not included automatically.

All historical outcomes and tests below are attributed to the visible conversation reports unless explicitly identified as checked during this documentation pass. No historical application task was rerun. Exact model versions, active AI time, code percentages and measured time savings are [Not documented]. Personal learning and productivity judgments are [Student reflection needed]. Alternatives are those discussed or directly contrasted in the source; they are not an assertion that Jim formally evaluated every option.

Entries group related iterations rather than individual prompts. Observable prompting patterns do not establish deliberate technique selection; Jim can confirm that in reflection. Decisions, source coverage and historical limits are recorded in the [ADR log](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-01: Clear issue names and epic labels

**Conversation link(s):** [Clarify GitHub issue nomenclature](https://chatgpt.com/s/cx_6af9ddf58920819195dbef38338c6200)

* **Date / Phase:** 2026-11-02 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Replace bracketed sprint codes in issue titles with descriptive titles, and represent each epic and work type with GitHub labels.
* **Challenge / Context:** Jim found identifiers such as S3-UI.5 difficult to interpret and requested less abbreviation.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Example-driven clarification and iterative scope correction. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Initial access was blocked by a signed-out browser; after the login handoff, Jim clarified that labels should cover all epics.
* **Verification Method:** Historical response reports checking the updated issue list and capturing github-issue-labels.png.
* **Code / Project Impact:** Project planning metadata; no application source changes.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The chat reports the requested issue updates and includes a screenshot. This documentation pass did not recheck the live board. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-01](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-02: Scope the persistence work and separate state from storage

**Conversation link(s):** [Estimate JimRashed issue effort](https://chatgpt.com/s/cx_6af9ddf2cd708191a9861d43ddaefd6b)

* **Date / Phase:** 2026-11-07 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Keep TripsStore for the active trip and drafts, introduce durable TripRepository storage, and divide Supabase delivery into client setup, auth UI, schema/security, and persistence acceptance.
* **Challenge / Context:** Jim requested independent effort estimates and an architecture explanation, then asked whether TripsStore actually persisted records and whether Supabase could support login and History.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Architecture questioning, independent estimation, dependency-based decomposition, and explicit acceptance criteria. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Moved from codebase overview and MVVM questions to storage boundaries, a connection-required scope, and four linked delivery steps.
* **Verification Method:** Historical source review at main 756f5a1; issue creation/board placement were reported. Device behavior was not established by that review.
* **Code / Project Impact:** Implementation plan for #59–#63 and the state/storage boundary.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** Jim requested the issue breakdown and explicitly required sign-in → finish trip → restart → same ID/status acceptance on Android and Desktop. AI estimates of 82–144 hours for the wider backlog and 24–40 hours for a usable Supabase release were planning estimates, not measured effort. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-02](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-03: Prove the shared Supabase client before the full account flow

**Conversation link(s):** [Estimate JimRashed issue effort](https://chatgpt.com/s/cx_6af9ddf2cd708191a9861d43ddaefd6b)

* **Date / Phase:** 2026-11-07 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Reuse one shared Supabase client and a temporary Android/Desktop sign-in screen before integrating the production auth destination.
* **Challenge / Context:** Jim needed file-by-file Kotlin and Gradle guidance for #60, including project access, dependencies, imports, and a connection smoke test.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Progressive elaboration, exact-file requests, error-trace debugging, and repository comparison. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Jim asked for increasingly detailed steps, then asked the agent to apply repairs. Native DLL/JAVA_HOME failures were separated from Kotlin and Yarn failures; the launchers were subsequently restored to App().
* **Verification Method:** Earlier build attempts stopped before compilation; later Desktop classes and Android assembly were reported successful. Live smoke-test results remain [Not documented].
* **Code / Project Impact:** Shared Gradle setup, SupabaseBackend, temporary sign-in screen, launchers and auth navigation.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The AI acknowledged its older full-file Gradle replacement had dropped existing configuration and reported restoring it. Later in the same chat, Desktop classes and Android debug assembly passed after duplicate AuthScreenKey removal. Successful live sign-in on both targets was not documented. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-03](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-04: Build session-based authentication and refine the draft UI

**Conversation link(s):** [Estimate JimRashed issue effort](https://chatgpt.com/s/cx_6af9ddf2cd708191a9861d43ddaefd6b); [Implement auth and planner screens](https://chatgpt.com/s/cx_6af9dd9d37008191b435aa8535a2da82)

* **Date / Phase:** 2026-11-07 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Use app-level session state with a shared AuthRepository/AuthViewModel form, provide sign-in or guest entry when no session exists, and style the screens against Jim’s supplied draft.
* **Challenge / Context:** A Profile status label alone did not supply a working sign-in screen. Jim later reported that startup changes did not appear and that fields, instructions, logo and button text were unreadable or missing.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Reference-image constraints, incremental implementation, user-observed defect feedback, and target-specific build checks. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Initial implementation was incomplete and initially unbuilt; Jim requested Android validation, styling integration and readability fixes in successive grouped refinements.
* **Verification Method:** Historical Android debug assembly passed; the agent explicitly did not verify the rendered screen on a device/emulator.
* **Code / Project Impact:** App.kt, Router.kt, AuthScreen.kt, AuthViewModel.kt and Profile layout.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** Jim explicitly requested implementation and then corrected missing startup behavior and visual defects. The chat reports Android debug assembly passing after fixes. Display-name editing and password changes remained unconnected. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-04](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-05: Isolate account state and harden authentication transitions

**Conversation link(s):** [Fix auth and account state bugs](https://chatgpt.com/s/cx_6af9dd9b384081919a39127e76814aeb)

* **Date / Phase:** 2026-11-07 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Scope trip state and navigation to the active identity, preserve same-account state where appropriate, show explicit signup confirmation, and prevent guest entry during an in-flight sign-in.
* **Challenge / Context:** PR #70 review feedback exposed missing source files, account-state leakage/reset problems, unreliable sign-out feedback, registration gaps, refresh-failure behavior and test races.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Review-driven repair, constraint correction, fake dependency injection and regression testing. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Grouped fixes evolved from compile blockers to account retention, confirmation persistence, debug preview access, UI-test animation timing and the sign-in/guest race.
* **Verification Method:** Chats report shared Android/JVM compilation, Android lint, and later JVM plus Android host tests passing. One earlier regression run was interrupted; live offline refresh/debug preview behavior remained untested.
* **Code / Project Impact:** Auth sources, App/session trip stores, Profile, debug network configuration and auth/UI tests.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** Jim clarified that pasted request-changes text was existing review feedback and instructed the agent not to commit or post to GitHub. Changes were reported local and uncommitted. The earlier signup-result interpretation was later refined in JIM-M3-08 to check actual session existence. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-05](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-06: Match the database schema to current DTOs and enforce ownership

**Conversation link(s):** [Add secure trip and review migrations](https://chatgpt.com/s/cx_6af9dd99b37c8191bb4049dce1e26239)

* **Date / Phase:** 2026-11-08 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Use versioned trips/reviews migrations aligned to current Kotlin DTOs, enforce ownership with grants and RLS on both tables, and link each review to its completed parent trip.
* **Challenge / Context:** Jim reported that the Supabase project had no tables, requested complete SQL, and challenged whether ownership protections applied to reviews as well as trips.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Source-grounded schema design, adversarial permission questions, and progressive SQL refinement. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Jim requested a code/docs comparison, then probed grants, auth.uid(), review ownership, SQL Editor versus Table Editor, and versioned CLI application.
* **Verification Method:** Historical source/DTO comparison; no documented live two-account CRUD/RLS test or fresh database check in this documentation pass.
* **Code / Project Impact:** Proposed trips/reviews schema and versioned migration; owner-scoped repository prerequisites.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The conversation iteratively refined proposed SQL and explained why RLS must cover direct review requests. Later local sessions reference 20261108154350_create_trips_and_reviews.sql. Hosted application and the two-account security acceptance results are [Not documented]. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-06](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-07: Persist final trips and reviews with durable retries

**Conversation link(s):** [Persist trips in Supabase History](https://chatgpt.com/s/cx_6af9dd96dc0c8191894388daf9f682c8); [Persist trips and reviews](https://chatgpt.com/s/cx_6af9dd957b688191a210a6e37a6d2770)

* **Date / Phase:** 2026-11-08 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Retain TripsStore for active state and drafts, load signed-in History/details from owner-scoped Supabase records, expose separate trip/review CRUD operations, and retry frozen final records using the same ID.
* **Challenge / Context:** History disappeared after application reset, and repository explanation showed that initial create/read methods did not provide full CRUD.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Concept clarification followed by implementation constraints, failure-scenario prompting and regression tests. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Two related conversations moved from create/read explanations to explicit update/delete/review operations, then full integration and an acceptance handoff.
* **Verification Method:** Historical Android debug build/Desktop compile passed; final serial runs reported 175 Desktop JVM tests and 152 Android host tests passing. An earlier Compose threading failure passed on rerun. Live relaunch/review/cancel evidence on both targets was handed to Jim and is [Not documented].
* **Code / Project Impact:** TripRepository, SupabaseTripRepository, CloudTrips, FinalTripOutbox, TripsStore, History/details/review UI.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** Jim authorized full CRUD and explicitly took over live acceptance testing. The chat reports implementation and tests, including lost-response retry after store recreation and cancelled status. Sign-out handling was subsequently strengthened in JIM-M3-08 to retain each account’s pending outbox. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-07](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-08: Address PR #74 persistence review feedback

**Conversation link(s):** [Address PR #74 comments](https://chatgpt.com/s/cx_6af9dd9387148191b80a07b903149986)

* **Date / Phase:** 2026-11-09 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Use a Wasm-compatible HTTP engine, retain pending writes per account across sign-out, determine signup confirmation from session existence, and save reviews atomically.
* **Challenge / Context:** PR #74 feedback covered build declarations, Wasm support, UUID checks, stale details, signup state, outbox loss and partial review writes.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Pasted-review grounding, finding-to-fix mapping, platform compilation and migration-based refinement. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** An inaccessible GitHub review was replaced with pasted evidence. Jim requested explanations and a commit-format overview; no live migration application was demonstrated.
* **Verification Method:** Historical :shared:jvmTest and :shared:compileKotlinWasmJs passed. The new review migration was not exercised against live Supabase.
* **Code / Project Impact:** Wasm dependencies, cloud/outbox declarations, App.kt, repository behavior and atomic review migration.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The chat reports fixes, passing JVM/Wasm checks, and migration 20261109160000_save_trip_review.sql. The initial retry comparison still included mutable title/description; JIM-M3-09 records the later correction. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-08](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-09: Acknowledge retried writes by immutable identity

**Conversation link(s):** [Fix retry acknowledgment after edits](https://chatgpt.com/s/cx_6af9dd907ab08191b6efab39042ab873)

* **Date / Phase:** 2026-11-13 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Confirm an already inserted trip using immutable ID, owner and creation time, allowing remote title, description and review edits to remain intact.
* **Challenge / Context:** A lost insert response followed by another device editing mutable fields caused retries to reject the existing row and leave the queue pending.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Concrete failure-scenario prompting and focused regression-driven repair. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** One focused request supplied the failure condition and required correction; no broader redesign was requested.
* **Verification Method:** Historical targeted tests passed; no new live multi-device test was performed for these records.
* **Code / Project Impact:** SupabaseTripRepository retry acknowledgement and regression coverage.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The historical final response reports the targeted fix and passing targeted tests. This supersedes the mutable-field comparison described in the earlier PR #74 work. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-09](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-10: Review PR #73 offline map fallback

**Conversation link(s):** [Review PR #73](https://chatgpt.com/s/cx_6af9dd91ecf481918398b8922cbb05ca)

* **Date / Phase:** 2026-11-13 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Recommend fitting the bundled offline map bounds when entering the picker with a saved viewport outside the available image.
* **Challenge / Context:** Jim requested a PR #73 review; the reported defect could leave the offline picker blank after browsing outside central Montréal.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Targeted code review with a concrete state-transition scenario. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** One PR-review request; no follow-up implementation recorded.
* **Verification Method:** Historical 175 JVM tests, Android debug Kotlin compilation and whitespace check passed. On-device behavior remained unverified.
* **Code / Project Impact:** Review finding for the Android offline picker; no source change reported.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The chat returned one actionable finding. Adoption of the suggested fix is [Not documented]. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-10](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-11: Preserve cloud persistence while resolving conflicts and reviewing PR #75

**Conversation link(s):** [Resolve PR #74 conflicts](https://chatgpt.com/s/cx_6af9dd8f6c3c819197385d9faa5a6034)

* **Date / Phase:** 2026-11-14 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Combine persistence and shared-map/authentication behavior when resolving PR #74 conflicts; recommend fixing PR #75’s Docker launcher omission before merging.
* **Challenge / Context:** Jim needed help choosing current versus incoming changes in GitHub’s web conflict editor, then requested a PR #75 review.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Side-by-side conflict reasoning, region-specific editing guidance, pasted-code review and broader PR validation. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Jim asked for exact current/incoming choices after the overview, supplied a resolution for checking, then expanded the conversation to PR #75.
* **Verification Method:** Historical PR #75 backend/launcher tests: 68 passed; shared JVM regression checks and exact-commit CI passed. Docker failure reproduced. Live OTP/realtime and packaged Windows execution were not tested.
* **Code / Project Impact:** Conflict guidance for App.kt, History, Gradle/auth/session/test files; Dockerfile and UI text review findings.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** Conflict recommendations were text/source review and were not compiled as Jim’s merged result. PR #75 review reported no tracked changes. The pasted resolution was judged structurally correct subject to checking the trailing declarations. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-11](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-12: Implement a presentation-only compass from the draft

**Conversation link(s):** [Add compass screen](https://chatgpt.com/s/cx_6af9dd6b21c88191a5f447b29fa2c17f)

* **Date / Phase:** 2026-11-14 — Milestone 3 / Sprint 3.
* **Goal / Requirement:** Build active/idle compass layouts with an input-driven needle, wire the existing bottom-tab placeholder, and leave sensor/trip-action integration for Jim.
* **Challenge / Context:** Jim supplied a draft screenshot and explicitly requested the screen without full functionality on feat/compass-screen-and-state.
* **AI Tool(s) Used:** Codex desktop; historical tasks used repository tools and, where recorded, browser/GitHub access. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Reference-image implementation, explicit scope constraints and commit decomposition. Deliberate technique selection: [Student reflection needed].
* **Iteration & Adaptation:** Implementation followed by a request to separate the changes into commits for layout, direction input, navigation and reveal controls.
* **Verification Method:** Historical JVM compilation and existing regressions passed; visual appearance was not verified in a running app. Commit history was checked during this documentation pass.
* **Code / Project Impact:** CompassScreen.kt, Router.kt and MainLayout.kt.
* **Time Spent / Saved:** [Not documented]. No measured productivity gain or student time estimate is available for this challenge.
* **Reflection / Result:** The chat reports commits 052800e, ef1ddb0, e9bafbc and 429a2e4. Current repository history confirms those four commits. Live direction/sensor behavior was intentionally left for Jim. Personal learning/productivity reflection: [Student reflection needed].
* **Decision Record:** [JIM-M3-12](ML3-Jim-AI-Decision-Log.md).

## Entry JIM-M3-13: Compile Jim’s conversation overview records

**Conversation Link:** [Create Jim's AI records](https://chatgpt.com/s/cx_6af9ddf8f64c8191aef02889219354a5)

* **Date / Phase:** November 15, 2026 — Milestone 3 documentation.
* **Goal / Requirement:** Produce separate Jim-specific decision and technique logs similar to the supplied examples, with conversation overviews and links.
* **Challenge / Context:** Existing logs cover other contributors; related local sessions include explanations, implementation, review recommendations and later corrections that must not be counted as independent completed features.
* **AI Tool(s) Used:** Codex desktop, local chat-history tools and repository file tools; no subagents used in this task. Exact model version [Not documented].
* **Prompting Technique(s) / Approach:** Example-guided formatting, repository-grounded synthesis and cross-conversation comparison.
* **Iteration & Adaptation:** Grouped related sessions, followed older-history cursors, distinguished recommendations from reported implementation, and recorded how immutable retry identity supersedes the earlier mutable-field comparison. The request’s overview format takes precedence over the guide’s full-prompt template.
* **Verification Method:** Check matching entry IDs, relative document links, retrieved chat IDs, source coverage and whitespace; preserve existing contributors’ files.
* **Code / Project Impact:** Two Jim-specific Markdown records and a central index link; application code unchanged.
* **Time Spent / Saved:** [Not documented].
* **Reflection / Result:** Conversation-based drafts created for Jim’s review. Personal learning and usefulness: [Student reflection needed].
* **Decision Record:** [JIM-M3-13](ML3-Jim-AI-Decision-Log.md).

