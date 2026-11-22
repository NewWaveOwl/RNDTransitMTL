# Debug trip simulation

The app starts in **Normal app use**. On Android, accepted trips use the device's
fresh GPS readings for the position marker, route progress and street guidance.
The recenter button requests location permission when needed and follows the
actual position; panning the map stops following until recenter is tapped again.
Recenter uses the same eased camera animation as the main Android map. Dragging
the trip panel only reveals or covers the stationary map; it does not pan, zoom,
or recenter the camera. The compact handle strip leaves more room for trip details.
Brief GPS gaps do not show an error. During a gap, **Estimated position** follows
the saved route using the last reliable movement. It stays
within the current section and never confirms a transfer or completes the trip.
After more than 30 seconds without a reliable reading, **Location is not updating**
appears while the estimated marker can continue within the current section.
It stops before a section boundary or arrival. Fresh GPS immediately replaces the estimate.
No estimate is made without a reliable movement vector, off-route, after permission
denial, or on fallback geometry. Stationary devices still request regular GPS updates.

Drag the trip panel down to leave more room for the map, or up to reveal more
trip information. It follows the finger continuously and stays at any intermediate
height when released. Both the handle and the panel body support dragging; tapping
the handle does not toggle it. At maximum height, the contents can scroll.
The blue instruction box starts with
a compact **Start on [street]** or **Continue on [street]** hint. Tap it to expand
**Section**, **Now** and **Next** with a downward expansion animation, and tap again
to retract them upwards. The compact street hint stays at the top. Street names come
from the accepted route; older routes without street instructions use their saved
origin/destination and mode labels.

Triple-tap the RND Transit logo in the header
or on the sign-in screen to open **Debug mode**, then choose **Trip simulation**
to enable the existing ten-second trip playback. A single header-logo tap still
returns to Main after the tap timeout. The debug menu does not navigate away from
the current trip.

Triple-tap again and choose **Normal app use** to stop playback. Opening the menu
pauses playback; dismissing it keeps the previous choice. The selection survives
screen navigation and saved-state recreation, but is not a persisted preference
for a new app launch. Switching modes preserves the active trip and its saved
progress. Normal use has no demo **Finish trip** action. **Cancel** remains
available if location is missing or permission is denied. GPS distance progress
is stored separately from the debug playback clock and survives saved-state
recreation. Desktop/preview hosts without a location adapter retain their route
and show **Trip progress paused**.

Use the [backend generation guide](../backend/TripGeneration.md) to start OTP, the STM/exo
realtime bridge and Ktor, then install Android. The app now generates routes only
through the actual backend when running with the live Android map. The old fixed
STM/exo endpoints, test buttons, launch intents and direct phone-to-OTP client have
been removed. There is no mock-route fallback on Android if the backend is unavailable.

Confirm an origin, choose time, modes, transit zones and walking pace, then press
**GO**. Time changes in one-minute steps, with a minimum of 5 and maximum of 360
minutes. Restart the routing backend after updating so it accepts every whole
minute value.
Review the route before **Start Trip**. **Generate another** keeps the origin
and requests a different destination. Progress is simulated only when explicitly
enabled in the debug menu. Normal Android use follows actual GPS readings.

Each trip leg has a distinct colour, including separate walking sections. The same
colours appear on Android's live map, the bundled map, and the **Trip sections** key
in trip information. The colour remains visible beneath the thin travelled overlay.

During playback, the information box shows **Section N of M**, **Now**, and **Next**.
Street instructions use OTP's saved steps (turn direction and street name). Transit
instructions use the provider's route number, headsign, boarding and alighting stop
when available. Before a transit section, **Next** tells the user which service to
wait for and board. Instructions follow the current route position; waiting time
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
