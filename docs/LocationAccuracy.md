# Location accuracy help

Open **Settings → Improve location accuracy** to see the Android permission level,
device location status, reported measurement accuracy and age of the last reading.
The same live diagnostics open from **Improve accuracy** on the map notice.
These controls reuse the shared location session and never change coordinates or
recorded trip progress.

Approximate permission immediately offers **Open settings**. Choose the app's
**Permissions → Location → Use precise location**. Device location being switched
off offers the device location settings instead. Access is checked on foreground
entry, after a permission request and once per second while the app is active.
Changing Precise/Approximate access restarts the existing source registration.

An inline **Location accuracy is low** notice requires fresh measurements worse
than 50 metres spanning at least 15 seconds. It clears after measurements within
25 metres span 10 seconds. Intermediate accuracy avoids flickering. One bad sample,
duplicate readings, background time and missing updates cannot qualify as sustained
weak accuracy. The existing trip warning still appears only after more than 30
seconds without a fresh location reading.

**Dismiss** suppresses all accuracy notices for 10 minutes, including across app
restarts. Settings remains available during that interval. Simulation and inactive
map controls do not display the notice. The known user marker, yellow uncertainty
circle and jump protection retain a shared trusted measurement. Raw readings remain
available for accuracy/freshness diagnostics. Weak readings enlarge uncertainty
without replacing a reliable marker or snapping it onto the saved route.

The help dialog offers system settings, clearer-signal guidance and optional
**Direction looks wrong?** compass guidance. Figure-eight calibration helps heading;
the app does not claim to calibrate GPS or apply a manual coordinate offset.

## Phone checks

1. Grant Precise location, open the help dialog, and confirm the reported accuracy
   and reading age update. Open both system settings actions and return to the app.
2. Switch to Approximate permission in Android settings. Return to Home and an
   active trip; confirm the permission notice and the retained marker. Restore
   Precise access and confirm the notice clears without restarting the app manually.
3. With continuous weak readings, confirm the notice appears after 15 seconds.
   Open help, then dismiss the notice. Navigate between maps and restart the app;
   confirm the notice stays suppressed for the remainder of the 10-minute interval.
4. Confirm good readings clear a weak-signal notice after 10 seconds. Turn location
   off and check the device-settings action. Missing readings should continue to
   use the separate 30-second trip warning with a retained marker.
