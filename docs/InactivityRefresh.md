# Refresh after inactivity (#96)

Returning after at least five minutes in the background replays the app's boot
animation while refreshing transport data, the signed-in profile and cloud trip
history. Short interruptions and ordinary screen navigation do not trigger it.
The existing screen, active trip, planner inputs and review draft stay composed.

Refresh is scoped to the current identity and runs once at a time. History shares
the result rather than fetching it again when its revision changes. A refresh
times out after 15 seconds; errors reveal the retained screen with a Retry action.
Backgrounding cancels the work and defers an interrupted refresh until foreground.
Both the inactivity clock and timeout are independent of wall-clock adjustments.

On Android, cold start continues to use the same animation and map-ready gate.
Its drawing code is shared with the resume overlay rather than copied.

Device checks: background for five minutes, resume on a planner/active trip/review,
check retained state and animation, then repeat offline and use Retry after
reconnecting. A short background interval should not replay the boot animation.
