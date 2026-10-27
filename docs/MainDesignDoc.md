# TransitRandomizer Greater MontrГ©al Design Document

**Team:** Jimmy, Caio, Artiom  
**Project:** RNDTransitMTL  
**Updated:** 2026-10-27

TransitRandomizer generates achievable, semi-random adventures around Greater MontrГ©al using the user's available time, location, transportation choices, interests, and previous feedback. The user chooses what kind of adventure they want; the application proposes a destination and a practical route instead of requiring a destination in advance.

This document defines the product concept, screen behavior, generation algorithm, data model, and proposed delivery sequence. It combines the original Word design document with the team's additional feature ideas and the current Figma interface decisions. Planned features below are requirements and proposals, not a claim that they are already implemented.

## Product purpose

The application helps people answer questions such as вЂњWhere can I explore in the next two hours?вЂќ or вЂњWhat unfamiliar place can I reach using Metro and walking?вЂќ It combines local discovery, route planning, personalization, and controlled randomness.

The intended users include MontrГ©al residents, students seeking inexpensive activities, tourists, pedestrians, cyclists, public-transit users, and groups of friends. A useful adventure should fit the available time, match interests, avoid unnecessary repetition, and remain practical to complete.

Walking, bicycle, bus, Metro, train, and REM are planned transportation options. Car support remains an open decision. Several modes may be combined in one adventure. A **trip leg** means a section of the journey, regardless of its mode; walking is a transportation mode rather than a synonym for a leg.

## Feature scope

| Feature | Intended behavior | Scope |
| --- | --- | --- |
| Random trip generator | Select reachable candidate places, compare them with time, interests, and transport preferences, and return a destination with a route | Core |
| Adventure categories | Optional rich interests used in future recommendations | Nice to have |
| User preferences | Store transport choices and basic constraints; richer personalization follows later | Basic settings core; rich preferences Nice to Have |
| Trip history | Save generated and completed trips, display their routes on the map, and allow an editable version of a past trip | Core |
| Post-trip review | Offer a star rating and short written review, plus feedback on individual places and route sections | Core |
| Map and compass views | Offer full map navigation or minimal compass guidance | Must Have |
| Shake to open compass | Open the compass when the user shakes the phone during an active adventure | AI Level-Up |
| Topographic main map | Show terrain and elevation; let users target altitude ranges or viewpoints during hikes | Nice to Have |
| Local group adventures | Create/join a session with shared constraints and participant locations | AI Level-Up |
| User-selected points of interest | Accept a list of places that influences the generated adventure path | Nice to have |
| Shared quests and competition | Give groups a shared quest pool for cooperative or competitive games inspired by Jet Lag | Nice to have |

The proposed first release prioritizes a complete single-user journey through generation, preview, completion, history, and review. Compass, elevation, and group features remain part of the product design, with their delivery order described later.

## Main user journey

1. The user launches the application. The entry flow supports a guest path; login and registration can be added when account services are available.
2. The user arrives at the main map and chooses their available time, transport modes, and transit zones when using transit.
3. The user adjusts trip-specific settings or accepts their saved preferences.
4. GO requests a generated adventure using the current settings and location.
5. The preview shows a destination or sequence of stops, route, duration, distance, and transportation methods.
6. The user starts the adventure, regenerates it, edits the settings, or saves it for later.
7. During the adventure, the user follows the map or switches to the direction-only compass view. Shaking the phone opens the compass.
8. The user completes or ends the adventure and reaches the review page.
9. The user can give stars, write a short review, and rate places or route sections, or skip the review.
10. The trip is stored in History. Reviews and visited locations influence future recommendations.

## Navigation and visual design

The current design uses the **Figma top navigation**. Profile and History appear on the left; Settings appears on the right. A gradient GO shortcut appears between History and Settings only when the active screen is not Main. This shortcut returns to the existing main map and preserves trip selections. The GO action inside `GOBox` requests an adventure; it has a different purpose from the header shortcut.

Profile provides the **about app** action, which opens About. Transport modes toggle independently on Main; exact bus numbers and route restrictions belong in detailed Settings. Platform Back returns through the navigation stack.

The target bottom navigation contains Map, Compass, and Together. These destinations share one active trip. Compass and Together destinations remain to be implemented.

Use [the colour system](colours.md) for all palette tokens, semantic roles, the blue-to-lime GO gradient, and LINE Seed JP Regular/Bold typography. Shared Material Symbols drawables and the existing team photographs remain in use.

