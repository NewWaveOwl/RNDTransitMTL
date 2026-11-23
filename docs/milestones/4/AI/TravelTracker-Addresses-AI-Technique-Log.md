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
