"""Build adapter identifier mappings from the same static feed used by OTP."""
import csv
import io
import json
import pathlib
import zipfile

root = pathlib.Path(__file__).resolve().parent
with zipfile.ZipFile(root / "stm-gtfs.zip") as archive:
    def rows(name):
        return csv.DictReader(io.TextIOWrapper(archive.open(name), encoding="utf-8-sig"))

    routes = {}
    for row in rows("routes.txt"):
        routes.setdefault(row["route_short_name"], []).append(row["route_id"])
    stops = {}
    for row in rows("stops.txt"):
        if row.get("stop_code"):
            stops.setdefault(row["stop_code"], []).append(row["stop_id"])
    directions = {}
    compass = {"Nord": "N", "Sud": "S", "Est": "E", "Ouest": "W"}
    for row in rows("trips.txt"):
        name = compass.get(row["trip_headsign"])
        if name and row.get("direction_id"):
            key = row["route_id"] + ":" + name
            directions.setdefault(key, set()).add(int(row["direction_id"]))
    # Ambiguous directions stay unmapped, so an alert cannot silently broaden.
    directions = {key: next(iter(values)) for key, values in directions.items() if len(values) == 1}
    index = {"routes": routes, "stops": stops, "directions": directions}
(root / "stm-adapter-index.json").write_text(json.dumps(index), encoding="utf-8")
print("Prepared STM route, stop-code and verified direction mappings.")
