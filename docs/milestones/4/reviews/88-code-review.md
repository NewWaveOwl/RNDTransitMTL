# Code review: #88 Create and join groups through the backend
Base: main @ 4954dac | Head: feature/88-create-join-groups @ 3942b2a

**Stats:** 11 commits; Kotlin, SQL and docs. New: migration, SQL test + shim, group models/DTOs/repository/change source, TogetherViewModel, Together screen (4 files), 6 test classes. Modified: Trip, TripDto, SupabaseTripRepository, TripsStore, Ids, SupabaseBackend, App, MainLayout, Router, ScreenKeys, build.gradle.kts, supabase/README.md, ML4 AI logs.

Checked against `docs/HowToWriteCode_InTheProject.md` (MVVM, KDoc with `@param`, layout settings, ID classes) and the plan. Also checked a crash risk and ruled it out: return trips are freshly generated (`ReturnTripViewModel.kt:121`), never copied from the outbound trip, so the new "return trip can't be a group trip" rule can't fire on them.

## Findings

### 1. A failed first load can leave a member stuck outside their own group
- severity: medium
- file: `shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/ViewModels/together/TogetherViewModel.kt:113` (and `View/together/TogetherContent.kt`, where `onRetry` is never shown)
- source: self
- detail: if `currentGroup()` fails (offline at open), the screen shows the invitation. Tapping Create or Join then gets `GROUP_ALREADY_JOINED` ("Leave it before joining another"), but there's no group on screen to leave and no Retry. The nav entry keeps the same ViewModel, so reopening the tab doesn't help until the app restarts.
- suggestion: after `AlreadyInGroup`, call `reload()`; and show a Retry action on the message card when the first load failed. Add a ViewModel test for both.
- outcome: _pending_

### 2. The unauthorized-update criterion has no executed test yet
- severity: medium (validation gap, not a code defect)
- file: `supabase/tests/groups.sql`
- source: self
- detail: the RLS policies and function checks are the security boundary for S2, and only the SQL test covers them. It hasn't run (no Docker or psql in this session).
- suggestion: start Docker Desktop. Then run the shim + migrations + test in `postgres:17`, plus the two break-and-restore checks from the plan.
- outcome: _pending_

### 3. Deploying the app before the migration breaks History for everyone
- severity: low
- file: `shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/data/trips/SupabaseTripRepository.kt:34`
- source: self
- detail: `historyColumns` embeds `group_trips`. If the app ships before `20261127200000_create_groups.sql` is applied, PostgREST rejects every History read. The README already says to apply migrations before deploying a client.
- suggestion: no code change. Put "apply the migration first" in the PR and apply it before merging (only the user can do this).
- outcome: _pending_

### 4. The mockup's "Shared choices" text describes an agreement flow that doesn't exist yet
- severity: low
- file: `shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/View/together/TogetherContent.kt` ("Shared choices" band, invitation text)
- source: self
- detail: the copy is kept from the mockup as asked, but in this slice the host plans and shares the trip; members don't vote. This is noted in the plan's Gate 2 answers.
- suggestion: keep it (the design is the spec) and open a follow-up issue for agreeing on choices, or soften the copy.
- outcome: _pending_

### 5. Two observers of the same group would share one Realtime topic
- severity: low (unverified)
- file: `shared/src/commonMain/kotlin/com/example/rnd_transit_mtl/data/group/GroupChangeSource.kt:52`
- source: self
- detail: every `changes(id)` call creates a channel named `group-<id>`. Today only `TogetherViewModel` observes. When #89/#90 add a second observer of the same group, removing one channel may affect the other (not checked against supabase-kt's channel registry).
- suggestion: give each subscription a unique suffix when #89 adds its observer, or share a single observer.
- outcome: _pending_

### 6. The AI log copies a local path containing a student number
- severity: low (privacy, your call)
- file: `docs/milestones/4/AI/ML4-AI-Decision-Log.md` (M4-A5, Full prompt 1)
- source: self
- detail: `HowToLog_AI.md` asks for prompts word for word, and prompt 1 includes `C:\Users\6324313\OneDrive - John Abbott College\...`. The repository is on GitHub.
- suggestion: keep it verbatim, or redact the path as `[local path]` with a note.
- outcome: _pending_

## Summary
**Needs revision:** 0 critical, 0 high, 2 medium (one code, one validation gap), 4 low.