The attraction-intensity control represents how strongly the user wants exploration and attraction discovery to influence the route. Its precise relationship to low, medium, and high exploration remains to be finalized.

## Main-screen interaction
The main screen presents the map, available minutes, GO, transport-mode toggles, and a draggable preferences sheet. Pulling the sheet upward reveals additional controls; expanded content scrolls independently. A tappable handle provides an alternative to dragging. The map remains stationary beneath the sheet.

Transport modes appear in this order: Walk, Bike, Bus, Metro, Train, REM. Each mode toggles independently. Main does not require users to select bus numbers or individual transit routes.

When a transit mode is enabled, show Transit zones with Zone A, Zone B, Zone C, and Zone D. Require at least one zone before generating a transit request. Preserve zone choices when the sheet moves or a mode is temporarily disabled. Zone restrictions apply to the transit journey, including transfers and intermediate travel, when supported by routing data.

Optional exact bus or route restrictions belong in detailed Settings. They are additional constraints, not a prerequisite for selecting a transit mode. Unsupported restrictions must be identified clearly rather than silently ignored.

The current sample-map generator can record zone selections but cannot validate geographic zone coverage. Geographic enforcement remains part of routing integration.

The shared top navigation contains Profile, History, and Settings. The target bottom navigation contains Map, Compass, and Together. These views share one active trip; switching views must not create another trip.

Use the palette and semantic roles in colours.md.
## Trip configuration and proposal alignment
The initial delivery generates one-way adventures. Required inputs are a valid starting location, positive whole minutes, and at least one supported transport mode. Transit requests additionally include selected zones under the updated main-screen design.

GO requests a proposal. The target preview offers Start Trip, Generate Another, and Edit Trip Settings. Generating a proposal must not itself mark the trip active. The existing sample flow currently starts a trip directly; introducing the preview boundary remains required implementation work.

Travel, waiting, transfers, planned visits, and an explicit allowance must fit the available duration. Return journeys and round-trip controls are outside the initial delivery.

The Milestone 2 Must-Haves include configuration, generation, preview, active-trip handling, persistent history, accounts and information screens, general settings, minimal compass guidance, and shared trip constraints. The AI Level-Ups are live map movement, shake-to-open compass, and group-session creation/joining.

Rich attraction preferences, personalization, favorites, terrain/elevation targets, selected points of interest, quests, and competition remain Nice-to-Have features. Showing a concept in Figma does not make its underlying service implemented.

The new zone-based Main interaction and foundations palette supersede the proposal's older route-expansion and colour descriptions. Record these as design changes in the next proposal revision.
## Required architecture: MVVM
The application must use Model–View–ViewModel with unidirectional data flow.
- Model: domain records, repository contracts, persistence, providers, and reusable business rules.
- View: composables render supplied state and send user actions through callbacks. Views own presentation details such as sheet position, scrolling, and animations.
- ViewModel: owns screen state, validates input, coordinates repository/use-case operations, and exposes read-only observable state.
Views must not generate trips, access storage, or own business decisions. ViewModels must not retain composables, platform views, activities, or navigation controllers. Navigation wiring belongs at the destination boundary.

Scope each ViewModel to its Navigation 3 entry. Keep application-wide trip state in one shared repository/store. A ViewModel must not create a second authoritative copy of the active trip.

Save planner drafts through a restoration mechanism and persist user defaults through a settings repository. A ViewModel alone does not provide persistence after process termination.
## Screen and component organization
Use the following structure under the existing shared Kotlin package:
```text
View/
    main/
        MainScreen.kt
        MainPlannerContent.kt
        components/
    history/
    current_trip/
    trip_details/
    trip_review/
    profile/
    settings/
    about/

ViewModels/
    main/
        MainPlannerViewModel.kt
    history/
    current_trip/
    trip_details/
    trip_review/
    profile/
    settings/

ui/
    global_ui_ellements/
    theme/

model/
data/
state/
layout/
```

Each screen/view and its private components belong in the corresponding named screen folder. Components reused by multiple screens belong in ui/global_ui_ellements. Keep the spelling consistent with this project convention.

Theme definitions remain in ui/theme. Shared navigation and scaffold assembly remain in layout.

Move existing files gradually, preserving package/import consistency and updating previews and tests. Moving a file into ViewModels does not make it a ViewModel; responsibilities must also change.
## Implementation status
The main-screen update introduces a draggable sheet, independent transport toggles, zone selection, and a ViewModel for planner selections.

