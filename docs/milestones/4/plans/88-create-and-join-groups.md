# Plan: Create and join groups through the backend (Issue #88, ML4-02.S1)

> Workflow: R-PIV pipeline from the McGill Portal project (`McGillPortal_Project/.claude/skills/`:
> triage → prime → plan-feature → execute → validate → code-review → open-pr → reflect), adapted to
> GitHub, Gradle and Supabase. This plan is the plan-feature output; no code is written before it is approved.
> Validate the patterns referenced here before implementing. Match existing names and packages exactly.

## User Story
As a signed-in traveller, I want to create a group or join one by code, so that I can share a trip with friends.

## Problem Statement
No group exists anywhere: `model/group/`, `View/together/` and `ViewModels/together/` are empty, the Together
tab in `SharedBottomBar` is disabled because nothing passes `onOpenTogether`, and Supabase has no group tables.
Trips are only saved when completed or cancelled, and every table is owner-only, so members cannot see each other.

## Solution Statement
- **Supabase:** `groups`, `group_members`, `group_trips` (the shared route, one row per group trip) and
  `group_trip_participants` (who started the group trip). Clients only **read** them through RLS; every write goes
  through `security definer` functions that check membership and host rights. `trips` gets `group_trip_id`; a
  member's history row points to the shared group trip and no longer stores its own route (decision A).
- **Realtime:** `realtime-kt` is installed. Changes to `groups` / `group_members` are invalidation signals: the
  repository refetches the group snapshot through PostgREST after every event (DELETE events carry no row data
  under RLS, so the event payload is never trusted).
- **App:** `GroupRepository` (+ Supabase implementation), `TogetherViewModel`, a Together screen (create, join by
  code, members, leave, host shares the active trip, members start the group trip), `Trip.groupTripId`.

## Metadata
**Type:** New capability | **Complexity:** High | **Systems affected:** Supabase schema, data, model, state,
navigation, Together UI | **New dependency:** `io.github.jan-tennert.supabase:realtime-kt:3.6.0` (same version
as the existing auth/postgrest modules; artifacts exist for android, jvm, iOS, js and wasm-js).

