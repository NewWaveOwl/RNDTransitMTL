# Run routed trips with simulated progress

Use the [backend generation guide](BackendTripGeneration.md) to start OTP, the STM/exo
realtime bridge and Ktor, then install Android. The app now generates routes only
through the actual backend when running with the live Android map. The old fixed
STM/exo endpoints, test buttons, launch intents and direct phone-to-OTP client have
been removed. There is no mock-route fallback on Android if the backend is unavailable.

Confirm an origin, choose time, modes, transit zones and walking pace, then press
**GO**. Review the route before **Start Trip**. **Generate another** keeps the origin
and requests a different destination. Active trip progress is still simulated;
this change does not implement GPS navigation.

The bridge's offline fixtures and live-feed diagnostics remain for testing real-time
freshness, authentication failures and scheduled fallback. They do not add test
journeys to the app.