Trip generation, validation feedback, and trip-opening coordination still require extraction from TransitOpeningScreen to complete the planner's MVVM migration.

The bundled map, normalized sample coordinates, and accelerated trip simulation remain prototype behavior. They do not demonstrate live geographic routing, live location tracking, geographic zone enforcement, or durable history storage.

## Screen requirements

### Launch and account entry

Show the application name and logo, a brief loading state, and location permission when required. Offer guest entry and, when implemented, login or registration. Continue to the main map. Account creation should not be required for the initial guest prototype.

### Main map

Display the user location or chosen starting point, nearby transit stops, lines, bicycle paths, interesting places, and the generated or active route. Completed routes can be shown when enabled. Keep trip generation and settings accessible from this screen.

A Nice-to-Have main-map style is **topographic**, with terrain and elevation information. Users should be able to select a desired altitude range, elevation target, or viewpoint for hiking adventures. Distinguish an absolute altitude target from total elevation gain. Both depend on elevation data and route coverage.

The first map prototype may use sample routes and markers before live location, topographic tiles, and routing are integrated. Group mode adds consenting members' locations to this map.

### Trip configuration

Collect a valid starting location, positive whole minutes, and at least one supported transport mode for a one-way adventure. Transit requests also require selected zones. Optional exact route restrictions belong in detailed Settings; rich attraction preferences and personalization remain backlog features. Return journeys and round-trip controls are future possibilities outside the initial delivery.

Topographic options add target altitude, acceptable elevation gain, and terrain preferences when supported. The optional points-of-interest list can contain places the user would like the generator to include. Required stops and suggested stops should be distinguished so a suggested place does not silently become a hard constraint.

Example request: вЂњI have two hours, I am starting at John Abbott College, I can walk and use public transit, and I want to discover somewhere unusual.вЂќ

### Generated trip preview

Show the route, destination and intermediate stops, estimated duration and distance, transportation methods, visit time at each stop, and arrival time. When elevation is used, show the altitude target and estimated elevation gain.

Provide **Start Trip**, **Generate Another Trip**, **Edit Trip Settings**, and **Save for Later**. Explain which selected constraints prevented generation if no valid route is available.

### Active trip and compass

The map view shows current location, remaining route, current leg, next destination or transit stop, estimated remaining time, and progress. Actions include pause, skip a destination, end the trip, recalculate the remaining route, and open external navigation for detailed directions when available.

The **compass view hides the map, markers, trip controls, statistics, and ordinary navigation bars**. It shows a compass or direction arrow and a short prompt to follow that direction, creating the feeling of moving into the unknown. Keep an unobtrusive way to return to the map or end the adventure.

The direction should follow the next reachable waypoint on the planned route rather than always pointing directly through obstacles toward the final destination. The route remains active behind the minimal display, even while destination details are hidden.

Shaking the phone during an active adventure opens the compass. Add a deliberate button alternative for devices without usable motion sensors and for users who cannot shake the phone. Motion detection should reject ordinary movement and avoid reopening the compass repeatedly from a single shake. Shake sensitivity and whether shaking again returns to the map are open interaction decisions.

Later versions may recalculate when transit is delayed, a place is closed, the user skips a stop, spends longer than expected, or leaves the route. Compass mode uses the updated route after recalculation.

### Review trip

Open the review page after an adventure is completed or explicitly ended. Offer an overall **1вЂ“5 star rating** and a short written review. The user may skip either. The proposed star scale should be confirmed during interface design.

Retain the source document's detailed feedback on individual route segments and attractions: single tap selects a segment and opens its information; double tap can like it; dislike is available from the information panel. Segment feedback may cover walking, cycling, transit, streets, parks, waterfronts, viewpoints, and attractions. Distinguish liked and disliked sections visually and with labels.

Store reviews separately from the original trip. Overall stars and text complement the per-segment and per-attraction likes/dislikes. A skipped review leaves the trip saved without a rating.

External place ratings, such as Google, Tripadvisor, or Yelp, are an optional future integration and must be shown with their source. They remain distinct from ratings written inside TransitRandomizer.

### History and trip details

Store generated, saved, completed, and cancelled trips with their status. A history entry may show its date, duration, distance, transport modes, places visited, and overall rating. Opening it displays the route on the map.

Provide viewing, repeating, modifying, deleting, favoriting, and reviewing an unreviewed trip. Modification can change stops, time budget, or transport modes and request a new valid route. Keep the original completed route, actual travel record, and review intact; create an editable copy or revision linked to the original trip.

