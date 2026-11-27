# Plan: Show group members, party size and active game in trip details and history (Issue #89, ML4-02.S2)

> Workflow: R-PIV pipeline from the McGill Portal project (`McGillPortal_Project/.claude/skills/`), adapted to
> GitHub, Gradle and Supabase. **Assumes `88-create-and-join-groups.md` is implemented exactly as planned and
> merged into `main`.** Reference its files and names; don't re-plan anything it covers.

## User Story
As a group member, I want to see my group in the trip tab and in history, so that I know who joined and can
revisit our adventure.

## Problem Statement
After #88 a trip can carry `groupTripId`, and Supabase records who started each group trip
(`group_trip_participants`). No screen shows any of it: the current trip, the history cards and the saved trip
details all render a group trip like a solo one.

## Solution Statement
One shared, stateless component, `GroupTripSection`, fed by a small presentation model, `GroupTripDetails`
(members, party size, game state). Three sources:
- **Trip tab (active trip):** the live group from #88's `GroupRepository.observeGroup` (realtime), members of the
  current group.
- **History list and saved details:** the participants of the group trip, read in **one** batched query per
  screen (`group_trip_participants` with `group_trip_id in (...)`), so the list makes no request per card.
- **Game state:** `GameState.None` for every trip today (decision 89.Q5). The model is ready for #104.

Solo trips render exactly as now. Group data that's loading, unavailable (offline, a group that ended, a guest),
or empty gets a clear state, and the rest of the trip screen stays usable.

