# AI Technique Log - Milestone 2 / Assignment 3 integration

## M2-A1 - Integrate reviews and UI flow

**Date:** 2026-10-18.
**Contributor:** Requester; personal attribution not independently confirmed.
**Conversation:** [Conversation link needed].
**Decision record / full prompt:** [M2-A1](Milestone2-As3-Merge-AI-Decision-Log.md#m2-a1---integrate-reviews-and-ui-flow).

| Field | Record |
| --- | --- |
| Challenge / context | Transfer reviews and the new UI flow across repositories with independent histories while retaining destination team material. |
| AI used | Codex desktop performed the integration; no subagents were used. |
| Observable prompting pattern | User supplied source/destination URLs, a local folder, exact branch name, and a document relocation. Deliberate technique selection: [Student reflection needed]. |
| Iterations | One available human task prompt. The agent performed comparison, transfer, and validation attempts. |
| Debugging / refinement | Gradle initially failed establishing a Java loopback connection. The agent inspected installed JDK source and applied a process-only socket fallback for validation; no workaround was added to project files. |
| Failed approaches | Initial Gradle runs failed before project evaluation, including an alternate JDK/IPv4 retry. |
| Strategy changes | Used focused file import because the repositories have no common Git ancestor. Used the GitHub API with the existing Git credential because the GitHub CLI was unavailable. Credentials were not printed or saved in the repository. |
| Verification | 56 JVM and 55 Android host tests passed; Android debug APK, Desktop classes, shared/Web JS and Wasm compilation, and Android lint passed. Lint reported 20 warnings, no errors. Imported feature content matches the source revision; practices document content is unchanged. Student evaluation: [Not documented]. |
| Code impact | Existing source implementation and tests imported. Student-estimated percentage: [Not documented]. |
| Time | Student-estimated active time: [Not documented]. |
| Outcome | Requested branch contains the imported feature flow and relocated practices document; pull request status is recorded separately. |
| Productivity / learning reflection | [Student reflection needed]. |