History records what happened. Favorites record routes or places that the user deliberately wants to keep. They are separate concepts.

### Preferences and settings

Transportation preferences include walking, bicycle, bus, Metro, train, and REM; car remains undecided. Adventure categories include popular attractions, hidden places, parks, architecture, food and cafГ©s, historic locations, nature, street art, waterfronts, interesting streets, and random exploration.

Restrictions include maximum walking and cycling distances, maximum duration, budget, avoiding highways or stairs, wheelchair accessibility, preferred bicycle infrastructure, indoor/outdoor choices, and late-night preferences. Elevation preferences add altitude and terrain requirements.

General settings include language, units, notifications, location permissions, privacy, deleting trip history, and deleting an account. Preference changes affect the next generation request. An active route changes only after the user requests or accepts recalculation.

### Profile and About

Profile can show username, image, completed-trip count, total distance and exploration time, visited neighbourhoods, and favorite modes, categories, and places. Account details and password controls belong here when authentication is implemented.

About is reached through Profile and introduces Caio, Artiom, and Jimmy using the existing photographs. Keep the provided вЂњWe make stuffвЂќ copy until the team supplies biographies.

Optional achievements include visiting ten MontrГ©al neighbourhoods, using every Metro line, completing 100 kilometres, visiting major parks, and completing trips with three transportation methods. Achievements are outside the initial proposed MVP.

### Local group adventures

Let a user create a nearby group session and let friends join. Participants share a generated adventure and see all members who have enabled location sharing on the map. Agree on the group's available time, categories, and transportation modes before generation. Respect the restrictions of every participant, including accessibility needs.

Joining and leaving a session should be explicit. Keep the group's route understandable if a participant disconnects, and indicate when a displayed location is stale. Group location sharing is limited to the session and its members.

As a **nice-to-have extension**, give the group a shared quest pool. Quests might ask members to reach a viewpoint, find public art, or visit selected places. Support cooperative completion or a competitive game inspired by Jet Lag. Scoring, completion evidence, teams, and deadlines remain design decisions.

вЂњLocal multiplayerвЂќ currently means nearby people taking an adventure together. Whether it uses a shared online service, local network, or direct device communication is undecided; offline multiplayer is not yet a requirement.

## Trip generation design

### Inputs

The generator receives a starting location, available one-way time, enabled transport modes, selectedZoneIds for transit, optional allowedRouteIds from detailed Settings, and applicable shared constraints. Rich interests, personalization, walking/cycling limits, and budget preferences are future extensions.

Supporting inputs include transit schedules and service conditions, place opening hours, typical visit durations, previous visits, personal and community reviews, and route data. Future inputs include weather, terrain/elevation, the optional points-of-interest list, and group preferences.

### Candidate selection and validation

The team's starting idea is to randomly select points near the user within a distance roughly walkable during the available minutes. The generator then compares those points with attraction preferences, time, and transportation choices to produce a final destination and path.

Use that walking-distance estimate as an initial candidate-search area, not as proof that a route fits. Roads, paths, barriers, visits, waiting, transfers, and an explicit timing allowance affect actual one-way travel time. When transit or cycling is enabled, reachable-area calculations can extend candidate search beyond the initial walking radius.

The proposed generation process is:

1. Validate time, origin, transport choices, and required restrictions.
2. Estimate a reachable area using the enabled modes and available time.
3. Select candidate destinations, attractions, and interesting route sections, using core routing data; user-listed points of interest are a later extension.
4. Remove closed, unavailable, inaccessible, over-budget, or otherwise incompatible places.
5. Reduce the priority of recently visited or disliked places and sections.
6. Calculate one-way routes to one or more candidates.
7. Include travel, waiting, transfers, visits, and a timing buffer in the total estimate.
8. Score valid routes for preference match, novelty, interestingness, and diversity.
9. Randomly choose among high-scoring valid routes so the result still feels surprising.
10. Recheck the chosen route against the full time budget and hard constraints, then return its geometry, stops, modes, and timing.

If there is no valid route, explain the limiting constraints and offer regeneration after the user changes them. Do not silently ignore the user's time or accessibility requirements.

### Scoring and exploration

```text
Route score = interestingness + novelty + preference match
            + route diversity + community rating
            - travel cost - repetition penalty
```

