# Issue #99 source prompt (M4-A7)

Date: 2026-11-27 (America/Toronto). Tool: Codex. [Conversation link needed].
This is the full available human task prompt. No conversation was published.

```text
Implement GitHub issue #99 using the issue details below. Read the repo instructions and preserve existing local changes. Run the relevant tests and report what changed and what passed. Leave the changes uncommitted.

{"body":"## User Story\n- **As a** traveller or guest\n- **I want to** receive accurate trip data and use an appropriate generation allowance\n- **So that** I can trust trip planning before starting.\n\n## Context / Background\nOwner: @NewWaveOwl. Source: Feedback list.md. Due 30 November.\n\n**Schedule:** 21 November 2026 ??? 30 November 2026. Sprint ends 30 November 2026.\n\n## Acceptance Criteria (Given / When / Then)\n- **Scenario 1:** Given a signed-in user or eligible guest, when I generate a trip, then the correct per-user track and refreshed estimates are returned.\n- **Scenario 2:** Given a guest reaches five trips in a day or fetching fails, when another request is made, then the limit/error is clear and the last valid trip remains available.\n\n## Technical Notes / Subtasks\n- Split fetch/estimate corrections, per-user backend tracks and five-per-day guest allowance.\n- Define daily reset/timezone and guest identity policy explicitly; enforce limits on the server.\n- Preserve existing timeout, cooldown and routing constraints.\n\n## Definition of Done\n- [ ] Code reviewed and merged\n- [ ] Relevant unit and integration tests pass\n- [ ] Verified in staging / test build; evidence attached\n","comments":[],"title":"ML4-11 Fix trip fetches and estimates, generate per-user routes and limit guests to 5 trips per day"}
```
