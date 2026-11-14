# RNDTransitMTL

RNDTransitMTL is a discovery app for Greater Montreal. Choose how much time you
have, how you want to travel, and where to start. The app finds a route to a
surprise destination so you can explore without planning the destination first.

Built with Kotlin Multiplatform, the current Android prototype combines a map-based
planner, real walking/cycling and transit routes, account sign-in, trip reviews,
and History.

## What you can do

- Choose your starting point on the map or use your device location.
- Set your available time, walking pace, transport choices and transit zones.
- Preview a real route before starting, or generate another destination from the
  same origin.
- Follow coloured trip sections with the current section and next action displayed.
- Rate completed trips, add comments or image links, and revisit or edit reviews
  in History.

## Plan an adventure

1. Sign in, confirm a starting point and choose your trip preferences.
2. Press **GO**. The app searches for a reachable route close to your available
   time without exceeding it. Selected transport modes are allowed alternatives;
   walking connections are available for transit.
3. Review the proposal. **Generate another** keeps your starting point and looks
   for another destination; **Start Trip** keeps the route you accepted.
4. At completion, tap the progress bar to review your trip, then find it in History.

## Android availability

APK distribution is planned. For now, developers can build and install the Android
app from source using the [Android development guide](docs/AndroidDevelopment.md).
APK download and installation details will be added here when a release is published.

The current app requires Android 7.0 / API 24 or newer and internet access for
sign-in, maps and trip generation. Remote trip generation requires a running
configured server and a signed-in account. Guest mode is available for local
development; it cannot access the authenticated public generation service.

## Current prototype limits

Trip progress is **simulated**; this is not live GPS navigation. Route timing and
realtime estimates can change after a trip is generated. Available transit routes
depend on the server's imported schedules and configured feeds.

Attraction preferences are saved but do not yet rank destinations. Trips and review
drafts support saved-state restoration; permanent trip storage is not implemented
by the current flow. Desktop and Web previews use sample trips unless connected to
a generation service.

## Project documentation

- [Android development, APK builds and client tests](docs/AndroidDevelopment.md)
- [Backend setup, server updates and hosting](backend/README.md)
- [Routing rules and generation verification](backend/TripGeneration.md)
- [Trip playback and guidance checks](docs/RunTripSimulation.md)
- [Design document and planned features](docs/MainDesignDoc.md)
- [Team practices](docs/TeamPractices.md)

The trip/review flow originated in
[Artiom's Assignment 3 repository](https://github.com/NewWaveOwl/As3_APPDEV2_RND_Tranzit_Sova/tree/8b1b538)
and has since been integrated with the real-route backend.

## Earlier milestone screenshots

### Main map and trip controls

![Main map with GOBox, transport selection, and attraction intensity](screenshots/MileStone_1_MainScreen.png)

### User profile

![User profile with account placeholders and the About app button](screenshots/MileStone_1_user.png)

### About

![About page introducing Caio, Artiom, and Jimmy](screenshots/MileStone_1_about.png)

These screenshots show the earlier milestone UI. They have not been refreshed for the imported trip and review flow.

## Team members

- Artiom Sova
- Caio Nunes
- Jimmy Rashid