Invalid routes are filtered before selection. Hard restrictions are not penalties that an attractive destination can outweigh. Score weights and the amount of randomness remain tunable design choices.

Low exploration favors popular, highly rated places, main streets, and familiar transport connections. Medium exploration combines familiar places with lesser-known streets, parks, and neighbourhoods. High exploration increases novelty, unusual places, and varied transport combinations. Exploration intensity never overrides time, accessibility, availability, or route restrictions.

Preference edits change filtering and scoring in later requests. Personal feedback should influence that user's results more than community popularity. Require enough feedback before making large community-score changes so isolated or manipulated ratings do not dominate.

### Data flow

New Trip or the main `GOBox` action starts generation. Stored preferences, reviews, visits, and external data support the generator; they do not start a new adventure by themselves.

![Original trip generator data flow showing requests and supporting inputs](assets/main-design/generator-data-flow.png)

The extended inputs add elevation information, an optional user-selected place list, and shared group constraints. The output remains a generated trip with route, timing, stops, and modes.

## Data model

These are proposed product entities. They describe required information without committing to a local database, remote backend, or specific service.

| Entity | Suggested fields |
| --- | --- |
| User | `userId`, `username`, `email`, `profileImage`, `createdAt`, `preferencesId`, `privacySettings` |
| UserPreferences | `preferencesId`, `userId`, `enabledTransportModes`, `selectedZoneIds`, optional `allowedRouteIds` for detailed Settings, `interestCategories`, `explorationLevel`, `maximumWalkingDistance`, `maximumCyclingDistance`, `budgetPreference`, `accessibilityRequirements`, `indoorOutdoorPreference`, `language`; proposed elevation preferences |
| TransportationMode | `transportModeId`, `type`, `category`, `routeName`, `stopOrStation`, `scheduledTime`, `estimatedTravelTime`, `availability` |
| Trip | `tripId`, `userId`, `status`, `startLocation`, `endLocation`, `startedAt`, `completedAt`, `estimatedDuration`, `actualDuration`, `estimatedDistance`, `actualDistance`, `transportModes`, `routeSegments`, `attractions`, `selectedZoneIds` (generation-time snapshot), `isFavorite`; proposed origin/revision link for editable copies |
| RouteSegment | `segmentId`, `tripId`, `orderNumber`, `transportMode`, `startCoordinates`, `endCoordinates`, `geometry`, `estimatedDuration`, `actualDuration`, `distance`, `relatedAttractionId`; proposed elevation measurements |
| Attraction or InterestingSpot | `attractionId`, `name`, `coordinates`, `category`, `description`, `rating`, `popularity`, `interestingnessScore`, `typicalVisitDuration`, `openingHours`, `priceLevel`, `accessibilityInformation`, `source`; proposed altitude |
| TripReview | `reviewId`, `tripId`, `userId`, `overallRating`, `createdAt`, `routeSegmentRatings`, `attractionRatings`; add `reviewText` for the short review |
| VisitedLocation | `visitedLocationId`, `userId`, `attractionId`, `tripId`, `visitedAt`, `visitCount`, `lastRating` |
| Favorite | `favoriteId`, `userId`, `itemType`, `tripId` or `attractionId`, `createdAt` |

Trip status values are **Draft**, **Generated**, **Active**, **Paused**, **Completed**, and **Cancelled**. Overall review ratings use the proposed star scale or no rating; route-segment and attraction feedback use Like, Dislike, or No Rating.

A user has preferences and many trips. A trip has ordered route segments, attraction references, visit records, and an optional review. A segment may reference an attraction. Favorites belong to users and reference a trip or place. Editable copies link to their original trip rather than overwriting completed travel records.

Proposed extension entities are:

| Entity | Purpose and suggested information |
| --- | --- |
| AdventureRequest | Capture the settings used for generation, including positive whole minutes, starting location, transport, `selectedZoneIds`, optional `allowedRouteIds`, and one-way timing for the initial delivery; rich categories, altitude and place constraints are future extensions |
| SelectedPointOfInterest | Record a user-selected attraction or coordinate and whether it is required or preferred |
| GroupSession | Session identifier, host, members, agreed constraints, shared trip, and session status |
| GroupMember | Session membership, display name, sharing consent, current location, last update time, and connection state |
| Quest | Optional objective, target place, completion rule, score, deadline, and cooperative or competitive mode |
| QuestCompletion | Optional member/team completion record, completion time, and validation state |

The source document's relationship diagram is preserved below. The table and extension definitions above include the new star reviews, editable history, elevation, and group concepts.