## Decisions (Gate 1, 2026-11-27)
| # | Decision |
|---|----------|
| D1 | #88 is done before #89 (#89 depends on it). Branch `feature/88-create-join-groups` from `main`, PR into `main`. |
| D2 | The pipeline is referenced, not copied into this repo; it is cited in the ML4 AI logs. |
| D3 | Sync uses Supabase Realtime (#90's map markers need it too). |
| D4 | Shared trip = `group_trips` table; each participant has their own `trips` row pointing to it (`group_trip_id`). Route columns live only in `group_trips` for group rows (option A). |
| D5 | Join code: 6 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, server-generated, retried on collision, expires when the host leaves; no time limit. |
| D6 | Member names are copied into the membership rows at join time. Refinement: the **server** copies `users.username` (fallback `Traveller`), so a client cannot spoof another name. |
| D7 | UI: minimal Together screen. The host shares the active trip; other members get "Start group trip". |

**Correction to the Q2 discussion:** I said the history row could double as the participation record. It
can't: `trips` is owner-only, so a member can't see the *other* participants' rows, and #89 needs their names.
`group_trip_participants` holds that list. It also decides who may read a group trip after leaving the group.

---

## Context References (read before implementing)
- `supabase/migrations/20261108154350_create_trips_and_reviews.sql`: table, grant and RLS style to mirror (revoke all, column-level update grants, `(select auth.uid())`).
- `supabase/migrations/20261109160000_save_trip_review.sql`: function style (`set search_path = ''`, revoke from public/anon, grant to authenticated).
- `supabase/migrations/20261114234345_add_trip_generation_metadata.sql` and `20261122234044_add_trip_journey_metadata.sql`: the columns that become shared route columns.
- `supabase/tests/trip_journey_metadata.sql`: rollback-only SQL test pattern.
- `shared/build.gradle.kts:12,87-88`: `supabaseVersion`, where the realtime dependency goes.
- `data/supabase/SupabaseBackend.kt`: install `Realtime` next to `Auth` and `Postgrest`.
- `data/trips/SupabaseTripRepository.kt` (whole file): save payload, `historyColumns`, `decodeTrips`, identity check at lines 63-67.
- `data/remote/dto/TripDto.kt` (whole file): DTO and mappings.
- `model/trip/Trip.kt:74-130`: the `Trip` constructor and its `init` rules.
- `model/ids/Ids.kt`: ID class pattern (`@Serializable @JvmInline value class ... : PlatformSerializable`, nonblank check).
- `state/trip/TripsStore.kt:181-238` (`startTrip`) and `:620-700` (saved-state JSON with `ignoreUnknownKeys = false`).
- `ViewModels/Accounts/ProfileViewModel.kt`: ViewModel pattern (`MutableStateFlow` + `asStateFlow`, `viewModelScope`, rethrow `CancellationException`).
- `navigation/ScreenKeys.kt` (keys + polymorphic registration at ~150), `navigation/Router.kt:200-245` (entries), `layout/MainLayout.kt:30-60,110-115` (tabs).
- `App.kt:180-262`: per-identity repositories and `CompositionLocalProvider`.
- `shared/src/jvmTest/.../data/trips/SupabaseTripRepositoryTest.kt:64-90`: `HttpServer` fake for PostgREST.
- `shared/src/commonTest/.../state/SavedStateMigrationTest.kt`, `.../data/TripDtoTest.kt`, `.../data/CloudTripsTest.kt`.

### Assumptions and how they were checked
- realtime-kt 3.6.0 exists for every target in `shared/build.gradle.kts`: checked on Maven Central (`realtime-kt-{android,jvm,js,wasm-js,iosarm64,iossimulatorarm64,iosx64}`).
- The Ktor engines already used (OkHttp, Darwin, Js) support WebSockets: true per Ktor docs; **verify** with `:androidApp:assembleDebug`, `:webApp:compileKotlinWasmJs`, `:desktopApp:classes` in Task 3.
- `TripsStore` saved state uses `ignoreUnknownKeys = false`: a **new optional field with a default** still decodes old JSON (missing keys use defaults); the old app reading new JSON is not a supported path. Covered by a migration test in Task 5.
- NULL passes the existing `CHECK` constraints on `legs`, `transports`, etc. (a CHECK that evaluates to NULL passes): standard Postgres behavior; covered by the SQL test.
- Realtime postgres-changes are filtered by the subscriber's RLS SELECT policy, and DELETE events are not filterable: Supabase Realtime docs ("Postgres Changes", "Delete events are not filterable"). That's why events only trigger a refetch.

### New files
- `supabase/migrations/20261127200000_create_groups.sql`
- `supabase/tests/groups.sql`
- `model/group/GroupJoinCode.kt`, `model/group/Group.kt` (Group, GroupMember)
- `data/group/GroupRepository.kt` (interface, `GroupFailure`), `data/group/SupabaseGroupRepository.kt`
- `data/remote/dto/GroupDto.kt`
- `ViewModels/together/TogetherViewModel.kt` (+ `TogetherUiState`)
- `View/together/TogetherScreen.kt`, `View/together/TogetherContent.kt`, `View/together/components/GroupMembersList.kt`, `View/together/components/JoinGroupForm.kt`, `View/together/previews/TogetherScreenPreview.kt`
- Tests listed in the Testing Strategy.

### Gotchas
- **Never trust realtime payloads**: treat every event as "refetch". Subscribe *before* the first fetch, so no change between fetch and subscribe is lost.
- **RLS recursion:** a `group_members` policy that queries `group_members` recurses. Use `security definer` helpers `private.is_group_member(gid)` / `private.is_group_trip_participant(gtid)` in a non-exposed schema.
- `supabase_realtime` publication only exists on Supabase; wrap `alter publication` in a `do` block that checks `pg_publication`, so the SQL test also runs on plain Postgres.
- Join codes are compared upper-case. Keep the alphabet in **one** place per side (SQL function + `GroupJoinCode`) and test that they match.
- `trips.group_trip_id` must not be in the update grant (immutable, like `owner_id`).
- The trip repository's retry identity check (`SupabaseTripRepository.kt:63-67`) must include `groupTripId`.
- A member's local `Trip` keeps the full route (needed offline and by `TripsStore`); only the *stored* row drops it.
- Return journeys reference the member's own outbound trip, so `group_trips.journey` allows only `round_trip` (or null). A return trip of a group trip is solo.
- Use `_` in Kotlin names, not `-` (`HowToWriteCode_InTheProject.md` §1.0). KDoc with `@param` on every declaration. Put `// Layout settings` / `// Animation settings` at the top of each Compose file.
- Guests (no account) see a "Sign in to use groups" state; they don't get a repository or a ViewModel (same as Profile/Settings).

---

## Step-by-Step Tasks

### Task 1: CREATE `supabase/migrations/20261127200000_create_groups.sql`
- **Implement** (one transaction):
  - `create schema if not exists private;` revoke usage from anon/authenticated.
  - `groups(id uuid pk default gen_random_uuid(), host_id uuid not null references auth.users, join_code text not null check (join_code ~ '^[A-HJ-NP-Z2-9]{6}$'), created_at timestamptz default now(), closed_at timestamptz, current_group_trip_id uuid)`; partial unique index `groups_open_code_key on groups(join_code) where closed_at is null`.
  - `group_members(group_id uuid references groups on delete cascade, user_id uuid references auth.users, display_name text not null check (display_name ~ '[^[:space:]]'), joined_at timestamptz default now(), primary key (group_id, user_id))`; index on `user_id`.
  - `group_trips(id uuid pk default gen_random_uuid(), group_id uuid not null references groups, created_by uuid not null references auth.users, title, description, image_url, origin, destination, legs, planned_minutes, transports, routes, attraction_intensity, zone_ids, zones_by_transport, routed_duration_seconds, waiting_time_seconds, walking_preference, orientation_buffer_seconds, requested_transport_ids, journey, created_at)`, with the **same checks** as `trips` for those columns, and `journey is null or journey->>'kind' = 'round_trip'`. FK `groups.current_group_trip_id → group_trips(id)` added after.
  - `group_trip_participants(group_trip_id uuid references group_trips on delete cascade, user_id uuid references auth.users, display_name text not null, started_at timestamptz default now(), primary key (group_trip_id, user_id))`.
  - `trips`: `add column group_trip_id uuid references group_trips(id)`; `alter column origin, destination, legs, planned_minutes, transports, routes, attraction_intensity drop not null`; constraint `trips_route_owner check ((group_trip_id is null) = (origin is not null and destination is not null and legs is not null and planned_minutes is not null and transports is not null and routes is not null and attraction_intensity is not null))`. Index on `group_trip_id`.
  - Helpers in `private` (security definer, `set search_path = ''`, stable): `is_group_member(gid)`, `is_group_trip_participant(gtid)`, `display_name()` (`coalesce(nullif(trim(users.username),''),'Traveller')`).
  - RLS on all four tables; `revoke all ... from public, anon, authenticated`; `grant select ... to authenticated`; **no** insert/update/delete grants. Policies:
    - `groups`, `group_members`: `using (private.is_group_member(group_id))`
    - `group_trips`: `using (private.is_group_member(group_id) or private.is_group_trip_participant(id))`
    - `group_trip_participants`: `using (private.is_group_trip_participant(group_trip_id))`
  - `trips`: an extra **restrictive** insert policy: `with check (group_trip_id is null or private.is_group_trip_participant(group_trip_id))`.
  - Functions (security definer, `set search_path = ''`, revoke from public/anon, grant to authenticated). Errors are raised with fixed messages the app maps: `GROUP_CODE_INVALID`, `GROUP_ALREADY_JOINED`, `GROUP_NOT_HOST`, `GROUP_NOT_MEMBER`, `GROUP_CODE_GENERATION_FAILED`, `GROUP_TRIP_INVALID`.
    - `create_group() returns groups`: rejects a user already in an open group; up to 10 attempts at a random code (`gen_random_bytes` mapped into the alphabet), catching `unique_violation`; inserts the host membership.
    - `join_group(p_code text) returns groups`: open group with `upper(trim(p_code))`, else `GROUP_CODE_INVALID`. Already a member of **this** group: returns it (reconnect is idempotent). Member of another open group: `GROUP_ALREADY_JOINED`.
    - `leave_group(p_group_id uuid) returns void`: the host leaving sets `closed_at = now()` and deletes all memberships (the code is freed and expires). A member leaving deletes their own row. Not a member: no-op.
    - `share_group_trip(p_group_id uuid, p_trip jsonb) returns group_trips`: host of an open group only (`GROUP_NOT_HOST`); inserts the group trip from the jsonb (shared columns only), sets `current_group_trip_id`, adds the host as participant.
    - `start_group_trip(p_group_trip_id uuid) returns group_trips`: caller must be a member of the trip's open group (`GROUP_NOT_MEMBER`) and it must be the group's current trip (`GROUP_TRIP_INVALID`); adds the participant (on conflict do nothing) and returns the trip.
  - Realtime: `do $$ begin if exists (select 1 from pg_publication where pubname='supabase_realtime') then alter publication supabase_realtime add table public.groups, public.group_members; end if; end $$;`
  - `notify pgrst, 'reload schema';`
- **Pattern:** the trips/reviews and save_trip_review migrations.
- **Validate:** Task 2's SQL test.
- **Checkpoint:** show the table/function list and the RLS summary.

### Task 2: CREATE `supabase/tests/groups.sql`
- **Implement:** a rollback-only script mirroring `trip_journey_metadata.sql`. Fixture users A (host), B, C; impersonate with `set local role authenticated` + `set local request.jwt.claims`. Asserts (with `do $$ ... assert ... $$`):
  create returns a 6-char code from the alphabet; B joins with a lower-case code; B joining twice is idempotent; C with a wrong code raises `GROUP_CODE_INVALID`; B can't create a second group while in one; C (non-member) sees 0 rows in all group tables; B can't `insert`/`update` `groups`/`group_members` directly (permission denied); B can't `share_group_trip` (`GROUP_NOT_HOST`); A shares, B starts; B inserts a `trips` row with `group_trip_id` and null route, C can't; a group row **with** route columns violates `trips_route_owner`; a solo row without a route violates it; A leaves → group closed, memberships gone, the code can be reused, B joining the old code gets `GROUP_CODE_INVALID`; B still reads the group trip afterwards (participant); `group_trip_id` is not updatable.
- **Update** `supabase/README.md`: add the migration and test to the list.
- **Validate:** run against Postgres if Docker is available (`docker run postgres:17` + a minimal `auth.users`/`auth.uid()` shim script under `supabase/tests/`). Otherwise mark it **not run** in the report. Never apply to the live project (the user does that).

### Task 3: UPDATE `shared/build.gradle.kts` and `data/supabase/SupabaseBackend.kt`
- **Implement:** `implementation("io.github.jan-tennert.supabase:realtime-kt:$supabaseVersion")` in commonMain; `install(Realtime)`.
- **Validate:** `./gradlew :shared:compileKotlinJvm :androidApp:assembleDebug :desktopApp:classes :webApp:compileKotlinJs :webApp:compileKotlinWasmJs`

### Task 4: ADD `GroupTripId` to `model/ids/Ids.kt`; CREATE `model/group/GroupJoinCode.kt`, `model/group/Group.kt`
- **Implement:** `GroupTripId` following `GroupId`. `GroupJoinCode` value class: normalizes `trim().uppercase()`, `require` length 6 and the alphabet, `companion fun parseOrNull(text): GroupJoinCode?`, `const val ALPHABET`. `Group(id: GroupId, joinCode: GroupJoinCode, hostId: UserId, members: List<GroupMember>, currentTripId: GroupTripId?)` with `isHost(userId)` and `partySize`. `GroupMember(userId, displayName, joinedAt: Long)`.
- **Tests:** `commonTest/model/GroupModelsTest.kt`; extend `IdsTest`/`IdsBundleTest` for `GroupTripId` (the Bundle test is required by the ID rule).
- **Validate:** `./gradlew :shared:jvmTest --tests "*GroupModelsTest" --tests "*IdsTest"` and `:shared:testAndroidHostTest --tests "*IdsBundleTest"`

### Task 5: UPDATE `model/trip/Trip.kt`, `data/remote/dto/TripDto.kt`
- **Implement:** `Trip.groupTripId: GroupTripId? = null` (KDoc: points to the shared route; null for solo trips). `TripDto.groupTripId` (`group_trip_id`) mapped both ways.
- **Tests:** `TripDtoTest` round trip with and without `groupTripId`; `SavedStateMigrationTest` decodes a saved state without the field.
- **Validate:** affected test classes.

### Task 6: UPDATE `data/trips/SupabaseTripRepository.kt`
- **Implement:**
  - `SHARED_ROUTE_FIELDS` constant: the column names listed in Task 1.
  - Save: when `groupTripId != null`, remove the shared fields from the payload (keep `title`, `description`, `image_url`, status and timestamps).
  - Read: `historyColumns` adds `group_trip:group_trips(<shared fields>)`. In `decodeTrips`, when `group_trip` is an object, overlay its shared fields onto the row before decoding. A group row whose `group_trip` is missing (no access) is skipped and logged, not a crash: `requireTrip` would throw.
  - Identity check also compares `groupTripId`.
- **Tests (jvmTest, extend `SupabaseTripRepositoryTest`):** the group save payload has no route keys and has `group_trip_id`; the solo payload is unchanged; a read overlays the group route; a group row without an embedded trip is skipped; a retry with a different `groupTripId` is rejected.
- **Validate:** `--tests "*SupabaseTripRepositoryTest" --tests "*CloudTripsTest"`

### Task 7: CREATE `data/group/GroupRepository.kt`, `data/remote/dto/GroupDto.kt`, `data/group/SupabaseGroupRepository.kt`
- **Implement:**
  - Interface: `suspend fun currentGroup(): Group?`, `createGroup(): Group`, `joinGroup(code: GroupJoinCode): Group`, `leaveGroup(id: GroupId)`, `shareTrip(id: GroupId, trip: Trip): GroupTripId`, `startGroupTrip(id: GroupTripId): Trip` (returns the shared route as a template), `fun observeGroup(id: GroupId): Flow<GroupSync>`, where `GroupSync` is `Live(group)` / `Closed` / `Reconnecting`. `GroupFailure` (sealed exception): `InvalidCode`, `AlreadyInGroup`, `NotHost`, `NotMember`, `CodeGenerationFailed`, `TripUnavailable`, `Network`.
  - Supabase: RPC calls via `client.postgrest.rpc`; map `RestException` messages containing the `GROUP_*` codes to `GroupFailure`; network/timeouts → `Network`. The group snapshot is a select `groups` with `members:group_members(...)` where open and the member is me. `observeGroup`: `channel("group:<id>")`, `postgresChangeFlow<PostgresAction>` for `groups` (filter `id=eq.<id>`) and `group_members` (filter `group_id=eq.<id>`), merged. Subscribe first, then emit a fetch, then refetch on every event (`conflate`). Channel status not `SUBSCRIBED` → emit `Reconnecting` and refetch on resubscribe. Unsubscribe in `finally`/`awaitClose`.
  - `startGroupTrip` builds the member's own `Trip`: new `TripId(Uuid.random())`, `ownerId = me`, `status = Generated`, `createdAt = now`, `groupTripId = id`, the route from the row.
- **Tests (jvmTest `SupabaseGroupRepositoryTest`, `HttpServer` fake on `/rest/v1/rpc/*` and `/rest/v1/groups`):** each `GROUP_*` error maps to its failure; join sends the normalized code; `startGroupTrip` returns a Generated trip with a fresh ID and the group trip ID; the snapshot decodes members and `currentTripId`. Realtime itself isn't unit-tested: the `observeGroup` refetch logic sits behind a small `GroupChangeSource` interface, so a fake channel can be tested (event → refetch, status → `Reconnecting`).
- **Validate:** `--tests "*SupabaseGroupRepositoryTest" --tests "*GroupSyncTest"`

### Task 8: UPDATE `state/trip/TripsStore.kt`
- **Implement:** `fun attachGroupTrip(tripId: TripId, groupTripId: GroupTripId): TripActionResult`. Only for the active trip, and only while its `groupTripId` is null (otherwise `InvalidInput`). It replaces the active trip with the copy.
- **Tests:** commonTest `TripsStoreGroupTest`: attaches to the active trip; rejects a missing/non-active trip or a second attach; the attachment survives `toSavedStateJson`/`fromSavedStateJson`.

### Task 9: CREATE `ViewModels/together/TogetherViewModel.kt`
- **Implement:** `TogetherUiState(loading, group: Group?, isHost, codeInput, codeValid, busy, failure: GroupFailure?, connection: Live|Reconnecting, canShareActiveTrip, canStartGroupTrip)`. Constructor: `GroupRepository`, `TripsStore`, `UserId`. `init` loads `currentGroup()` and collects `observeGroup`. Actions: `onCodeChange`, `create`, `join`, `leave`, `shareActiveTrip` (`repository.shareTrip` then `tripsStore.attachGroupTrip`), `startGroupTrip(): Trip?` (`repository.startGroupTrip` then `tripsStore.start`; returns the trip so the screen navigates), `retry`, `dismissFailure`. Busy actions ignore duplicate taps. `Closed` (the host left) → group cleared + a "group ended" message; the local active trip is untouched.
- **Tests:** commonTest `TogetherViewModelTest` with a `FakeGroupRepository` and `runTest`: create shows the code; an invalid code is disabled client-side; a server `InvalidCode` shows its message; a double-tap creates once; live member updates appear; `Closed` clears the group and keeps the active trip; share attaches the group trip; start begins a trip carrying `groupTripId`; `Reconnecting` is shown and cleared.

### Task 10: CREATE the Together UI and navigation
- **Design (Gate 2, provided by the user):** the "Explore together" mockup (390×842, saved as `docs/mockups/together.png`).
  `V3Page` + `V3Heading("Explore together")` (title band with the GO pill, `ui/global_ui_ellements/trip/TripV3Shared.kt:59-140`);
  a teal (`TransitComplementary`) panel with the RND logo (`rnd_logo_on_blue`), "Bring friends along", body text,
  the lime `rnd_nav_groups` icon, a blue primary button ("Sign in to create a group" for guests → `AuthScreenKey`;
  "Create a group" when signed in) and a white outlined "Join with a code" button (opens the code field);
  a lime (`TransitSelected`) "Shared choices" band with teal text; an outlined "Game & quests preview" button,
  **disabled** with a "coming soon" description (#104 is research); a teal footer note. Once in a group, the teal
  panel shows the code, members and actions instead of the two buttons.
- **Implement:** `TogetherScreenKey` (+ polymorphic registration); a `Router` entry building the ViewModel from `LocalGroupRepository`, `LocalTripsStore` and the signed-in user (guests get `TogetherContent` with a sign-in prompt and no ViewModel); `MainLayout` defaults `onOpenTogether` to `navigator.open(TogetherScreenKey)` (like Compass) and shows `Together` as selected on that key. `App.kt`: `LocalGroupRepository` scoped per owner, like `accountRepository`. `TogetherContent`: no group → Create button + `JoinGroupForm`; in a group → the code (large, for sharing), `GroupMembersList` (host marked, party size), share/start buttons per state, Leave (host: confirm that the group ends for everyone), a reconnecting banner, and error text from `GroupFailure`. After starting a group trip: `navigation.openCurrentTrip(trip)`. Colours from `docs/colours.md`; reuse the existing `rnd_nav_groups` icon.
- **Tests:** jvmTest `TogetherContentTest` (compose UI test like `TripScreensV3Test`): the empty state shows Create/Join; the join button is disabled until the code is valid; the members list and party size render; the guest state renders; the Leave confirm appears for the host.
- **Validate:** affected tests + `:androidApp:assembleDebug`.

### Task 11: Docs and AI log
- `supabase/README.md` (Task 2). Add a #88 entry to `docs/milestones/4/AI/ML4-AI-Decision-Log.md` and `ML4-AI-Technique-Log.md` per `docs/HowToLog_AI.md`: the decisions D1-D7, the prompts, and a reference to the McGill pipeline as the workflow used (D2). Use `[Conversation link needed]` unless you provide the link.

---

## Testing Strategy
| Acceptance criterion | Test | Level |
|---|---|---|
| S1: two clients create/join, membership and shared trip consistent | `groups.sql` (create, join, share, start, participant reads); `TogetherViewModelTest` live updates/share/start; `SupabaseGroupRepositoryTest` start builds the member trip; manual two-device check | SQL, unit, manual |
| S2: expired code rejected with clear feedback | `groups.sql` old code after the host leaves; repository error mapping; ViewModel shows `InvalidCode` | SQL, unit |
| S2: unauthorized update rejected | `groups.sql` direct insert/update denied, non-host share denied, non-participant trip insert denied, non-member sees nothing | SQL |
| Host exit | `groups.sql` close + code reuse; ViewModel `Closed` keeps the active trip | SQL, unit |
| Code expiry / generation failure | `groups.sql` code alphabet; repository maps `GROUP_CODE_GENERATION_FAILED`; `GroupJoinCode` alphabet matches the SQL regex | SQL, unit |
| Reconnect | `GroupSyncTest` status → `Reconnecting` → refetch; `join_group` idempotent | unit, SQL |
| History stays per member (no regression) | `SupabaseTripRepositoryTest` solo unchanged + group overlay; existing suites green | unit |

Security checks (break and restore once per rule, file copied to the scratchpad first and restored byte for byte; never `git checkout`/`git restore`/`sed`): drop the `GROUP_NOT_HOST` check, confirm `groups.sql` fails, restore; drop the restrictive trips insert policy, confirm it fails, restore. Only possible if the SQL test can run.

## Validation Commands
1. `./gradlew :shared:jvmTest :shared:testAndroidHostTest` (baseline 341 + 279, all pass)
2. `./gradlew :backend:test :backend:launcher:test` (CI parity, should be unaffected)
3. `./gradlew :androidApp:assembleDebug :desktopApp:classes :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :androidApp:lintDebug`
4. `supabase/tests/groups.sql` on a disposable Postgres (if Docker runs)
5. Manual: apply the migration to the RNDTransitMTL Supabase project (**user only**), enable it, then two signed-in devices: create → join → members appear live on both → host shares the trip → member starts it → host leaves → the member sees "group ended" and the old code fails.

## Acceptance Criteria
- [ ] Given two signed-in clients, when one creates and the other joins, then membership and shared trip are consistent.
- [ ] Given an expired code or unauthorized update, when a request is submitted, then the server rejects it with clear feedback.
- [ ] Host exit, code expiry, reconnect and generation failure are tested.

## Out of Scope
- Group members' map markers and location sharing (#90); group details in the trip tab and history (#89); games (#104).
- Push notifications or invite links (deep links could be added later).
- Changing a group trip after it's shared.

## Gate 2 answers (2026-11-27)
- OQ1 → default: yes, a member who leaves mid-trip still saves history pointing to the group trip.
- OQ2 → the user provided the Together mockup (see Task 10). The issue asks for "everyone agrees to the same trip
  choices"; this slice implements "the host plans and shares the trip" and keeps the mockup copy. Agreeing on
  choices in the app is a follow-up, not part of #88.
- Plans approved with that change; all tasks run in a row, one commit per task, and nothing is pushed before Gate 3.

## Confidence: 6/10 for one pass
The SQL and repository parts are well specified and match existing patterns. The risks are the realtime API details (subscription status and reconnection on each platform), the untested SQL if Docker isn't running, and the size: 11 tasks across 6 layers, three days before the sprint ends.
