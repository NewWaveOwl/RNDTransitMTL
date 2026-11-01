# AI Decision Log - Milestone 2 / Assignment 3 integration

## Overview

This record covers AI-assisted integration of the reviews and new UI flow from Artiom's Assignment 3 repository into RNDTransitMTL, and relocation of the team practices document. It records this integration session only; the earlier Sprint 1 records remain unchanged.

## Contributor and milestone coverage

| Contributor | Requirement / milestone | AI use | Entry IDs |
| --- | --- | --- | --- |
| Requester; personal attribution not independently confirmed | Milestone 2 folder / Assignment 3 feature integration | Codex performed repository comparison, integration, documentation, and validation attempts | M2-A1 |

## Linked AI sessions

| Entry | Date | Tool / conversation link | Purpose |
| --- | --- | --- | --- |
| M2-A1 | 2026-10-18 | Codex desktop; [Conversation link needed] | Prepare a separate branch and pull request for reviews and UI flow |

## M2-A1 - Integrate reviews and UI flow

**AI tool:** Codex desktop. Exact model version: [Not documented].
**Source:** Current conversation; [Conversation link needed]. No conversation was publicly shared to generate a link.
**Attachments:** No file attachments supplied. Two GitHub repository URLs and a TeamPractices.md URL were supplied.

### Full available human prompt

```text
0\) use this folder K:\CLASSES 26 FALL\APP_DEV\Milestone_2_as3_merge; When working create sepparate bracbh named As3_Artiom_UI_Review_Merge 1) Move code of the revies and new UI flow from this repo [https://github.com/NewWaveOwl/As3_APPDEV2_RND_Tranzit_Sova.git](https://github.com/NewWaveOwl/As3_APPDEV2_RND_Tranzit_Sova.git) as merge request to [https://github.com/NewWaveOwl/RNDTransitMTL](https://github.com/NewWaveOwl/RNDTransitMTL). 2) Move [TeamPractices.md](https://github.com/NewWaveOwl/RNDTransitMTL/blob/main/TeamPractices.md "TeamPractices.md")  to docs
```

**AI output summary:** Created the requested branch from destination main `5518977`. Compared the repositories and found no shared Git ancestor. Imported `shared/src`, the shared build dependencies, Android network permissions, and the Wasm dependency lock from source `8b1b538`. Updated README usage and moved `TeamPractices.md` to `docs/TeamPractices.md` without changing its content. Preserved the destination's issue templates, release workflow, existing team/design documents, and earlier AI logs. Updated the pull-request workflow to use JDK 21, the imported regression tests, platform compilation, Android lint, and validation report uploads; its previous `ktlintCheck` command had no configured plugin/task.

**Student decision / how output was used:** The user explicitly requested the integration and pull request. Acceptance of the resulting code and personal understanding of it: [Student reflection needed].

**Agent use:** Codex executed the work autonomously within the user's request. No subagents were used. Imported implementation and tests retain their source content; this session did not originate that feature implementation.

**Verification:** Newly executed local checks passed:

- `:shared:jvmTest`: 56 tests, zero failures/errors/skips.
- `:shared:testAndroidHostTest`: 55 tests, zero failures/errors/skips.
- `:androidApp:assembleDebug`: Android debug APK built.
- `:shared:compileKotlinJs`, `:shared:compileKotlinWasmJs`, `:webApp:compileKotlinJs`, and `:webApp:compileKotlinWasmJs`: compilation passed.
- `:desktopApp:classes`: Desktop entry point compiled.
- `:androidApp:lintDebug`: passed with 20 warnings and no errors.
- `git diff --cached --check`: passed. Imported feature paths match source `8b1b538` exactly, and the relocated practices document has the same Git content hash as the original.

Gradle initially failed before project evaluation because this Windows execution environment rejected Java's Unix socket connection. A process-only `JAVA_TOOL_OPTIONS` override forced the installed JDK's TCP fallback by selecting an unavailable Unix socket directory. This allowed validation; no project setting was changed for the workaround. Interactive app operation and iOS checks were not run. GitHub-hosted workflow results are separate from these local results.

**Limitations:** No independent student review or device interaction is established by this session. Historical source-repository test claims are not treated as new results. The nickname is memory-only and History uses saved-state restoration, not permanent database storage.

**Technique record:** [M2-A1](Milestone2-As3-Merge-AI-Technique-Log.md).

## Reflection

- Observable AI assistance: repository comparison, focused feature transfer, documentation, and validation attempts.
- Student assessment of usefulness, code understanding, learning, time, and code percentage: [Student reflection needed].