![Original core backend relationship diagram](assets/main-design/data-model-reference.png)

## Data sources and services

Potential integrations include map tiles and geocoding, routing, official transit schedules, real-time transit information, municipal open data, bicycle and pedestrian infrastructure, attraction information, elevation data, and community feedback from TransitRandomizer.

Prefer official APIs and GTFS or GTFS-Realtime feeds where available. Evaluate provider coverage, terms, attribution, rate limits, cost, and reliability before choosing services. External ratings and places should retain source attribution. Map, routing, topographic, and multiplayer providers remain open decisions.

Live feeds and exact provider integrations are not required to demonstrate the initial generation flow with clearly identified sample data. Stored history and reviews should remain available if a provider is offline.

## Quality requirements

- **Performance:** keep map interaction responsive, show progress during generation, and avoid blocking the interface during route calculations.
- **Usability:** require only a few selections for a simple adventure, keep core actions accessible, and explain generation failures. Compass view should be easy to enter and leave.
- **Reliability:** account for total adventure time, preserve saved trips, and handle missing schedules, unavailable routes, location errors, network failures, and stale group updates.
- **Privacy:** collect location history only when needed, explain stored location precision, support deletion, and avoid exposing personal routes in community analytics. Group sharing requires explicit participation.
- **Accessibility:** support screen readers, usable touch targets, readable LINE Seed JP text, and feedback indicators beyond color. Treat route accessibility restrictions as hard constraints and provide an alternative to shaking.

## Proposed delivery sequence

### Sprint 3: Foundations

Establish shared navigation, MVVM responsibilities, basic configuration, model/repository contracts, and the design foundations. Keep terrain and rich attraction categories in the backlog.

### Sprint 4: Core flow

Implement generation, a proposal preview with Start Trip / Generate Another / Edit Trip Settings, active-trip handling, durable history, accounts and information screens, and general settings. Generating a proposal must not activate it.

### Sprint 5: Tracking, groups, and Level-Ups

Integrate tracking, minimal compass guidance and shared trip constraints. Add live map movement, shake-to-open compass, and group-session creation/joining as the proposal's AI Level-Ups.

### Sprint 6: Testing and final delivery

Verify the complete journey, navigation, restoration, persistence, target-platform behavior, accessibility, and failure handling. Resolve defects and prepare the final delivery and supporting documentation.

Further possibilities from the source document include real-time replanning, weather-aware recommendations, community route discovery, achievements, social sharing, offline trip access, advanced safety controls, automatic detection of the travelled route, and push notifications.

## Open decisions

- Map, topographic, routing, transit, attraction, and elevation providers.
- Whether the first complete MVP uses live data or a controlled dataset.
- Local persistence, remote backend, account services, and guest-data behavior.
- Car support and how many transport combinations are practical for a given time budget.
- In-app versus external detailed navigation and reliable selection of map segments.
- How attraction intensity maps to scoring and exploration levels.
- Altitude-range versus elevation-gain controls and provider coverage for hikes.
- Compass heading source, waypoint selection, shake sensitivity, and shake-again behavior.
- Required versus preferred stops in the optional place list.
- Final star scale, review-text length, and treatment of external place ratings.
- Group connection method, group size, mixed preferences, host departure, and reconnect behavior.
- Quest rules, scoring, teams, and evidence of completion for optional competitive play.
- Maybe we should add sort of sorting or filter on user's type of stm pass/ticket + price maybe

## Source references

Proposal alignment source supplied for this revision: `E:/CLouD MAIL/RNDTransitMTL_Milestone2_Proposal_Final (1).pdf`. The October 27 main-screen and foundations decisions supersede its older route-expansion and palette descriptions.

The base concept, original screen requirements, generator design, and nine core entities come from `TransitRandomizer_MTL_ed (3).docx`, supplied from `E:/CLouD MAIL/TransitRandomizer_MTL_ed (3).docx`. The shake compass, direction-only view, category-led adventures, optional place list, topographic altitude targeting, local group adventures, optional quests, editable history, and stars with a short review incorporate the team's added feature ideas.

The original navigation flow is retained as a product-journey reference. The current top-bar layout and additional compass/group features are defined in this document.

![Original navigation and adventure journey diagram](assets/main-design/navigation-reference.png)

Related project documentation: [navigation implementation](navigation.md), [app colors](colours.md), and [Material Symbols resources](material-symbols.md).
