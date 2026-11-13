import com.fasterxml.jackson.databind.JsonNode;
import com.google.transit.realtime.GtfsRealtime.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;

/** Polls the optional train subscription and checks identifiers against the local train GTFS. */
final class ExoRealtimeFeed {
    static final String ID = "EXO_TRAINS";
    static final String CONFIG = "exo-realtime.local.json";
    private static final String ENDPOINT = "https://exo.chrono-saeiv.com/api/opendata/v1/TRAINS/";

    interface Download { byte[] fetch(boolean alerts) throws Exception; }

    record ScheduleIds(Set<String> trips, Set<String> stops, Set<String> routes, Set<String> agencies) {
        static ScheduleIds read(Path zip) throws IOException {
            var dao = new GtfsDaoImpl();
            var reader = new GtfsReader();
            reader.setEntityStore(dao);
            reader.setInputLocation(zip.toFile());
            reader.run();
            return new ScheduleIds(
                dao.getAllTrips().stream().map(t -> t.getId().getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllStops().stream().map(s -> s.getId().getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllRoutes().stream().map(r -> r.getId().getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllAgencies().stream().map(a -> a.getId()).collect(Collectors.toUnmodifiableSet()));
        }

        boolean matches(TripDescriptor trip) {
            return (!trip.hasTripId() || trips.contains(trip.getTripId())) &&
                (!trip.hasRouteId() || routes.contains(trip.getRouteId()));
        }

        boolean matches(EntitySelector selector) {
            if (selector.hasTrip() && !selector.getTrip().hasTripId() && !selector.getTrip().hasRouteId()) return false;
            return (selector.hasAgencyId() || selector.hasRouteId() || selector.hasStopId() || selector.hasTrip()) &&
                (!selector.hasAgencyId() || agencies.contains(selector.getAgencyId())) &&
                (!selector.hasRouteId() || routes.contains(selector.getRouteId())) &&
                (!selector.hasStopId() || stops.contains(selector.getStopId())) &&
                (!selector.hasTrip() || matches(selector.getTrip()));
        }
    }

    final Clock clock;
    final Download download;
    final ScheduleIds schedule;
    final long pollSeconds;
    volatile StmRealtimeAdapter.Snapshot snapshot;
    volatile String mode = "live";

    ExoRealtimeFeed(Clock clock, Download download, ScheduleIds schedule, long pollSeconds) {
        this.clock = clock;
        this.download = download;
        this.schedule = schedule;
        this.pollSeconds = pollSeconds;
        String error = download == null ? "not_configured" : "not_downloaded";
        snapshot = new StmRealtimeAdapter.Snapshot(
            new StmRealtimeAdapter.FeedState(null, 0, 0, error, 0, 0, StmRealtimeAdapter.MAX_AGE_SECONDS),
            new StmRealtimeAdapter.FeedState(null, 0, 0, error, 0, 0, StmRealtimeAdapter.MAX_ALERT_AGE_SECONDS));
    }

    static ExoRealtimeFeed load(Path root, Clock clock, HttpClient http) throws IOException {
        Path file = root.resolve(CONFIG);
        if (!Files.isRegularFile(file)) return new ExoRealtimeFeed(clock, null, null, 0);
        JsonNode config;
        try { config = StmRealtimeAdapter.JSON.readTree(file.toFile()); }
        catch (IOException e) { throw new IOException("Invalid exo realtime configuration"); }
        String token = config.path("token").asText("").trim();
        long seconds = config.path("pollSeconds").asLong(0);
        if (token.isBlank() || seconds < 30 || seconds > 3600) {
            throw new IOException("Set an exo subscription token and pollSeconds between 30 and 3600");
        }
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        long[] retryAt = new long[2];
        Download download = alerts -> {
            int feedIndex = alerts ? 1 : 0;
            if (clock.millis() < retryAt[feedIndex]) throw new IOException("EXO HTTP 429");
            String resource = alerts ? "alert" : "tripupdate";
            var request = HttpRequest.newBuilder(URI.create(ENDPOINT + resource + "?token=" + encodedToken))
                .timeout(Duration.ofSeconds(12)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 429) {
                long fallback = clock.millis() + 2 * seconds * 1000;
                String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                retryAt[feedIndex] = retryAfterMillis(retryAfter, clock, fallback);
            }
            if (response.statusCode() != 200) throw new IOException("EXO HTTP " + response.statusCode());
            return response.body();
        };
        return new ExoRealtimeFeed(clock, download, ScheduleIds.read(root.resolve("exo-trains-gtfs.zip")), seconds);
    }

    private static long retryAfterMillis(String value, Clock clock, long fallback) {
        try { return clock.instant().plusSeconds(Long.parseLong(value)).toEpochMilli(); }
        catch (RuntimeException e) {
            try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
            catch (RuntimeException ignored) { return fallback; }
        }
    }

    void refresh() {
        if (download == null) return;
        var previous = snapshot;
        String currentMode = mode;
        snapshot = new StmRealtimeAdapter.Snapshot(
            refreshOne(false, previous.trips(), currentMode), refreshOne(true, previous.alerts(), currentMode));
    }

    private StmRealtimeAdapter.FeedState refreshOne(boolean alerts, StmRealtimeAdapter.FeedState previous,
                                                    String currentMode) {
        if (currentMode.equals("stale")) return previous;
        try {
            if (currentMode.equals("feed-outage")) throw new IOException("simulated_outage");
            return validate(download.fetch(alerts), alerts);
        } catch (Exception e) {
            String message = e.getMessage();
            String failure = message != null && message.matches("EXO HTTP \\d{3}")
                ? message : e.getClass().getSimpleName();
            return new StmRealtimeAdapter.FeedState(previous.bytes(), previous.timestamp(), previous.downloadedAt(),
                failure, previous.mapped(), previous.skipped(), previous.maxAgeSeconds());
        }
    }

    StmRealtimeAdapter.FeedState validate(byte[] bytes, boolean alerts) throws IOException {
        var source = FeedMessage.parseFrom(bytes);
        if (!source.hasHeader() || !source.getHeader().hasTimestamp() ||
            source.getHeader().getIncrementality() != FeedHeader.Incrementality.FULL_DATASET) {
            throw new IOException("Expected a timestamped exo full feed");
        }
        var accepted = FeedMessage.newBuilder().setHeader(source.getHeader());
        int skipped = 0;
        for (var entity : source.getEntityList()) {
            if (alerts && entity.hasAlert()) {
                var alert = entity.getAlert();
                var matches = alert.getInformedEntityList().stream().filter(schedule::matches).toList();
                if (!matches.isEmpty()) {
                    var normalized = alert.toBuilder().clearInformedEntity().addAllInformedEntity(matches);
                    if (alert.hasHeaderText()) normalized.setHeaderText(withFallback(alert.getHeaderText()));
                    if (alert.hasDescriptionText()) normalized.setDescriptionText(withFallback(alert.getDescriptionText()));
                    accepted.addEntity(entity.toBuilder().setAlert(normalized));
                    continue;
                }
            } else if (!alerts && entity.hasTripUpdate()) {
                var update = entity.getTripUpdate();
                if (update.getTrip().hasTripId() && schedule.matches(update.getTrip()) &&
                    update.getStopTimeUpdateList().stream().allMatch(stop -> !stop.hasStopId() ||
                        schedule.stops().contains(stop.getStopId()))) {
                    accepted.addEntity(entity);
                    continue;
                }
            }
            skipped++;
        }
        var filtered = accepted.build();
        if (source.getEntityCount() > 0 && filtered.getEntityCount() == 0) {
            throw new IOException("No exo entities match the imported schedule");
        }
        return new StmRealtimeAdapter.FeedState(filtered.toByteArray(), source.getHeader().getTimestamp(),
            clock.millis(), null, filtered.getEntityCount(), skipped,
            alerts ? StmRealtimeAdapter.MAX_ALERT_AGE_SECONDS : StmRealtimeAdapter.MAX_AGE_SECONDS);
    }

    private static TranslatedString withFallback(TranslatedString text) {
        var translations = text.getTranslationList().stream().filter(t -> !t.getText().isBlank()).toList();
        var result = TranslatedString.newBuilder().addAllTranslation(translations);
        if (!translations.isEmpty() && translations.stream().noneMatch(t -> t.getLanguage().isBlank())) {
            result.addTranslation(translations.getFirst().toBuilder().clearLanguage());
        }
        return result.build();
    }
}
