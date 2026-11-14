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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;

/** Polls a Chrono subscription and checks identifiers against that sector's matching GTFS. */
final class ExoRealtimeFeed {
    static final String ID = "EXO_TRAINS";
    static final String CONFIG = "exo-realtime.local.json";
    static final String BUS_CONFIG = "exo-bus-realtime.local.json";
    static final String BUS_CATALOG = "exo-buses.json";
    static final String BUS_PREFIX = "EXO_BUS_";

    interface Download { byte[] fetch(boolean alerts) throws Exception; }

    record ScheduleIds(Set<String> trips, Set<String> stops, Set<String> routes, Set<String> agencies, Map<String, String> tripRoutes) {
        ScheduleIds(Set<String> trips, Set<String> stops, Set<String> routes, Set<String> agencies) { this(trips, stops, routes, agencies, Map.of()); }
        static ScheduleIds read(Path zip) throws IOException {
            return read(zip, false);
        }
        static ScheduleIds read(Path zip, boolean busOnly) throws IOException {
            var dao = new GtfsDaoImpl();
            var reader = new GtfsReader();
            reader.setEntityStore(dao);
            reader.setEntityClasses(List.of(org.onebusaway.gtfs.model.Agency.class, org.onebusaway.gtfs.model.Route.class,
                org.onebusaway.gtfs.model.Stop.class, org.onebusaway.gtfs.model.Trip.class));
            reader.setInputLocation(zip.toFile());
            reader.run();
            if (busOnly && (dao.getAllRoutes().isEmpty() || dao.getAllRoutes().stream().anyMatch(route -> route.getType() != 3)))
                throw new IOException("Expected an exo bus GTFS archive with bus routes (route_type 3)");
            return new ScheduleIds(
                dao.getAllTrips().stream().map(t -> t.getId().getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllStops().stream().map(s -> s.getId().getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllRoutes().stream().map(r -> r.getId().getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllAgencies().stream().map(a -> a.getId()).collect(Collectors.toUnmodifiableSet()),
                dao.getAllTrips().stream().collect(Collectors.toUnmodifiableMap(t -> t.getId().getId(), t -> t.getRoute().getId().getId())));
        }

        boolean matches(TripDescriptor trip) {
            return (!trip.hasTripId() || trips.contains(trip.getTripId())) &&
                (!trip.hasRouteId() || routes.contains(trip.getRouteId())) &&
                (!trip.hasTripId() || !trip.hasRouteId() || tripRoutes.isEmpty() || trip.getRouteId().equals(tripRoutes.get(trip.getTripId())));
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
        return subscribed(root.resolve("exo-trains-gtfs.zip"), "TRAINS", token, seconds, clock, http);
    }

    static String busAgency(String agency) throws IOException {
        if (!agency.matches("[A-Z][A-Z0-9_]{1,31}") || agency.equals("TRAINS")) throw new IOException("Invalid exo bus agency code");
        return agency;
    }

    static String busArchive(String agency) throws IOException { return "exo-bus-" + busAgency(agency) + "-gtfs.zip"; }

    static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(file), digest)) { input.transferTo(java.io.OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable"); }
    }

    /** Binds bus schedules to the graph produced by Prepare-ExoBusGraph.ps1. */
    static List<String> busAgencies(Path root) throws IOException {
        if (!Files.exists(root.resolve(BUS_CATALOG))) return List.of();
        JsonNode catalog;
        try { catalog = StmRealtimeAdapter.JSON.readTree(root.resolve(BUS_CATALOG).toFile()); }
        catch (IOException e) { throw new IOException("Invalid exo bus graph manifest"); }
        if (!catalog.path("agencies").isArray() || catalog.path("agencies").size() > 16 ||
            !sha256(root.resolve("graph.obj")).equalsIgnoreCase(catalog.path("graphSha256").asText()))
            throw new IOException("exo bus graph does not match its manifest; rebuild with Prepare-ExoBusGraph.ps1");
        var agencies = new java.util.ArrayList<String>();
        for (JsonNode value : catalog.path("agencies")) {
            String agency = busAgency(value.asText());
            if (agencies.contains(agency)) throw new IOException("Duplicate exo bus agency");
            if (!sha256(root.resolve(busArchive(agency))).equalsIgnoreCase(catalog.path("scheduleSha256").path(agency).asText()))
                throw new IOException("exo bus schedule changed; rebuild the graph before using realtime");
            agencies.add(agency);
        }
        return List.copyOf(agencies);
    }

    static Map<String, ExoRealtimeFeed> loadBuses(Path root, Clock clock, HttpClient http) throws IOException {
        var agencies = busAgencies(root);
        JsonNode config = StmRealtimeAdapter.JSON.createObjectNode();
        if (Files.exists(root.resolve(BUS_CONFIG))) {
            try { config = StmRealtimeAdapter.JSON.readTree(root.resolve(BUS_CONFIG).toFile()); }
            catch (IOException e) { throw new IOException("Invalid exo bus realtime configuration"); }
            if (!config.path("feeds").isArray()) throw new IOException("exo bus configuration requires a feeds array");
        }
        var settings = new LinkedHashMap<String, JsonNode>();
        for (JsonNode value : config.path("feeds")) {
            String agency = busAgency(value.path("agency").asText());
            if (!agencies.contains(agency) || settings.putIfAbsent(agency, value) != null)
                throw new IOException("exo bus credential agency is missing from the graph or repeated");
        }
        var result = new LinkedHashMap<String, ExoRealtimeFeed>();
        for (String agency : agencies) {
            JsonNode value = settings.get(agency);
            boolean enabled = value != null && value.path("enabled").asBoolean(true);
            String token = value == null ? "" : value.path("token").asText("").trim();
            long seconds = value == null ? 60 : value.path("pollSeconds").asLong(60);
            if (seconds < 30 || seconds > 3600 || (enabled && token.isBlank())) throw new IOException("Set an exo bus token and pollSeconds between 30 and 3600");
            var feed = enabled ? subscribed(root.resolve(busArchive(agency)), agency, token, seconds, clock, http)
                : new ExoRealtimeFeed(clock, null, null, 0);
            result.put(BUS_PREFIX + agency, feed);
        }
        return result;
    }

    private static ExoRealtimeFeed subscribed(Path archive, String agency, String token, long seconds, Clock clock, HttpClient http) throws IOException {
        long[] retryAt = new long[2];
        Download download = alerts -> {
            int feedIndex = alerts ? 1 : 0;
            if (clock.millis() < retryAt[feedIndex]) throw new IOException("EXO HTTP 429");
            var request = request(agency, token, alerts);
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 429) {
                long fallback = clock.millis() + 2 * seconds * 1000;
                String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                retryAt[feedIndex] = retryAfterMillis(retryAfter, clock, fallback);
            }
            if (response.statusCode() != 200) throw new IOException("EXO HTTP " + response.statusCode());
            return response.body();
        };
        return new ExoRealtimeFeed(clock, download, ScheduleIds.read(archive, !agency.equals("TRAINS")), seconds);
    }

    static HttpRequest request(String agency, String token, boolean alerts) throws IOException {
        if (!agency.equals("TRAINS")) busAgency(agency);
        String resource = alerts ? "alert" : "tripupdate";
        return HttpRequest.newBuilder(URI.create("https://exo.chrono-saeiv.com/api/opendata/v1/" + agency + "/" + resource +
            "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(12)).GET().build();
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
