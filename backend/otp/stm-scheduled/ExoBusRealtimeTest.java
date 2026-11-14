import com.google.transit.realtime.GtfsRealtime.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/** Offline fixtures for bus namespace isolation, actual-itinerary fallback and graph/schedule binding. */
public class ExoBusRealtimeTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static void rejects(Throwing action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); } catch (java.io.IOException expected) { checks++; }
    }
    interface Throwing { void run() throws Exception; }
    static final long NOW = 1794024913L;
    static byte[] update(String trip, String route) {
        return FeedMessage.newBuilder().setHeader(StmRealtimeAdapter.header(NOW)).addEntity(FeedEntity.newBuilder().setId("update")
            .setTripUpdate(TripUpdate.newBuilder().setTrip(TripDescriptor.newBuilder().setTripId(trip).setRouteId(route))
                .addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder().setStopId("S").setDeparture(TripUpdate.StopTimeEvent.newBuilder().setDelay(120)))))
            .build().toByteArray();
    }
    static ObjectNode journeys(String... routes) {
        var result = StmRealtimeAdapter.JSON.createObjectNode();
        var edges = result.putObject("data").putObject("planConnection").putArray("edges");
        for (String route : routes) edges.addObject().putObject("node").putArray("legs").addObject()
            .put("mode", "BUS").put("realTime", true).putObject("route").put("gtfsId", route);
        return result;
    }
    static void schedule(Path path, String agency) throws Exception {
        var files = Map.of(
            "agency.txt", "agency_id,agency_name,agency_url,agency_timezone\n"+agency+",Bus,http://example.test,America/Toronto\n",
            "routes.txt", "route_id,agency_id,route_short_name,route_long_name,route_type\n10,"+agency+",10,Bus,3\n11,"+agency+",11,Bus 11,3\n",
            "stops.txt", "stop_id,stop_name,stop_lat,stop_lon\nS,Stop,45.5,-73.6\n",
            "trips.txt", "route_id,service_id,trip_id\n10,weekday,T\n",
            "stop_times.txt", "trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT,10:00:00,10:00:00,S,1\n");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
    }
    public static void main(String[] args) throws Exception {
        var clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
        var empty = FeedMessage.newBuilder().setHeader(StmRealtimeAdapter.header(NOW)).build().toByteArray();
        var adapter = new StmRealtimeAdapter(Path.of("."), clock, (url,key) -> url.equals(StmRealtimeAdapter.TRIPS) ? empty :
            ("{\"header\":{\"timestamp\":"+NOW+"},\"alerts\":[]}").getBytes(), StmRealtimeAdapter.JSON.readTree("{}"), "fixture", java.net.URI.create("http://127.0.0.1:18085"), true);
        adapter.refresh();
        var schedule = new ExoRealtimeFeed.ScheduleIds(Set.of("T"), Set.of("S"), Set.of("10","11"), Set.of("CITSO"), Map.of("T","10"));
        var so = new ExoRealtimeFeed(clock, alert -> alert ? empty : update("T","10"), schedule, 60);
        var la = new ExoRealtimeFeed(clock, alert -> alert ? empty : update("T","10"), schedule, 90);
        so.refresh(); la.refresh();
        adapter.exoBuses = Map.of("EXO_BUS_CITSO",so,"EXO_BUS_CITLA",la);
        check(adapter.healthByFeed().has("EXO_BUS_CITSO") && adapter.healthByFeed().has("EXO_BUS_CITLA"), "Separate bus feed namespaces");
        check(adapter.predictionsUsable(Set.of("EXO_BUS_CITSO","EXO_BUS_CITLA")), "Both sectors fresh");
        rejects(() -> so.validate(update("T","11"), false), "A known trip cannot be applied to the wrong route");
        rejects(() -> so.validate(update("UNKNOWN","10"), false), "Unknown trips must be rejected");
        so.mode = "feed-outage"; so.refresh();
        check(!adapter.predictionsUsable(Set.of("EXO_BUS_CITSO")), "Affected sector falls back");
        check(adapter.predictionsUsable(Set.of("STM","EXO_BUS_CITLA")), "STM and the other bus sector remain realtime");
        check(so.snapshot.trips().timestamp() == NOW, "Outage cannot renew source timestamp");
        check(so.snapshot.trips().downloadedAt() == clock.millis(), "Outage preserves last success");
        var dynamic = StmRealtimeAdapter.JSON.createObjectNode();
        dynamic.putObject("extensions").put("realtimeFeedsFromItinerary",true).putArray("realtimeFeeds").add("STM").add("EXO_TRAINS");
        check(StmRealtimeAdapter.initialFeeds(dynamic).isEmpty(), "Unused mode hints must not disable realtime");
        check(StmRealtimeAdapter.initialFeeds(StmRealtimeAdapter.JSON.createObjectNode()).equals(Set.of("STM")), "Legacy debug scope retained");
        var original = journeys("STM:55","EXO_BUS_CITSO:10","EXO_BUS_CITLA:10");
        check(adapter.needsScheduledFallback(original), "One affected alternative triggers a scheduled retry");
        check(!adapter.needsScheduledFallback(journeys("STM:55")), "STM-only alternatives need no bus fallback");
        var scheduled = journeys("EXO_BUS_CITSO:10");
        // A whole-graph scheduled replan can also return healthy-feed journeys. They must not hide known delays.
        ((com.fasterxml.jackson.databind.node.ArrayNode) scheduled.path("data").path("planConnection").path("edges"))
            .add(journeys("STM:55").path("data").path("planConnection").path("edges").get(0));
        ((ObjectNode)scheduled.path("data").path("planConnection").path("edges").get(0).path("node").path("legs").get(0)).put("realTime",false);
        var merged = adapter.mergeScheduledFallback(original,scheduled);
        check(merged.path("data").path("planConnection").path("edges").size()==3, "Healthy alternatives retained and affected one replaced");
        for (var edge : merged.path("data").path("planConnection").path("edges")) {
            var leg = edge.path("node").path("legs").get(0);
            check(leg.path("realTime").asBoolean() != leg.path("route").path("gtfsId").asText().startsWith("EXO_BUS_CITSO:"), "Only the affected bus journey is scheduled");
        }
        var noJourneys = journeys();
        check(adapter.needsScheduledFallback(noJourneys,Set.of("EXO_BUS_CITSO")), "Missing journeys still retry unavailable feeds");
        check(!adapter.needsScheduledFallback(noJourneys,Set.of("STM")), "No scheduled retry for healthy cancellation results");
        var busRequest = StmRealtimeAdapter.JSON.createObjectNode();
        busRequest.putObject("variables").putObject("modes").putObject("transit").putArray("transit")
            .addObject().put("mode","BUS");
        check(adapter.requestedFeeds(busRequest).equals(Set.of("STM","EXO_BUS_CITSO","EXO_BUS_CITLA")), "Empty bus results include configured bus sectors");
        routingFallbackOverHttp();
        so.mode = "live"; so.refresh(); check(adapter.predictionsUsable(Set.of("EXO_BUS_CITSO")), "Bus recovery restores predictions");
        var request = ExoRealtimeFeed.request("CITSO","fixture & key",false);
        check(request.uri().getPath().equals("/api/opendata/v1/CITSO/tripupdate"), "API request uses selected sector");
        check(request.uri().getRawQuery().equals("token=fixture+%26+key"), "Subscription key is URL-encoded");
        check(ExoRealtimeFeed.request("CITLA","fixture",true).uri().getPath().endsWith("/CITLA/alert"), "Alerts use their own sector endpoint");
        rejects(() -> ExoRealtimeFeed.request("../TRAINS","fixture",false), "Agency paths cannot escape the API prefix");
        Path root = Files.createTempDirectory(Path.of(args[0]), "bus-fixture-");
        Files.writeString(root.resolve("graph.obj"), "graph fixture");
        schedule(root.resolve(ExoRealtimeFeed.busArchive("CITSO")),"CITSO");
        schedule(root.resolve(ExoRealtimeFeed.busArchive("CITLA")),"CITLA");
        var manifest = StmRealtimeAdapter.JSON.createObjectNode();
        manifest.putArray("agencies").add("CITSO").add("CITLA"); manifest.put("graphSha256", ExoRealtimeFeed.sha256(root.resolve("graph.obj")));
        var hashes = manifest.putObject("scheduleSha256");
        for (String agency : List.of("CITSO","CITLA")) hashes.put(agency,ExoRealtimeFeed.sha256(root.resolve(ExoRealtimeFeed.busArchive(agency))));
        Files.writeString(root.resolve(ExoRealtimeFeed.BUS_CATALOG), manifest.toString());
        check(ExoRealtimeFeed.loadBuses(root,clock,null).values().stream().allMatch(feed -> feed.download == null), "Bus schedules work without realtime subscriptions");
        Files.writeString(root.resolve(ExoRealtimeFeed.BUS_CONFIG), "{\"feeds\":[{\"agency\":\"CITSO\",\"token\":\"fixture\",\"pollSeconds\":60}]}");
        var loaded = ExoRealtimeFeed.loadBuses(root,clock,null);
        check(loaded.get("EXO_BUS_CITSO").download != null && loaded.get("EXO_BUS_CITLA").download == null, "Only explicitly configured sectors poll upstream");
        check(loaded.get("EXO_BUS_CITSO").schedule.matches(TripDescriptor.newBuilder().setTripId("T").setRouteId("10").build()), "Real CSV bus identifiers load correctly");
        Files.writeString(root.resolve("graph.obj"), "another graph");
        rejects(() -> ExoRealtimeFeed.loadBuses(root,clock,null), "Changed graphs cannot accept bus realtime");
        System.out.println("PASS: " + checks + " exo bus fixture checks; no API calls made.");
    }

    /** Actual bridge requests against a local OTP fixture, with no agency requests or real graph. */
    static void routingFallbackOverHttp() throws Exception {
        var requests = new ArrayList<Boolean>();
        var otp = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        otp.createContext("/", exchange -> {
            boolean scheduled = StmRealtimeAdapter.JSON.readTree(exchange.getRequestBody()).path("variables").path("excludeRealtime").asBoolean();
            requests.add(scheduled);
            var response = scheduled ? journeys("STM:55") : journeys();
            StmRealtimeAdapter.send(exchange,200,"application/json",StmRealtimeAdapter.JSON.writeValueAsBytes(response));
        });
        otp.start();
        var adapter = new StmRealtimeAdapter(Path.of("."),Clock.systemUTC(),(url,key)->new byte[0],
            StmRealtimeAdapter.JSON.createObjectNode(),"fixture",java.net.URI.create("http://127.0.0.1:"+otp.getAddress().getPort()),false);
        adapter.http = java.net.http.HttpClient.newHttpClient();
        var bridge = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        bridge.createContext("/",adapter::handle);
        bridge.start();
        try {
            var body = StmRealtimeAdapter.JSON.createObjectNode();
            body.put("query","query { planConnection(preferences:{transit:{timetable:{excludeRealTimeUpdates:$excludeRealtime}}}) { edges { node { duration } } } }");
            body.putObject("variables").put("excludeRealtime",false);
            body.putObject("extensions").put("realtimeFeedsFromItinerary",true).putArray("realtimeFeeds").add("STM");
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+bridge.getAddress().getPort()+"/otp/gtfs/v1"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type","application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var response = adapter.http.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==200,"Empty-result fallback succeeds over HTTP");
            check(requests.equals(List.of(false,true)),"Unavailable feeds get exactly one scheduled retry");
            check(StmRealtimeAdapter.JSON.readTree(response.body()).path("data").path("planConnection").path("edges").size()==1,
                "The scheduled journey survives the production bridge response");
        } finally { bridge.stop(0); otp.stop(0); }
    }
}
