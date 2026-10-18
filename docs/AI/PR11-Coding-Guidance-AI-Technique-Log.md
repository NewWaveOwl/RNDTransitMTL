# AI Technique Log - PR 11 coding guidance and skill setup

## PR11-A1 - Extend guidance and package the existing skill

**Date:** 2026-10-18.
**Contributor:** Requester; personal attribution not independently confirmed.
**Conversation:** [Conversation link needed].
**Decision record and full prompt:** [PR11-A1](PR11-Coding-Guidance-AI-Decision-Log.md#pr11-a1---update-coding-practices-and-add-ai_log).

| Field | Record |
| --- | --- |
| Challenge / context | Extend PR 11's coding/commenting guide from two decks and add repository agent instructions plus the installed AI_Log skill. |
| AI used | Codex desktop, without subagents. |
| Observable prompt elements | Existing PR/file, two local decks, a reference repository, and an explicit source-instruction boundary. Deliberate prompting technique choice: [Student reflection needed]. |
| Iterations | One available task prompt for this update; the agent performed source inspection, drafting, and validation. |
| Debugging / refinement | Initial text extraction hit the Windows console encoding limit. UTF-8 extraction and smaller slide ranges supplied the text. Technical claims were checked against official sources instead of copying the slide tables as universal guarantees. |
| Failed / rejected approaches | No human-rejected output is documented. An initial large read was truncated and replaced with focused reads. |
| Strategy changes | Adapted TravelTracker's canonical `.github/skills` routing to this Kotlin project. Added wrappers for agent discovery and made the copied personal skill's source references portable. |
| Verification | Three skill entry points passed quick_validate.py. Nine Markdown files passed code-fence and 15 local-link/heading checks; the 18-deck/624-slide inventory was verified. The copied rules/templates match the installed skill, and Git whitespace checks passed. Application builds/tests were not run for this documentation-only update. Student evaluation: [Not documented]. |
| Code impact | Documentation/agent instructions only. Student-estimated percentage: [Not documented]. |
| Time | Student-estimated active interaction time: [Not documented]. |
| Outcome | Updated coding guidance and repository-local skill on PR 11's existing head branch. |
| Productivity / learning reflection | [Student reflection needed]. |
