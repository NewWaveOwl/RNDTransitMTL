# ML3 PR 75 review, fixes and merge - available human prompts

**Source:** Current Codex conversation; [Conversation link needed]. No verified share URL or app-only conversation link was supplied for this chat. The older shared conversation in M3-A9 through M3-A12 is a different source and is not reused here.

**Logged:** 2026-11-14 (America/Toronto). Individual prompt timestamps, exact model version and personal time estimates: [Not documented].

**Coverage:** The three available task prompts below are retained in order, including their original spelling, punctuation and line breaks. Automatically supplied environment metadata, interface labels, hidden instructions and private reasoning are excluded. No attachments were supplied in these three prompts. Assistant answers are summarized in the decision log rather than represented as a full raw transcript. The supplied prompts contain no tokens or secret configuration values; no secret values from inspected configuration or authentication tools are copied into this archive.

**Cross-references:** [M3-A13 - independent review](ML3-AI-Decision-Log.md#m3-a13---independent-pr-75-review-and-reproduction), [M3-A14 - fixes and Git handoff](ML3-AI-Decision-Log.md#m3-a14---pr-75-fixes-merge-resolution-and-commit-handoff), and the [technique log](ML3-AI-Technique-Log.md#m3-a13---independent-pr-75-review-and-reproduction).

## P01 - Independent PR review

````text
Thoroughly review PR https://github.com/NewWaveOwl/RNDTransitMTL/pull/75.

Repository:
K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL-otp-integration

This is a review-only task. Work autonomously without asking for approval. Run necessary inspection, fetch, build and test commands within available permissions. If blocked, report the limitation and continue independent checks.

Read all applicable AGENTS.md instructions first.

IMPORTANT: The backend is already running on a separate server PC.
- Preserve the running server. Do not restart, stop, reconfigure, rebuild or redeploy it.
- Do not start another complete server/OTP stack merely to repeat startup verification.
- Find the configured generation endpoint in local configuration or documentation without printing credentials.
- Establish the running server's version using available version/build information. If it cannot be identified, clearly distinguish live-server observations from verification of this PR.
- Use safe, bounded requests to check connectivity, authentication and representative trip generation where credentials and access are already available.
- Run requests sequentially. Do not perform load tests, exhaust rate limits or disrupt service.
- Do not assume a running server proves the PR is correct.

Establish the exact review scope:
- Use GitHub CLI (gh) to retrieve the latest PR description, base/head branches and SHAs, changed files, existing review comments and CI results.
- Fetch required Git refs.
- Compare the PR head against its merge base with the current PR base.
- Verify whether the local checkout matches the latest PR head.
- Review committed PR changes only. Preserve all existing uncommitted changes, especially gradle.properties.
- Do not switch branches or overwrite local work unnecessarily. Use a separate temporary checkout if needed.

Review the complete diff and relevant surrounding code, callers, configuration and tests. Investigate:
1. Backend generation: bus-trip timeouts, search deadlines, candidate selection, duration matching, initial waiting, transfers, orientation allowance and cancellation.
2. Planner constraints: transport modes as alternatives, walking access, selected routes, fare zones, intermediate geometry checks and excluded destinations.
3. OTP decoding: response handling, connected geometry, endpoint connectors, timing, realtime metadata and scheduled fallback.
4. Android flow: GO, loading, Cancel, back navigation, retry, concurrent requests, stale responses, proposal acceptance and saved-trip restoration.
5. Trip presentation: leg colours, current section, next action, walking turns, boarding instructions and simulated progress.
6. Windows server: launcher startup/shutdown logic, process ownership and recovery, imports, packaging, configuration, credentials, feed health and tunnels.
7. Build and integration compatibility across affected modules.

Choose verification based on risk:
- Inspect CI results for the exact PR head before deciding which checks to repeat.
- Run focused backend, launcher and shared tests that exercise changed behavior or investigate suspected bugs.
- Avoid repeating expensive checks already passing for the exact head unless local configuration or an unresolved concern justifies it.
- Attempt Android compilation/lint if relevant coverage is missing.
- Check launcher and packaging code through inspection and isolated tests; do not manipulate the running server.
- For outages, stale feeds and fallback, use fixtures or isolated tests rather than disabling real services.
- Build outputs and temporary verification files are allowed; do not alter tracked source files.

If the running server is accessible, prioritize a small representative sample:
- Health and authenticated generation.
- Walk only, Bus only, and Walk + Bus.
- Short and longer time budgets.
- Confirm accepted elapsed time stays within the requested maximum.
- Inspect geometry, modes, timing and realtime/fallback metadata.
- Investigate intermittent bus failures with a small bounded number of sequential retries; record request choices, latency and response status.
- Treat cancellation, stale UI responses and restoration as client behaviors requiring code tracing, focused tests or device verification. A successful API response does not verify them.

Skip live checks when the endpoint or valid credentials are unavailable. Do not invent credentials or claim unperformed tests passed. Never print tokens or secret configuration values.

Trace potential bugs end to end. Check whether tests cover production behavior. Passing tests and a running server do not prove integration correctness.

Do not change machine settings, install global dependencies, stop unrelated services, deploy anything, modify source code, commit, push, merge, or publish comments/reviews to GitHub.

Report only actionable bugs introduced by this PR:
- Verify each finding through a reproduction, focused test, or clear code path.
- Distinguish regressions from pre-existing behavior and intentional design choices.
- Avoid speculative findings, style preferences and unrelated refactoring.
- Verify whether previously reported bugs remain present at the reviewed head.
- A confirmed finding does not require writing a fix.

Final response:
- State reviewed head SHA and base SHA.
- List findings first, ordered by severity.
- For each finding include [P1], [P2] or [P3], a concise title, precise changed file/line reference, triggering scenario, actual behavior, user impact and suggested correction.
- Summarize checks actually run and their results.
- Separate live-server observations, local tests and exact-head CI evidence.
- List skipped or blocked checks and explain why.
- State remaining verification gaps and give a merge recommendation.
- If no actionable bugs are found, say so explicitly with verification limits.

The earlier ChatGPT review was interrupted. It provides no evidence that this PR is correct. Perform an independent review and persist until all accessible review work is complete.
````

## P02 - Remediation and Git authorization

````text
fix found errors , then commit, fix merge conlfict then commit and push to the PR
````

## P03 - AI log and push-command handoff

````text
Using AI log skill log this "chat", them commit and povrf push comamd for me
````

The logging request preserves P01 and P02 as historical evidence. It does not repeat their coding, test or server instructions. The requested push command must use the isolated checkout containing the new commits; no successful remote push is established by this chat.
