# Trip persistence schema

Apply **all** files in `migrations/` in timestamp order to the Supabase project
used by this Android app before deploying a new client. The routing server's OTP
graph and Windows launcher are separate from the Supabase trips database.

The generation-metadata migration adds `routed_duration_seconds`,
`waiting_time_seconds`, `walking_preference`, `orientation_buffer_seconds` and
`requested_transport_ids`. These must exist even when their DTO values are null:
the repository serializes default values on every save. Existing trips retain
null metadata and a zero orientation reserve. The migration extends only the
existing editable-column grants; owner/id protection and row policies remain.

With Supabase CLI installed, check `supabase db push --help`, verify the linked
project is **RNDTransitMTL**, then apply pending migrations using the CLI's linked
project mode. Do not apply them to another application's database. Alternatively,
run each pending SQL migration in the target project's SQL editor and coordinate
migration history before subsequent CLI deployment.

## Verification

On a disposable/local Supabase database with all migrations applied, run
`tests/trip_generation_metadata.sql` as the database administrator. It creates
temporary fixtures inside a transaction, exercises authenticated owner writes,
legacy defaults, metadata round-trip, constraints, column privileges and owner
isolation, then rolls everything back. Run with stop-on-error enabled when using
psql. This test is not a substitute for a PostgREST integration check.

On the configured app project, complete and save a trip while signed in, reopen
it from History, and check its timing and requested transport choices. Repeat
with a cancelled trip that the user chooses to keep. Verify another account
cannot see either trip. A successful generation request only verifies routing,
not cloud persistence.

## Live groups (#88)

`20261127200000_create_groups.sql` adds `groups`, `group_members`, `group_trips`
(the shared route) and `group_trip_participants`, plus `trips.group_trip_id`. A
member's History row for a group trip stores no route of its own; it points to
the group trip. App users can only read the group tables, and only for groups
they belong to or group trips they started. Every write goes through
`create_group`, `join_group`, `leave_group`, `share_group_trip` and
`start_group_trip`. They raise fixed messages (`GROUP_CODE_INVALID`,
`GROUP_ALREADY_JOINED`, `GROUP_NOT_HOST`, `GROUP_NOT_MEMBER`, `GROUP_TRIP_INVALID`,
`GROUP_CODE_GENERATION_FAILED`, `GROUP_NOT_SIGNED_IN`), which the app maps to
messages. The migration adds `groups` and `group_members` to the
`supabase_realtime` publication when it exists.

Run `tests/groups.sql` the same way as the other tests. It covers create/join,
idempotent rejoin, wrong and expired codes, direct writes being denied, the
host-only share, participants saving History, outsiders seeing nothing, and the
host leaving. Without the Supabase CLI, a plain Postgres container works: run
`tests/support/plain_postgres_auth_shim.sql` first, then every migration in
order, then the test.
