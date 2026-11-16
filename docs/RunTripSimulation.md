# Run routed trips with simulated progress

Use the [backend generation guide](../backend/TripGeneration.md) to start OTP, the STM/exo
realtime bridge and Ktor, then install Android. The app now generates routes only
through the actual backend when running with the live Android map. The old fixed
STM/exo endpoints, test buttons, launch intents and direct phone-to-OTP client have
been removed. There is no mock-route fallback on Android if the backend is unavailable.

Confirm an origin, choose time, modes, transit zones and walking pace, then press
**GO**. Review the route before **Start Trip**. **Generate another** keeps the origin
and requests a different destination. Active trip progress is still simulated;
this change does not implement GPS navigation.

Each trip leg has a distinct colour, including separate walking sections. The same
colours appear on Android's live map, the bundled map, and the **Trip sections** key
in trip information. The colour remains visible beneath the thin travelled overlay.

During playback, the information box shows **Section N of M**, **Now**, and **Next**.
Street instructions use OTP's saved steps (turn direction and street name). Transit
instructions use the provider's route number, headsign, boarding and alighting stop
when available. Before a transit section, **Next** tells the user which service to
wait for and board. Instructions follow the simulated map position; waiting time
and actual boarding are not detected. Older saved trips without steps show their
saved mode/line and the next section without inventing street names.

To check: generate a walk/transit trip, start it, and watch the section number and
instructions change at each leg boundary. Check a named turn on a walking section,
the bus number before boarding, the alighting stop while riding, and the final walk
to the destination. Open trip information to compare its section colours with the map.
Restart Ktor after updating so its OTP query includes the navigation fields.

The bridge's offline fixtures and live-feed diagnostics remain for testing real-time
freshness, authentication failures and scheduled fallback. They do not add test
journeys to the app.
