"""Offline checks for the Windows graph helper; no OTP build or network access."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


class ExoBusPreparationTest(unittest.TestCase):
    def test_preparation_preserves_source_and_never_marks_unbuilt_data_ready(self):
        script = Path(__file__).resolve().parents[2] / "scripts" / "Prepare-ExoBusGraph.ps1"
        with tempfile.TemporaryDirectory(prefix="exo bus preparation ") as temporary:
            root = Path(temporary)
            source = root / "source with spaces"
            source.mkdir()
            files = {
                "agency.txt": "agency_id,agency_name,agency_url,agency_timezone\nCITSO,Bus,http://example.test,America/Toronto\n",
                "routes.txt": "route_id,agency_id,route_short_name,route_type\n10,CITSO,10,3\n",
                "trips.txt": "trip_id,route_id,service_id\nT,10,weekday\n",
                "stops.txt": "stop_id,stop_name,stop_lat,stop_lon\nS,Stop,45.5,-73.6\n",
                "stop_times.txt": "trip_id,stop_id,stop_sequence,arrival_time,departure_time\nT,S,1,10:00:00,10:00:00\n",
            }
            archive = root / "bus sector.zip"
            with zipfile.ZipFile(archive, "w") as output:
                for name, content in files.items():
                    output.writestr(name, content)
            (source / "stm-gtfs.zip").write_bytes(archive.read_bytes())
            (source / "graph.obj").write_bytes(b"original graph")
            (source / "stm-adapter-index.json").write_text('{"routes":{},"stops":{},"directions":{}}')
            (source / "stm-upstream.json").write_text('{"updaters":[{"headers":{"apiKey":"fixture-private"}}]}')
            (source / "unrelated-secret.txt").write_text("not copied")
            (source / "build-config.json").write_text(json.dumps({"graph": str(source / "graph.obj"), "transitModelTimeZone": "America/Toronto"}))
            osm = root / "map.osm.pbf"
            osm.write_bytes(b"OSM fixture, not used for a real build")

            def invoke(destination, agency="CITSO"):
                def quote(path):
                    return "'" + str(path).replace("'", "''") + "'"
                wrapper = root / "run-helper.ps1"
                wrapper.write_text(
                    "$ErrorActionPreference='Stop'\n& " + quote(script) +
                    " -SourceDirectory " + quote(source) + " -Destination " + quote(destination) +
                    " -BusFeeds @{" + quote(agency) + "=" + quote(archive) + "} -OsmFile " + quote(osm) + " -PrepareOnly\n",
                    encoding="utf-8-sig",
                )
                return subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(wrapper)],
                                      capture_output=True, text=True, timeout=30)

            destination = root / "new bus graph"
            result = invoke(destination)
            self.assertEqual(0, result.returncode, result.stderr)
            config = json.loads((destination / "build-config.json").read_text())
            self.assertEqual({"STM", "EXO_BUS_CITSO"}, {feed["feedId"] for feed in config["transitFeeds"]})
            self.assertEqual("graph.obj", config["graph"])
            self.assertEqual("map.osm.pbf", config["osm"][0]["source"])
            self.assertEqual(b"original graph", (source / "graph.obj").read_bytes())
            self.assertFalse((destination / "graph.obj").exists())
            self.assertFalse((destination / ".rndtransit-data").exists())
            self.assertFalse((destination / "exo-buses.json").exists())
            self.assertFalse((destination / "unrelated-secret.txt").exists())
            self.assertTrue((destination / "exo-bus-CITSO-gtfs.zip").exists())
            self.assertNotEqual(0, invoke(destination).returncode, "Must not overwrite an existing destination")
            unsafe = root / "unsafe destination"
            self.assertNotEqual(0, invoke(unsafe, "../CITSO").returncode)
            self.assertFalse(unsafe.exists())


if __name__ == "__main__":
    unittest.main()
