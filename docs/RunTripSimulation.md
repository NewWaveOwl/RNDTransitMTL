# Debug trip simulation

The app starts in **Normal app use**. On Android, accepted trips use the device's
fresh GPS readings for the position marker, route progress and street guidance.
The planner and current trip share one location session. Starting a trip immediately
displays the cached position as **Last known position**, then continues the same live
updates. Provider outages keep that cached marker; switching screens does not restart
GPS or reset the 30-second warning deadline for the last fresh measurement.
Old or imprecise cached positions can be displayed but cannot advance or finish a trip.
Fresh plausible imprecise readings move the marker with a small **Low GPS accuracy** label,
without the not-updating warning. Only accurate readings can advance or complete a trip.
When weak GPS is close to a plausible position on the current real route leg,
the marker is map-matched and labelled **Estimated position · Low GPS accuracy**.
Matching uses recent progress and movement, stays within the current leg, and rejects
large jumps and ambiguous branches. It never confirms transfers or arrival. The raw
GPS reading remains separate from the displayed match. Distant, off-route and fallback
geometry readings remain unsnapped. A rejected match keeps the previous displayed
position until consistent readings confirm the new position; it never jumps straight
to an unreliable raw point.
Both map screens share a jump filter. Movement must be plausible for the current
travel mode and elapsed time, with a bounded allowance for GPS error. Large changes
require three consistent measurements spanning at least four seconds. Duplicate,
old or future measurements cannot confirm a relocation. Random provider outliers
retain the visible marker as **Last known position** with a larger yellow circle,
and cannot change progress or confirm arrival. Fresh rejected readings still count
as provider activity for the 30-second warning. Confirmed off-route movement remains
visible. Accepted display changes animate over 500 milliseconds; these animation
frames never feed back into recorded GPS progress.
Once a real position is known, the marker remains visible through signal loss,
screen/lifecycle pauses and permission loss. Prediction stops when tracking stops.
A yellow uncertainty circle surrounds the displayed marker, accounting for GPS accuracy,
the matching offset and increasing age. It grows during an outage and shrinks when
precise GPS returns. No position is fabricated before the first measurement.
The recenter button requests location permission when needed and follows the
actual position; panning the map stops following until recenter is tapped again.
Recenter uses the same eased camera animation as the main Android map. Dragging
the trip panel only reveals or covers the stationary map; it does not pan, zoom,
or recenter the camera. The compact handle strip leaves more room for trip details.
Brief GPS gaps do not show an error. During a gap, **Estimated position** follows
the saved route using the last reliable movement. It stays
within the current section and never confirms a transfer or completes the trip.
After more than 30 seconds without a fresh reading, **Location is not updating**
appears while the estimated marker can continue within the current section.
It stops before a section boundary or arrival. Accepted GPS smoothly replaces the estimate.
The shared Android source automatically replaces stalled location registrations after
30 seconds of silence, then backs off to 60 and 120 seconds between retries.
Any fresh reading, including an imprecise one, resets that retry interval. Retries stop
when the app is no longer active or location permission is revoked.
No estimate is made without a reliable movement vector, off-route, after permission
denial, or on fallback geometry. Stationary devices still request regular GPS updates.

Drag the trip panel fully down to leave only a thin handle at the bottom, or up to reveal more
trip information. It follows the finger continuously and stays at any intermediate
height when released. Pull the same handle upwards to restore the panel. Both the handle and the panel body support dragging; tapping
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
