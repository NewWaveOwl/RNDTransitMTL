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