## Metadata
**Type:** Enhancement | **Complexity:** Medium | **Systems affected:** model, data (read only), ViewModels,
current trip, history, trip details UI | **New dependencies:** none | **Schema change:** none (reads #88's tables)

## Decisions (Gate 1, 2026-11-27)
| # | Decision |
|---|----------|
| 89.Q3 | Branch `feature/89-group-trip-details` from `main` after #88 merges; PR into `main`. |
| 89.Q4 | "Trip tab" = the current-trip screen while the trip is active. "History" = the history cards **and** the saved trip details. |
| 89.Q5 | Game state shows "No active game"; the field stays optional so #104 can fill it. |
| D4 (#88) | Shared trip identity = `groupTripId`; the participants list comes from `group_trip_participants`. |

---

## Context References (read before implementing)
- The #88 plan and its merged code: `data/group/GroupRepository.kt`, `SupabaseGroupRepository.kt`, `model/group/Group.kt`, `TogetherViewModel.kt` (patterns for a ViewModel with a fake repository).
- `View/history/HistoryScreen.kt`, `HistoryContent.kt`, `HistoryPresentation.kt:22-80`, `components/TripHistoryCard.kt`: the card layout and presentation helpers.
- `View/trip_details/TripDetailsScreen.kt:41-220` (loads from `tripsStore.findCompleted`), `TripDetailsContent.kt`.
- `ui/global_ui_ellements/trip/HistoryV3.kt`, `TripDetailsV3.kt`, `TripV3Shared.kt`: the shared presentations that `HowToWriteCode` names as the owners of history and saved details.
- `View/current_trip/CurrentTripContent.kt`, `TripJourneySheet.kt`, `components/TripProgressPanel.kt`: where the trip tab shows trip info.
- `shared/src/jvmTest/.../TripScreensV3Test.kt` and `commonTest/.../ui/HistoryPresentationTest.kt`: test patterns.
- `docs/mockups/history.png`, `docs/mockups/trip_details.png`, `docs/colours.md`.

### Assumptions to verify at the start of execution
- #88 landed with the names in its plan (`GroupRepository`, `GroupSync`, `Group.partySize`, `GroupTripId`). If not, **stop and report**. Don't improvise.
- RLS lets a participant read *all* participants of their group trip (#88 Task 1 policy): covered by `groups.sql`.
- Exactly where the section goes inside `TripJourneySheet` / the `TripDetailsV3` layout is decided by reading those files. The plan names the composable and its slot, not pixel positions.

### New files
- `model/group/GroupTripDetails.kt` (`GroupTripDetails`, `GroupParticipant`, `GameState`)
- `data/group/GroupTripDetailsRepository.kt`: or two methods added to `GroupRepository` (see Task 2)
- `ViewModels/history/GroupHistoryViewModel.kt`
- `ui/global_ui_ellements/trip/GroupTripSection.kt` (+ preview in `ui/global_ui_ellements/previews/`)

### Gotchas
- **No request per card:** batch the participants for every visible group trip ID; cache by `GroupTripId` in the ViewModel.
- A solo trip (`groupTripId == null`) renders **no** section and fires no request.
- History data comes from `TripsStore`, the single source of truth. The group section is extra display data and never goes into `Trip` or saved state.
- On the trip tab, the section shows the *current group* (members can leave mid-trip). In history it shows the *participants* (who actually started the trip). Label them "Group" and "Travelled with" so they aren't confused.
- When the group ends (`GroupSync.Closed`) during a trip: "Group ended", and the trip continues (the #88 rule).
- Compose files: `// Layout settings` at the top; reuse `rnd_nav_groups`; colours from `docs/colours.md`; KDoc with `@param`.

---

## Step-by-Step Tasks

### Task 1: CREATE `model/group/GroupTripDetails.kt`
- **Implement:** `sealed interface GameState { data object None; data class Active(val name: String) }`. `GroupParticipant(userId, displayName, isCurrentUser)`. `GroupTripDetails(groupTripId, participants, gameState = GameState.None)` with `partySize = participants.size`. `sealed interface GroupSectionState { Solo; Loading; Ready(details); Unavailable(reason: Offline|Ended|SignedOut) }`.
- **Tests:** commonTest `GroupTripDetailsTest`: party size; current user listed first; empty participants → `Unavailable`, not `Ready` with 0.

### Task 2: ADD `suspend fun participants(ids: Set<GroupTripId>): Map<GroupTripId, List<GroupParticipant>>` to `GroupRepository` + Supabase implementation
- **Implement:** one select on `group_trip_participants` with `isIn("group_trip_id", ids)`, ordered by `started_at`. An empty set makes no request.
- **Tests:** extend `SupabaseGroupRepositoryTest`: one request for several IDs; no request for an empty set; RLS-hidden IDs are absent from the map (the caller shows `Unavailable`).

### Task 3: CREATE `ViewModels/history/GroupHistoryViewModel.kt`
- **Implement:** takes `GroupRepository` and the current `UserId`. `fun sectionFor(trip: Trip): GroupSectionState` reads a cached `StateFlow<Map<GroupTripId, GroupSectionState>>`. `fun load(trips: List<Trip>)` batches the group IDs that aren't loaded yet. `retry()`. Failures → `Unavailable(Offline)` per ID, never an exception into the UI.
- **Tests:** commonTest `GroupHistoryViewModelTest` with the fake repository: solo trips stay `Solo` with no call; one call for N group trips; a failure → `Unavailable`; retry → `Ready`; an already loaded ID isn't refetched.

### Task 4: CREATE `ui/global_ui_ellements/trip/GroupTripSection.kt`
- **Implement:** a stateless composable `GroupTripSection(state: GroupSectionState, title: String, onRetry: (() -> Unit)?, modifier)`. `Ready`: party size ("Party of 3"), member chips/rows (you first, the host marked when known), game line ("No active game" / the game name). `Loading`: a skeleton row. `Unavailable`: a short reason + Retry when `onRetry != null`. `Solo`: renders nothing. A compact variant for the history card (icon + party size only).
- **Tests:** jvmTest `GroupTripSectionTest`: each state's text and Retry; `Solo` emits no nodes; a long display name ellipsizes.

### Task 5: UPDATE the trip tab: `View/current_trip/` (the trip info sheet)
- **Implement:** in the current-trip screen, when the active trip has `groupTripId`, collect `observeGroup` for the user's group (via `LocalGroupRepository`, the #88 ViewModel pattern; a small `CurrentGroupViewModel` in `ViewModels/current_trip/` if `TogetherViewModel` doesn't fit) and map `GroupSync` → `GroupSectionState` (`Live` → Ready with members; `Reconnecting` → the last members + a "Reconnecting" note; `Closed` → `Unavailable(Ended)`). Place `GroupTripSection(title = "Group")` in the journey/info sheet.
- **Tests:** commonTest mapping test for `GroupSync` → `GroupSectionState`; jvmTest: an active solo trip shows no group section (regression).

### Task 6: UPDATE history: `HistoryScreen`/`HistoryContent`/`TripHistoryCard` (and `HistoryV3.kt` if it renders the card)
- **Implement:** the history screen gets a `GroupHistoryViewModel` (signed-in users only; guests → every group trip is `Unavailable(SignedOut)`, though in practice guests have no group trips) and calls `load` with the history trips. The card shows the compact section (group icon + "Party of N") for group trips. Solo cards are unchanged.
- **Tests:** extend `HistoryPresentationTest` / jvmTest: a group card shows the party size; a solo card is unchanged.

### Task 7: UPDATE saved details: `TripDetailsScreen`/`TripDetailsContent` (and `TripDetailsV3.kt`)
- **Implement:** add a "Travelled with" section with the full `GroupTripSection` for group trips, using the same ViewModel pattern (one ID). The rename/edit/delete flows are untouched. The shared trip identity stays visible (the `groupTripId` link is kept on edit, since `updateTrip` only changes title and description).
- **Tests:** jvmTest: the details of a group trip list the participants and "No active game"; unavailable data shows a reason and the details stay editable; a solo trip is unchanged.

### Task 8: Previews, screenshots, docs, AI log
- Previews for each `GroupSectionState`. Before/after screenshots from the Android emulator go in the PR (the DoD asks for evidence). Add a #89 entry to the ML4 AI logs per `HowToLog_AI.md`, referencing the McGill pipeline.

---

## Testing Strategy
| Acceptance criterion | Test | Level |
|---|---|---|
| S1: active group trip → trip tab shows members, party size, game state | `GroupSync` mapping test; `GroupTripSectionTest` Ready; manual two-device check | unit, UI, manual |
| S1: history entry → members, party size, game state | `GroupHistoryViewModelTest`; history card + details UI tests | unit, UI |
| S2: solo trip remains usable | solo regression tests in the trip tab, history and details (no section, no request) | UI, unit |
| S2: unavailable group data → clear state | `GroupHistoryViewModelTest` failure; `GroupTripSectionTest` Unavailable + Retry; `Closed` → Ended | unit, UI |

## Validation Commands
1. `./gradlew :shared:jvmTest :shared:testAndroidHostTest`: all pass (baseline = the totals after #88 merges)
2. `./gradlew :androidApp:assembleDebug :desktopApp:classes :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :androidApp:lintDebug`
3. Manual on two devices with #88's migration applied: start a group trip → both trip tabs show 2 members, "Party of 2", "No active game" → complete it → both histories show the group badge and details list both names → a solo trip looks unchanged → airplane mode → details show "Group details unavailable" + Retry, and the trip is still editable.

## Acceptance Criteria
- [ ] Given an active group trip, when I open the trip tab or its history entry, then members, party size and active/no game state appear.
- [ ] Given a solo trip or unavailable group data, when I open details, then solo trips remain usable and missing data has a clear state.

## Out of Scope
- Real games (#104) and the map markers (#90).
- Editing who took part, or reviewing a group trip together.

## Gate 2 answers (2026-11-27)
- The user: "For the active trip and the history for it, just add an extra section for the group info such as who
  is/was participating." So: an extra section in the existing screens, no redesign; built with the existing palette.
- OQ2 → default: the history card shows "Party of N" only; names go in the details.

## Confidence: 7/10 for one pass
The data access is read-only and small, and the UI is one shared component in three places. The risk is #88 landing
differently (the plan stops if it does) and fitting the section into the existing sheets without breaking their tuned layouts.
