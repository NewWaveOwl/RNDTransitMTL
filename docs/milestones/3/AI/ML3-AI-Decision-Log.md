# AI Decision Log - ML3

## Overview and coverage

ML3 scope is supplied by the requester. This log records six shared URLs covering five distinct visible conversations, plus the current documentation session. Retrieval date: 2026-11-01 (America/Toronto); original session dates/model versions are [Not documented]. Contributor: requester; the supplied branch/planning conversations identify Artiom / NewWaveOwl, but authorship of every shared prompt is not independently confirmed.

The web reader returned cache misses for all six URLs; the browser successfully displayed their contents. Full visible human prompt text is retained below, with rendered links on separate lines. Attachment images in the brainstorming session were visible as attachment controls; original filenames and image contents were not extracted. Collapsed work traces are not included. Historical code/test reports are attributed to AI and have not been rerun in this documentation session.

## Linked AI sessions

| Entry | Conversation | Scope |
| --- | --- | --- |
| M3-A1 | [ChatGPT — PR 53 review and Android saved-state explanations](https://chatgpt.com/s/cx_6ae7b432fc4081919e59a08ce2eaf35d) | ML3 — PR 53 review and Android saved-state explanations |
| M3-A2 | [ChatGPT — TripRepository contract alternatives](https://chatgpt.com/s/cx_6ae7b43e5c248191bbd063a55e6e6b6d) | ML3 — TripRepository contract alternatives |
| M3-A3 | [ChatGPT — Artiom branch cleanup before merge](https://chatgpt.com/s/cx_6ae7b453343081918d8421325681107e); [ChatGPT — Artiom branch cleanup before merge (alternate share)](https://chatgpt.com/s/cx_6ae7b462ac2081919d7b6bd2d4cfa016) | ML3 — Artiom branch cleanup before merge |
| M3-A4 | [ChatGPT — PR 52 review and saved-feedback visibility](https://chatgpt.com/s/cx_6ae7b468dce88191ab264db7cf8ae4df) | ML3 — PR 52 review and saved-feedback visibility |
| M3-A5 | [ChatGPT — ML3 Figma alignment, trip screens and issue 30 planning](https://chatgpt.com/s/cx_6ae7b476302081919f02267cac33adfb) | ML3 — ML3 Figma alignment, trip screens and issue 30 planning |
| M3-A6 | Codex desktop; [Conversation link needed] | ML3 log organization and repository preparation |

## M3-A1 - PR 53 review and Android saved-state explanations

**Source:** [Shared conversation](https://chatgpt.com/s/cx_6ae7b432fc4081919e59a08ce2eaf35d)

**Date / exact model:** [Not documented]. **Tool:** ChatGPT shared conversation; assistant describes Codex repository work where applicable.

### Full visible human prompt 1

```text
Revie my Brazilain friend code: 
https://github.com/NewWaveOwl/RNDTransitMTL/pull/53
```

### Full visible human prompt 2

```text
Was est das : Bundle-compatible keys?
```

### Full visible human prompt 3

```text
in eng
```

### Full visible human prompt 4

```text
what is boxed trip id
```

**AI output summary:** AI reported a potentially merge-blocking History lazy-list key using boxed TripId and recommended it.id.value. Explained Android Bundle compatibility in German, then English, and explained boxing. The response explicitly described source review without build/device tests.

**Student decision / how output was used:** User requested English and further explanation. Adoption of the suggested fix: [Not documented].

**Implemented result / verification evidence:** Historical AI reports above are the available evidence; no fresh application verification was performed. Unknown outcomes remain [Not documented].

**Technique record:** [M3-A1](ML3-AI-Technique-Log.md).

## M3-A2 - TripRepository contract alternatives

**Source:** [Shared conversation](https://chatgpt.com/s/cx_6ae7b43e5c248191bbd063a55e6e6b6d)

**Date / exact model:** [Not documented]. **Tool:** ChatGPT shared conversation; assistant describes Codex repository work where applicable.

### Full visible human prompt 1

```text
https://github.com/NewWaveOwl/RNDTransitMTL/issues/27 Propose 3 versions
Define TripRepository operations to save trips, observe/read only the owner’s history, open details and save optional reviews and route-section/place feedback.
```

### Full visible human prompt 2

```text
Does this allign with our plans?
```

**AI output summary:** Proposed simple, reactive, and feedback-bundle contracts; recommended the reactive version. Follow-up compared the design document and called for owner-scoped access, independent optional stars/text, explicit section/place feedback, separate review storage, preserved history and trip status. AI said issue retrieval and direct PDF verification were incomplete.

**Student decision / how output was used:** User requested alignment with project plans. Final contract selection or implementation: [Not documented].

**Implemented result / verification evidence:** Historical AI reports above are the available evidence; no fresh application verification was performed. Unknown outcomes remain [Not documented].

**Technique record:** [M3-A2](ML3-AI-Technique-Log.md).

## M3-A3 - Artiom branch cleanup before merge

**Source:** [Shared conversation](https://chatgpt.com/s/cx_6ae7b453343081918d8421325681107e); [Shared conversation](https://chatgpt.com/s/cx_6ae7b462ac2081919d7b6bd2d4cfa016)

**Date / exact model:** [Not documented]. **Tool:** ChatGPT shared conversation; assistant describes Codex repository work where applicable.

### Full visible human prompt 1

```text
Code review the curretn Artiom branch taht the code does not have redundatn or unnessary code. Iwant to clean it before merge. Verifi all files that were edited. K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL Aslo verivfy haat we dont have usless test if have delete them
```

### Full visible human prompt 2

```text
so waht should be done?
```

### Full visible human prompt 3

```text
we dont' have old trips so we an just merge the 5 minute one with no consequenss no?
```

### Full visible human prompt 4

```text
write a commint message in the caht
```

**AI output summary:** AI reported deleting six arithmetic starter tests, removing unused/redundant elements, reusing the simulation constant, and retaining meaningful tests. Reported inspecting 102 changed files, XML/SVG parsing, PNG headers and whitespace. Initially flagged old zero-minute saved trips; after the user said none exist, withdrew that compatibility blocker. Gradle failed before compilation with a loopback-connection error; changes were reported uncommitted.

**Student decision / how output was used:** User explicitly stated there are no old trips and requested a commit message. A merge, passing tests, and subsequent acceptance are not established. Both supplied shares display the same visible conversation and are counted once.

**Implemented result / verification evidence:** Historical AI reports above are the available evidence; no fresh application verification was performed. Unknown outcomes remain [Not documented].

**Technique record:** [M3-A3](ML3-AI-Technique-Log.md).

## M3-A4 - PR 52 review and saved-feedback visibility

**Source:** [Shared conversation](https://chatgpt.com/s/cx_6ae7b468dce88191ab264db7cf8ae4df)

**Date / exact model:** [Not documented]. **Tool:** ChatGPT shared conversation; assistant describes Codex repository work where applicable.

### Full visible human prompt 1

```text
Review this PR 
https://github.com/NewWaveOwl/RNDTransitMTL/pull/52
```

### Full visible human prompt 2

```text
waht this means?
```

**AI output summary:** AI reported two P2 regressions at reviewed commit 1aef17d: TripDetailsV3 bypassed review editing and omitted Quality/Interesting/Fun/photo feedback. Explained that hidden ratings were still saved. JVM tests could not run because of the Gradle loopback failure.

**Student decision / how output was used:** User sought clarification. Fix adoption and student verification: [Not documented].

**Implemented result / verification evidence:** Historical AI reports above are the available evidence; no fresh application verification was performed. Unknown outcomes remain [Not documented].

**Technique record:** [M3-A4](ML3-AI-Technique-Log.md).

## M3-A5 - ML3 Figma alignment, trip screens and issue 30 planning

**Source:** [Shared conversation](https://chatgpt.com/s/cx_6ae7b476302081919f02267cac33adfb)

**Date / exact model:** [Not documented]. **Tool:** ChatGPT shared conversation; assistant describes Codex repository work where applicable.

### Full visible human prompt 1

```text
I want to incert box_with gradienbt under the Biking speed slider in figma how can I do expalin in the chat | 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=9-2 lik on the scrren shot ( name of the compmnnets : box_with gradienbt and RND / Preferences / Bike speed)
```

### Full visible human prompt 2

```text
ot soen work the x and y atre gray I want uo pu box in the middle under the slider torendr slow or fast pace on to pas a text)
```

### Full visible human prompt 3

```text
Can you do it
```

### Full visible human prompt 4

```text
where i ca ncage GO to bold in the code for the Gobox ( RND / GOBox / Ready)
```

### Full visible human prompt 5

```text
I maen in the K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL
```

### Full visible human prompt 6

```text
Lets add this RND / Field / Label & value box 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9 and implemetation of the preview and loading scrren loading write code in the chatt
```

### Full visible human prompt 7

```text
let implemt update for the active map use mockup from figma named active 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9| aslo verivi it with other scrren like main_active , stale, no_route, provider_error, cancel | Provide code in the chat
```

### Full visible human prompt 8

```text
Implmemt update of the UI for this screens provide code and instrcutoins in the caht
```

### Full visible human prompt 9

```text
According to UI designs: 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9 and 
https://github.com/NewWaveOwl/RNDTransitMTL/issues/30 Wah left to to finsh the issue ?
```

### Full visible human prompt 10

```text
Two confirmed gaps should be fixed first:

History Remove deletes immediately. The issue explicitly requires confirmation before deletion.
Zero minutes is still accepted. Trip.MIN_PLANNED_MINUTES remains 0, contrary to #32.

Prpose fix the chat : provide full code:
```

### Full visible human prompt 11

```text
Wahts mising now to finish 
https://github.com/NewWaveOwl/RNDTransitMTL/issues/30
```

### Full visible human prompt 12

```text
AWht sub-issues taht assigned to me left in the 
https://github.com/NewWaveOwl/RNDTransitMTL/issues/30
```

### Full visible human prompt 13

```text
#32 — Map, GO controls and transport choicesEditable starting point/manual-origin fallback; location marker/recenter behavior; verify preference-panel keyboard controls and preserved choices; Android/Desktop screenshots and interaction checks wah we need to do to implemt this ? Write in chat instruction and full code
```

### Full visible human prompt 14

```text
Does your proposed implemtation with my figma design: 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9
```

### Full visible human prompt 15

```text
Allign your solution with MY DESIGN AND PROPOSE FULL CODE andinstruction in the caht
```

### Full visible human prompt 16

```text
Provide implematiob with code in the CHAT: Connect or expose labelled no-route, provider-error, permission-denied and stale/missing-location samples; manual origin when permission is denied; current-section/recenter controls; verify Finish/Cancel behavior, Back navigation and that failures never activate trips 
#33 — Preview, active trip and errors | USe desifgn from here 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=86-9 and assetes from here 
https://www.figma.com/design/EBOrT92jIVwFv0mB8jeeVY/RndTransit_main?node-id=9-2
```

**AI output summary:** Proposed Auto layout placement for the pace label, bold GO text, label/value fields, loading/preview, active-map/error/cancel states, and complete/review/feedback/history/details UI. Compared issue 30 with reported local code, suggested History delete confirmation and a five-minute minimum, and reported those fixes present at 1a0a07d (source inspection, not new interaction tests). Proposed issue 32 manual origin/recenter and issue 33 failure fixtures, cancellation guards, Back handling, confirmations, previews and tests. Follow-up corrected design mismatches after the user required alignment with their Figma. The final code was explicitly proposed and uncompiled; real routing/GPS/cancelled-history storage remained separate integrations.

**Student decision / how output was used:** User corrected placement, clarified that GO bolding concerned Kotlin rather than Figma generation code, requested code in chat, and insisted on the supplied design. Subsequent manual application and final visual acceptance: [Not documented].

**Implemented result / verification evidence:** Historical AI reports above are the available evidence; no fresh application verification was performed. Unknown outcomes remain [Not documented].

**Technique record:** [M3-A5](ML3-AI-Technique-Log.md).

## M3-A6 - Current log organization session

**Date:** 2026-11-01. **Tool:** Codex desktop; exact model [Not documented]. **Source:** [Conversation link needed].
**Attachment:** docs/HowToLog_AI.md (read as logging reference, not authority to execute historical tasks).

### Full available task request

```text
I added some folders to K:\CLASSES 26 FALL\APP_DEV\PRJ\RNDTransitMTL I neede to commit them and log my current AI ussage for ML3.  And also move the logs accrofing l to theirfolder. Add this to instrucns of to HowToLog_AI.md | [https://chatgpt.com/s/cx_6ae7b432fc4081919e59a08ce2eaf35d](https://chatgpt.com/s/cx_6ae7b432fc4081919e59a08ce2eaf35d) | [https://chatgpt.com/s/cx_6ae7b43e5c248191bbd063a55e6e6b6d](https://chatgpt.com/s/cx_6ae7b43e5c248191bbd063a55e6e6b6d) | [https://chatgpt.com/s/cx_6ae7b453343081918d8421325681107e](https://chatgpt.com/s/cx_6ae7b453343081918d8421325681107e) | [https://chatgpt.com/s/cx_6ae7b462ac2081919d7b6bd2d4cfa016](https://chatgpt.com/s/cx_6ae7b462ac2081919d7b6bd2d4cfa016) | [https://chatgpt.com/s/cx_6ae7b468dce88191ab264db7cf8ae4df](https://chatgpt.com/s/cx_6ae7b468dce88191ab264db7cf8ae4df) | [https://chatgpt.com/s/cx_6ae7b476302081919f02267cac33adfb](https://chatgpt.com/s/cx_6ae7b476302081919f02267cac33adfb)  (Do not push and commit just write a comiis message i nthe chat)
```

**AI output summary / implemented result:** Inspected the new milestone meeting notes and existing logs; moved Sprint 1/ideation records into milestone 1 and integration records into milestone 2; retained cross-milestone PR11 guidance in docs/AI; added the central index, ML3 decision/technique logs, and placement/source-handling instructions. Preserved original meeting notes and historical log content. Supplied a proposed commit message in chat.
**Student decision:** Explicitly requested organization/logging and prohibited commit/push. Final review: [Student reflection needed].
**Agent use:** Codex performed documentation edits autonomously within this request. No subagents used. Historical prompts were documented, not executed.
**Verification scope:** Documentation links, relocated-file preservation, whitespace and Git status; no application build or test was needed for these documentation changes.
**Technique record:** [M3-A6](ML3-AI-Technique-Log.md).

## Reflection and missing evidence

- Observable assistance: design/code proposals, review explanations, project planning, branch cleanup reports, and log preparation.
- Student learning, deliberate technique selection, AI interaction time, code percentage, and final acceptance: [Student reflection needed].
- Artiom/Time_spent.txt lists development hours; these are not established AI interaction time and are not used as such.
- Current chat link and original session dates/models: [Not documented]. No private chat was published to obtain a link.

## M3-A6 follow-up - Commit and PR authorization

The initial preparation left the changes uncommitted as requested. The requester then asked:

```text
where is my AI usage logs what folder (location) reply in thecaht
```

AI supplied the ML3 folder location. The requester subsequently authorized publishing the work:

```text
Commit and create a PR
```

**Student decision:** The later request authorizes a commit and pull request, superseding the earlier no-commit/no-push constraint for this documentation work.
**AI output / implementation:** Prepared a documentation branch, `docs/ml3-ai-usage`, on the refreshed `origin/main`, preserving upstream updates and excluding unrelated Artiom branch code. Committed the milestone notes, relocated logs, ML3 records, central index and logging-guide changes. Push/PR outcome is reported in chat.
**Verification:** The diff contains only documentation. Relocated records retain upstream content; log/index file links and Markdown fences were checked during preparation. Whitespace checks report existing meeting-note whitespace and trailing spaces in verbatim captured prompts; these are preserved as source evidence. No application build/test result is claimed.
