# Fare-zone source

The first backend start downloads the unmodified ARTM **Zones tarifaires 2026** GeoJSON,
published September 17, 2026, into `fare-zones-2026.geojson`. The cache is ignored by Git.
To prepare an offline server, download the file beforehand or set `FARE_ZONES_FILE`.
Startup validates the data and fails if it cannot load all four zones; it never guesses
zones from the absent STM or legacy numeric exo `zone_id` values.

Source: https://donnees.artm.quebec/catalogue/dataset/zones-tarifaires-du-transport-en-commun-pour-la-region-metropolitaine-de-montreal

Publisher: Autorité régionale de transport métropolitain. Licence: CC BY 4.0.
https://creativecommons.org/licenses/by/4.0/

Refresh the cached file when ARTM publishes new boundaries. Zone validation covers
transit geometry (vertices and points at most 100 metres apart), including intermediate
zones. This is a geographical planner restriction, not a ticket-price calculator.
