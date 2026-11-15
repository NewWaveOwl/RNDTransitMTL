import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.transit.realtime.GtfsRealtime.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic tests for conversion and failure policy, requiring no server or API key. */
public class StmRealtimeAdapterTest {
    static final long NOW = 1794024913L;
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static final String SOURCE = """
        {"header":{"timestamp":1794024913},"alerts":[{
          "active_periods":{"start":1792342860,"end":null},
          "cause":null,"effect":null,
          "informed_entities":[{"route_short_name":"55"},{"direction_id":"S"},{"stop_code":"52807"}],
          "header_texts":[{"language":"fr","text":"Votre arrêt"},{"language":"en","text":null}],
          "description_texts":[{"language":"fr","text":"<b>Arrêt déplacé</b> &amp; travaux"}]
        }]}
        """;

    public static void main(String[] args) throws Exception {
        var mapper = StmRealtimeAdapter.JSON;
        var index = mapper.readTree("""
            {"routes":{"55":["55"]},"stops":{"52807":["52807"]},"directions":{"55:S":0,"55:N":1}}
            """);
        var clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
        byte[] predictions = FeedMessage.newBuilder().setHeader(StmRealtimeAdapter.header(NOW)).build().toByteArray();
        var response = new AtomicReference<byte[]>(SOURCE.getBytes(StandardCharsets.UTF_8));
        StmRealtimeAdapter.Fetcher fetch = (url, key) -> {
            if (key.equals("invalid-test-key")) throw new java.io.IOException("HTTP 400");
            return url.equals(StmRealtimeAdapter.TRIPS) ? predictions : response.get();
        };
        var adapter = new StmRealtimeAdapter(Path.of("."), clock, fetch, index, "test-only",
            URI.create("http://127.0.0.1:18085/otp/gtfs/v1"), true);
        var converted = adapter.convertAlerts(response.get());
        FeedMessage feed = FeedMessage.parseFrom(converted.bytes());
        check(feed.getEntityCount() == 1, "One real schema alert should map");
        var alert = feed.getEntity(0).getAlert();
        var entity = alert.getInformedEntity(0);
        check(entity.getRouteId().equals("55") && entity.getStopId().equals("52807"), "Route AND stop scope");
        check(entity.getDirectionId() == 0, "Verified southbound direction");
        check(entity.getDirectionId() != 1, "Northbound must stay unaffected");
        check(!entity.getRouteId().equals("24"), "Other routes must stay unaffected");
        check(alert.getHeaderText().getTranslationCount() == 1, "Null English translation excluded");
        check(alert.getDescriptionText().getTranslation(0).getText().equals("Arrêt déplacé & travaux"),
            "HTML must be displayed as plain text");
        check(!alert.getActivePeriod(0).hasEnd(), "Open-ended alert period preserved");
        check(feed.getEntity(0).getId().equals(FeedMessage.parseFrom(adapter.convertAlerts(response.get()).bytes())
            .getEntity(0).getId()), "Stable source alert ID");
        check(adapter.convertAlerts(SOURCE.replace("\"S\"", "\"W\"").getBytes(StandardCharsets.UTF_8)).mapped() == 0,
            "Unknown directions cannot broaden scope");
        check(adapter.convertAlerts(SOURCE.replace("\"52807\"", "\"99999\"").getBytes(StandardCharsets.UTF_8)).mapped() == 0,
            "Unknown stops cannot become route-wide alerts");
        adapter.refresh();
        check(adapter.fresh(adapter.snapshot.trips()), "Valid predictions should be fresh");
        check(adapter.fresh(adapter.snapshot.alerts()), "Converted source timestamp preserved");
        var original = adapter.snapshot;
        var olderClock = Clock.fixed(Instant.ofEpochSecond(NOW + 181), ZoneOffset.UTC);
        var aged = new StmRealtimeAdapter(Path.of("."), olderClock, fetch, index, "test-only",
            URI.create("http://127.0.0.1:18085/otp/gtfs/v1"), true);
        aged.snapshot = original;
        check(!aged.fresh(aged.snapshot.trips()), "A successful old download is stale");
        check(aged.fresh(aged.snapshot.alerts()), "Service alerts have a separate ten-minute freshness limit");
        var expired = new StmRealtimeAdapter(Path.of("."),
            Clock.fixed(Instant.ofEpochSecond(NOW + 601), ZoneOffset.UTC), fetch, index, "test-only",
            URI.create("http://127.0.0.1:18085/otp/gtfs/v1"), true);
        expired.snapshot = original;
        check(!expired.fresh(expired.snapshot.alerts()), "Alerts older than ten minutes are stale");
        ObjectNode journey = (ObjectNode) mapper.readTree("""
            {"data":{"planConnection":{"edges":[{"node":{"legs":[{
              "start":{"scheduledTime":"2026-11-07T00:21:00-05:00"},
              "end":{"scheduledTime":"2026-11-07T00:27:00-05:00"},
              "alerts":[{"alertHash":1,"activityPeriods":[]}],
              "trip":{"pattern":{"code":"STM:55:0:01","alerts":[
                {"alertHash":1,"activityPeriods":[]},
                {"alertHash":2,"activityPeriods":[{"start":"2026-10-18T13:01:00-04:00","end":null}]},
                {"alertHash":3,"activityPeriods":[{"start":"2026-11-08T00:00:00-05:00","end":null}]},
                {"alertHash":4,"activityPeriods":[{"start":null,"end":"2026-11-07T00:21:00-05:00"}]},
                {"alertHash":5,"activityPeriods":[{"start":"bad","end":null}]}
              ]}}
            }]}}]}}}
            """);
        StmRealtimeAdapter.mapJourneyAlerts(journey, true);
        var journeyLeg = journey.path("data").path("planConnection").path("edges").get(0)
            .path("node").path("legs").get(0);
        check(journeyLeg.path("alerts").size() == 2, "Merge exact pattern alerts, deduplicate, exclude future/expired/malformed periods");
        check(journeyLeg.path("alerts").get(1).path("alertHash").asInt() == 2, "Applicable directional alert included");
        StmRealtimeAdapter.mapJourneyAlerts(journey, false);
        check(journeyLeg.path("alerts").isEmpty() && journeyLeg.path("trip").path("pattern").path("alerts").isEmpty(),
            "Stale alerts withheld at both levels");
        ObjectNode query = mapper.createObjectNode().put("query",
            "query($excludeRealtime:Boolean!){planConnection(preferences:{transit:{timetable:{excludeRealTimeUpdates:$excludeRealtime}}}){edges{node{duration}}}}");
        check(aged.routingRequest(query.deepCopy()).path("variables").path("excludeRealtime").asBoolean(),
            "Stale data forces scheduled routing");
        check(!adapter.routingRequest(query.deepCopy()).path("variables").path("excludeRealtime").asBoolean(),
            "Fresh data permits realtime routing");
        var forwarded = adapter.otpRequest(query.deepCopy());
        check(forwarded.version().orElseThrow() == java.net.http.HttpClient.Version.HTTP_1_1,
            "OTP forwarding must avoid an HTTP/2 upgrade");
        check(forwarded.method().equals("POST") && forwarded.bodyPublisher().orElseThrow().contentLength() > 0,
            "GraphQL POST has a fixed nonempty request body");
        check(StmRealtimeAdapter.otpResponse(200, "{\"data\":{}}".getBytes()).has("data"),
            "JSON OTP replies are preserved");
        try {
            StmRealtimeAdapter.otpResponse(500, "<html>Error</html>".getBytes());
            throw new AssertionError("An OTP HTTP 500 must stay an explicit upstream failure");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().equals("OTP returned HTTP 500"), "Explicit upstream HTTP status");
        }
        adapter.mode = "invalid-key";
        adapter.refresh();
        check(!adapter.fresh(adapter.snapshot.trips()), "Rejected key must withhold fresh status");
        check(adapter.snapshot.trips().timestamp() == NOW, "Failure cannot renew feed timestamp");
        check(adapter.routingRequest(query.deepCopy()).path("variables").path("excludeRealtime").asBoolean(),
            "Rejected key uses schedules");
        adapter.mode = "feed-outage";
        adapter.refresh();
        check(!adapter.fresh(adapter.snapshot.alerts()), "Outage must withhold fresh alerts");
        adapter.mode = "live";
        adapter.refresh();
        check(adapter.fresh(adapter.snapshot.trips()), "Restored feed recovers");
        check(adapter.snapshot.alerts().mapped() == 1, "Alerts recover");
        response.set("{broken".getBytes(StandardCharsets.UTF_8));
        adapter.refresh();
        check(!adapter.fresh(adapter.snapshot.alerts()), "Malformed JSON cannot renew freshness");
        check(adapter.snapshot.alerts().timestamp() == NOW, "Malformed JSON preserves old timestamp");
        verifyExo(adapter, query, clock);
        System.out.println("PASS: alert scope, direction, translations, periods, stale data, invalid key, outage, recovery and scheduled fallback.");
    }

    private static void verifyExo(StmRealtimeAdapter adapter, ObjectNode query, Clock clock) throws Exception {
        var schedule = new ExoRealtimeFeed.ScheduleIds(java.util.Set.of("train"), java.util.Set.of("vendome"),
            java.util.Set.of("1"), java.util.Set.of("exo"));
        var matched = TripUpdate.newBuilder().setTrip(TripDescriptor.newBuilder().setTripId("train").setRouteId("1"))
            .addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder().setStopId("vendome")
                .setDeparture(TripUpdate.StopTimeEvent.newBuilder().setDelay(120)));
        var trips = FeedMessage.newBuilder().setHeader(StmRealtimeAdapter.header(NOW))
            .addEntity(FeedEntity.newBuilder().setId("matching").setTripUpdate(matched))
            .addEntity(FeedEntity.newBuilder().setId("unmatched").setTripUpdate(matched.clone()
                .setTrip(TripDescriptor.newBuilder().setTripId("unknown"))))
            .build().toByteArray();
        var alerts = FeedMessage.newBuilder().setHeader(StmRealtimeAdapter.header(NOW - 601))
            .addEntity(FeedEntity.newBuilder().setId("alert").setAlert(Alert.newBuilder()
                .addInformedEntity(EntitySelector.newBuilder().setRouteId("1"))
                .setDescriptionText(TranslatedString.newBuilder()
                    .addTranslation(TranslatedString.Translation.newBuilder().setLanguage("fr").setText("Train retardé"))
                    .addTranslation(TranslatedString.Translation.newBuilder().setLanguage("en").setText("")))))
            .build().toByteArray();
        var exo = new ExoRealtimeFeed(clock, isAlert -> isAlert ? alerts : trips, schedule, 60);
        adapter.exo = exo;
        exo.refresh();
        check(exo.snapshot.trips().mapped() == 1 && exo.snapshot.trips().skipped() == 1,
            "Only updates for imported exo identifiers may reach OTP");
        var translations = FeedMessage.parseFrom(exo.snapshot.alerts().bytes()).getEntity(0).getAlert()
            .getDescriptionText().getTranslationList();
        check(translations.stream().anyMatch(t -> !t.hasLanguage() && t.getText().equals("Train retardé")) &&
              translations.stream().noneMatch(t -> t.getText().isBlank()),
            "A blank English translation cannot hide an exo alert");
        var health = adapter.healthByFeed().path(ExoRealtimeFeed.ID);
        check(health.path("tripUpdates").path("state").asText().equals("fresh") &&
              health.path("alerts").path("state").asText().equals("stale"),
            "exo alert and prediction freshness must be independent");
        var mixed = java.util.Set.of("STM", ExoRealtimeFeed.ID);
        check(!adapter.routingRequest(query.deepCopy(), mixed).path("variables").path("excludeRealtime").asBoolean(),
            "Stale alerts must not disable fresh predictions");
        exo.mode = "feed-outage";
        exo.refresh();
        check(adapter.routingRequest(query.deepCopy(), mixed).path("variables").path("excludeRealtime").asBoolean(),
            "An exo outage must fall back to a consistent mixed schedule");
        check(!adapter.routingRequest(query.deepCopy()).path("variables").path("excludeRealtime").asBoolean(),
            "An exo outage must not disable STM-only predictions");
        exo.mode = "live";
        exo.refresh();
        check(adapter.predictionsUsable(mixed), "Fresh exo predictions must recover after the outage");
        System.out.println("PASS: exo identifier matching, independent alert freshness, outage, STM isolation and recovery (fixtures).");
    }
}
