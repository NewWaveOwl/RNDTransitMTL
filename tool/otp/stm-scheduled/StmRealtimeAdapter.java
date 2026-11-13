import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.transit.realtime.GtfsRealtime.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Local STM feed bridge. Uses protobuf and JSON libraries already in OTP's JAR. */
public class StmRealtimeAdapter {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String TRIPS = "https://api.stm.info/pub/od/gtfs-rt/ic/v2/tripUpdates";
    static final String ALERTS = "https://api.stm.info/pub/od/i3/v2/messages/etatservice";
    static final long MAX_AGE_SECONDS = 180;
    static final long MAX_ALERT_AGE_SECONDS = 600;
    final Path root;
    final Clock clock;
    final Fetcher fetcher;
    final JsonNode index;
    final String key;
    final URI otp;
    ExoRealtimeFeed exo;
    HttpClient http; // Supplied by main; pure conversion tests open no sockets.
    volatile Snapshot snapshot = new Snapshot(FeedState.empty(), FeedState.empty());
    volatile String mode = "live";
    final boolean testControls;

    interface Fetcher { byte[] get(String url, String key) throws Exception; }
    record FeedState(byte[] bytes, long timestamp, long downloadedAt, String error, int mapped, int skipped,
                     long maxAgeSeconds) {
        static FeedState empty() { return new FeedState(null, 0, 0, "not_downloaded", 0, 0, MAX_AGE_SECONDS); }
    }
    record Snapshot(FeedState trips, FeedState alerts) {}

    StmRealtimeAdapter(Path root, Clock clock, Fetcher fetcher, JsonNode index,
                       String key, URI otp, boolean testControls) {
        this.root = root;
        this.clock = clock;
        this.fetcher = fetcher;
        this.index = index;
        this.key = key;
        this.otp = otp;
        this.testControls = testControls;
        this.exo = new ExoRealtimeFeed(clock, null, null, 0);
    }

    boolean fresh(FeedState feed) {
        long age = clock.instant().getEpochSecond() - feed.timestamp;
        return feed.bytes != null && feed.error == null && age >= -30 && age <= feed.maxAgeSeconds;
    }

    static TranslatedString translations(JsonNode values) {
        var result = TranslatedString.newBuilder();
        for (JsonNode value : values) {
            if (!value.path("text").isTextual() || value.path("text").asText().isBlank()) continue;
            String text = value.path("text").asText().replaceAll("<[^>]*>", "")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'");
            var translation = TranslatedString.Translation.newBuilder().setText(text);
            if (value.path("language").isTextual()) translation.setLanguage(value.path("language").asText());
            result.addTranslation(translation);
        }
        return result.build();
    }

    List<EntitySelector> selectors(JsonNode alert) {
        var routes = new ArrayList<String>();
        var stops = new ArrayList<String>();
        String direction = null;
        for (JsonNode entity : alert.path("informed_entities")) {
            if (entity.has("route_short_name")) {
                JsonNode matches = index.path("routes").path(entity.path("route_short_name").asText());
                if (!matches.isArray() || matches.isEmpty()) return List.of();
                for (JsonNode match : matches) routes.add(match.asText());
            }
            if (entity.has("stop_code")) {
                JsonNode matches = index.path("stops").path(entity.path("stop_code").asText());
                if (!matches.isArray() || matches.isEmpty()) return List.of();
                for (JsonNode match : matches) stops.add(match.asText());
            }
            if (entity.has("direction_id")) {
                String candidate = entity.path("direction_id").asText();
                if (direction != null && !direction.equals(candidate)) return List.of();
                direction = candidate;
            }
        }
        if (routes.isEmpty()) return List.of(); // Never invent an agency-wide alert.
        var result = new ArrayList<EntitySelector>();
        for (String route : routes) {
            var selector = EntitySelector.newBuilder().setRouteId(route);
            if (direction != null) {
                JsonNode mapped = index.path("directions").path(route + ":" + direction);
                if (!mapped.isInt()) return List.of();
                selector.setDirectionId(mapped.asInt());
            }
            if (stops.isEmpty()) result.add(selector.build());
            for (String stop : stops) result.add(selector.clone().setStopId(stop).build());
        }
        return result;
    }

    FeedState convertAlerts(byte[] input) throws Exception {
        JsonNode document = JSON.readTree(input);
        if (!document.path("header").path("timestamp").isIntegralNumber() ||
            !document.path("alerts").isArray()) throw new IOException("Invalid STM alerts document");
        long timestamp = document.path("header").path("timestamp").asLong();
        var feed = FeedMessage.newBuilder().setHeader(header(timestamp));
        int skipped = 0;
        for (JsonNode source : document.path("alerts")) {
            var entities = selectors(source);
            var title = translations(source.path("header_texts"));
            var description = translations(source.path("description_texts"));
            JsonNode period = source.path("active_periods");
            if (entities.isEmpty() || (title.getTranslationCount() == 0 && description.getTranslationCount() == 0) ||
                !period.path("start").isIntegralNumber()) { skipped++; continue; }
            long start = period.path("start").asLong();
            if (start < 0 || (period.path("end").isNumber() && period.path("end").asLong() < start)) {
                skipped++; continue;
            }
            var active = TimeRange.newBuilder().setStart(start);
            if (period.path("end").isIntegralNumber()) active.setEnd(period.path("end").asLong());
            var alert = Alert.newBuilder().addActivePeriod(active).addAllInformedEntity(entities)
                .setHeaderText(title).setDescriptionText(description);
            // STM currently returns null cause/effect. Preserve recognized enum names if supplied later.
            if (source.path("cause").isTextual()) {
                try { alert.setCause(Alert.Cause.valueOf(source.path("cause").asText())); }
                catch (IllegalArgumentException ignored) {}
            }
            if (source.path("effect").isTextual()) {
                try { alert.setEffect(Alert.Effect.valueOf(source.path("effect").asText())); }
                catch (IllegalArgumentException ignored) {}
            }
            String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JSON.writeValueAsBytes(source)));
            feed.addEntity(FeedEntity.newBuilder().setId("stm-" + id).setAlert(alert));
        }
        FeedMessage converted = feed.build();
        return new FeedState(converted.toByteArray(), timestamp, clock.millis(), null,
            converted.getEntityCount(), skipped, MAX_ALERT_AGE_SECONDS);
    }

    static FeedHeader header(long timestamp) {
        return FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0")
            .setIncrementality(FeedHeader.Incrementality.FULL_DATASET).setTimestamp(timestamp).build();
    }

    FeedState refreshOne(boolean alerts, FeedState previous) {
        try {
            if (mode.equals("feed-outage")) throw new IOException("simulated_outage");
            if (mode.equals("stale")) return previous;
            byte[] bytes = fetcher.get(alerts ? ALERTS : TRIPS, mode.equals("invalid-key") ? "invalid-test-key" : key);
            if (alerts) return convertAlerts(bytes);
            FeedMessage feed = FeedMessage.parseFrom(bytes);
            if (!feed.hasHeader() || !feed.getHeader().hasTimestamp()) throw new IOException("Missing timestamp");
            return new FeedState(bytes, feed.getHeader().getTimestamp(), clock.millis(), null,
                feed.getEntityCount(), 0, MAX_AGE_SECONDS);
        } catch (Exception error) {
            // Never log headers, keys or HTTP response bodies.
            String failure = error.getMessage() != null && error.getMessage().matches("STM HTTP \\d{3}")
                ? error.getMessage() : error.getClass().getSimpleName();
            return new FeedState(previous.bytes, previous.timestamp, previous.downloadedAt,
                failure, previous.mapped, previous.skipped, previous.maxAgeSeconds);
        }
    }

    synchronized void refresh() {
        Snapshot before = snapshot;
        snapshot = new Snapshot(refreshOne(false, before.trips), refreshOne(true, before.alerts));
        System.out.println("STM refresh: tripUpdates=" + status(snapshot.trips).path("state").asText()
            + ", alerts=" + status(snapshot.alerts).path("state").asText()
            + ", mappedAlerts=" + snapshot.alerts.mapped + ", skippedAlerts=" + snapshot.alerts.skipped);
    }

    ObjectNode status(FeedState feed) {
        return status(feed, mode);
    }

    ObjectNode status(FeedState feed, String mode) {
        ObjectNode result = JSON.createObjectNode();
        String state = feed.bytes == null ? "unavailable" : fresh(feed) ? "fresh" : "stale";
        if (feed.error != null && feed.bytes != null && mode.equals("live")) state = "unavailable";
        if (!mode.equals("live")) state = mode.equals("stale") ? "stale" : "unavailable";
        result.put("state", state);
        result.put("sourceTimestamp", mode.equals("stale") ? Math.max(0, feed.timestamp - 3600) : feed.timestamp);
        result.put("downloadedAtMillis", feed.downloadedAt);
        result.put("maxAgeSeconds", feed.maxAgeSeconds);
        result.put("mappedEntities", feed.mapped);
        result.put("skippedEntities", feed.skipped);
        if (feed.error != null) result.put("error", feed.error);
        return result;
    }

    ObjectNode health(Snapshot value) {
        return health(value, mode);
    }

    ObjectNode health(Snapshot value, String mode) {
        ObjectNode result = JSON.createObjectNode();
        result.put("checkedAtMillis", clock.millis());
        result.set("tripUpdates", status(value.trips, mode));
        result.set("alerts", status(value.alerts, mode));
        return result;
    }

    ObjectNode healthByFeed() {
        ObjectNode result = JSON.createObjectNode();
        result.set("STM", health(snapshot));
        result.set(ExoRealtimeFeed.ID, health(exo.snapshot, exo.mode));
        return result;
    }

    boolean predictionsUsable(Set<String> feeds) {
        return feeds.stream().allMatch(feed -> switch (feed) {
            case "STM" -> mode.equals("live") && fresh(snapshot.trips);
            case ExoRealtimeFeed.ID -> exo.mode.equals("live") && fresh(exo.snapshot.trips);
            default -> false;
        });
    }

    ObjectNode routingRequest(ObjectNode body) throws IOException {
        return routingRequest(body, Set.of("STM"));
    }

    ObjectNode routingRequest(ObjectNode body, Set<String> feeds) throws IOException {
        String compact = body.path("query").asText().replaceAll("\\s+", "");
        if (!compact.contains("excludeRealTimeUpdates:$excludeRealtime"))
            throw new IOException("Use the STM debug query with excludeRealtime");
        ObjectNode variables = body.with("variables");
        variables.put("excludeRealtime", !predictionsUsable(feeds) ||
            variables.path("excludeRealtime").asBoolean(false));
        return body;
    }

    HttpRequest otpRequest(ObjectNode body) throws IOException {
        // Grizzly's GraphQL endpoint uses HTTP/1.1. Avoid a cleartext HTTP/2
        // upgrade while sending the POST body.
        return HttpRequest.newBuilder(otp).version(HttpClient.Version.HTTP_1_1)
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build();
    }

    static ObjectNode otpResponse(int statusCode, byte[] bytes) throws IOException {
        if (statusCode < 200 || statusCode >= 300)
            throw new IOException("OTP returned HTTP " + statusCode);
        JsonNode parsed;
        try { parsed = JSON.readTree(bytes); }
        catch (IOException error) { throw new IOException("OTP returned a non-JSON response"); }
        if (!(parsed instanceof ObjectNode object)) throw new IOException("OTP returned an invalid JSON response");
        return object;
    }

    static void send(HttpExchange exchange, int code, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/test-mode") && testControls && exchange.getRequestMethod().equals("POST")) {
                String requested = new String(exchange.getRequestBody().readNBytes(64), StandardCharsets.UTF_8).trim();
                if (!List.of("live", "invalid-key", "feed-outage", "stale").contains(requested)) {
                    send(exchange, 400, "text/plain", "Unknown test mode".getBytes()); return;
                }
                mode = requested;
                // Fail/stale serving takes effect immediately, without restarting OTP.
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(health(snapshot)));
                return;
            }
            if (path.equals("/status") && exchange.getRequestMethod().equals("GET")) {
                ObjectNode current = health(snapshot);
                current.set("realtimeByFeed", healthByFeed());
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(current)); return;
            }
            if (path.equals("/exo/test-mode") && testControls && exchange.getRequestMethod().equals("POST")) {
                String requested = new String(exchange.getRequestBody().readNBytes(64), StandardCharsets.UTF_8).trim();
                if (!List.of("live", "feed-outage", "stale").contains(requested)) {
                    send(exchange, 400, "text/plain", "Unknown test mode".getBytes()); return;
                }
                exo.mode = requested;
                send(exchange, 200, "application/json", JSON.writeValueAsBytes(health(exo.snapshot, exo.mode)));
                return;
            }
            if (List.of("/trip-updates", "/alerts", "/exo/trip-updates", "/exo/alerts").contains(path)) {
                boolean exoFeed = path.startsWith("/exo/");
                Snapshot current = exoFeed ? exo.snapshot : snapshot;
                String currentMode = exoFeed ? exo.mode : mode;
                FeedState feed = path.endsWith("/alerts") ? current.alerts : current.trips;
                if (currentMode.equals("stale") && feed.bytes != null) {
                    // Test-only replay: old feed header, never falsely restamped as fresh.
                    var replay = FeedMessage.parseFrom(feed.bytes).toBuilder()
                        .setHeader(header(Math.max(1, feed.timestamp - 3600))).build();
                    send(exchange, 200, "application/x-protobuf", replay.toByteArray());
                    return;
                }
                boolean usable = fresh(feed) && currentMode.equals("live");
                if (!usable) {
                    // Preserve the source timestamp. Restamping an empty snapshot can make OTP
                    // reject a recovered source publication as older than the empty snapshot.
                    // The routing gateway withholds retained alerts whenever their source is stale.
                    send(exchange, 503, "text/plain", "Feed unavailable or stale".getBytes());
                } else send(exchange, 200, "application/x-protobuf", feed.bytes);
                return;
            }
            if (path.equals("/otp/gtfs/v1") && exchange.getRequestMethod().equals("POST")) {
                route(exchange); return;
            }
            send(exchange, 404, "text/plain", "Not found".getBytes());
        } catch (Exception error) {
            String detail = error.getMessage();
            if (detail == null || !detail.startsWith("OTP ")) detail = error.getClass().getSimpleName();
            detail = detail.replace(key, "[redacted]");
            System.err.println("Adapter " + exchange.getRequestURI().getPath() + ": " + detail);
            for (int i = 0; i < Math.min(6, error.getStackTrace().length); i++)
                System.err.println("  at " + error.getStackTrace()[i]);
            var failure = JSON.createObjectNode();
            failure.putArray("errors").addObject().put("message", "Local STM adapter request failed: " + detail);
            send(exchange, 502, "application/json",
                JSON.writeValueAsBytes(failure));
        } finally { exchange.close(); }
    }

    void route(HttpExchange exchange) throws Exception {
        byte[] input = exchange.getRequestBody().readNBytes(262145);
        if (input.length > 262144) throw new IOException("Request too large");
        ObjectNode body = (ObjectNode) JSON.readTree(input);
        // Catalogue queries have no journey estimates or alerts to filter for freshness.
        if (!body.path("query").asText().contains("planConnection")) {
            body.remove("extensions");
            var metadata = http.send(otpRequest(body), HttpResponse.BodyHandlers.ofByteArray());
            var result = otpResponse(metadata.statusCode(), metadata.body());
            send(exchange, metadata.statusCode(), "application/json", JSON.writeValueAsBytes(result));
            return;
        }
        // Planning queries declare this variable so a stale feed can force scheduled routing.
        Set<String> feeds = new HashSet<>();
        for (JsonNode feed : body.path("extensions").path("realtimeFeeds")) feeds.add(feed.asText());
        if (feeds.isEmpty()) feeds.add("STM"); // Legacy STM debug requests.
        body.remove("extensions");
        routingRequest(body, feeds);
        ObjectNode variables = body.with("variables");
        boolean exclude = variables.path("excludeRealtime").asBoolean();
        var request = otpRequest(body);
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        ObjectNode result = otpResponse(response.statusCode(), response.body());
        // If a source became stale during routing, retry once with scheduled times.
        for (JsonNode edge : result.path("data").path("planConnection").path("edges")) {
            for (JsonNode leg : edge.path("node").path("legs")) {
                if (leg.path("route").isObject()) feeds.add(legFeed(leg));
            }
        }
        if (!exclude && !predictionsUsable(feeds)) {
            variables.put("excludeRealtime", true);
            var retry = otpRequest(body);
            response = http.send(retry, HttpResponse.BodyHandlers.ofByteArray());
            result = otpResponse(response.statusCode(), response.body());
        }
        ObjectNode health = healthByFeed();
        mapJourneyAlerts(result, leg -> health.path(legFeed(leg)).path("alerts").path("state").asText().equals("fresh"));
        ObjectNode extensions = result.with("extensions");
        extensions.set("stmRealtime", health.path("STM"));
        extensions.set("realtimeByFeed", health);
        send(exchange, response.statusCode(), "application/json", JSON.writeValueAsBytes(result));
    }

    static String legFeed(JsonNode leg) {
        String id = leg.path("route").path("gtfsId").asText();
        int separator = id.indexOf(':');
        if (separator > 0) return id.substring(0, separator);
        return leg.path("mode").asText().equals("RAIL") ? ExoRealtimeFeed.ID : "STM";
    }

    /** OTP attaches route+direction alerts to Pattern rather than Leg. Keep that exact scope. */
    static void mapJourneyAlerts(ObjectNode result, boolean usable) {
        mapJourneyAlerts(result, leg -> usable);
    }

    static void mapJourneyAlerts(ObjectNode result, Predicate<JsonNode> isUsable) {
        for (JsonNode edge : result.path("data").path("planConnection").path("edges")) {
            for (JsonNode value : edge.path("node").path("legs")) {
                if (!(value instanceof ObjectNode leg)) continue;
                boolean usable = isUsable.test(leg);
                var matches = new LinkedHashMap<String, JsonNode>();
                JsonNode pattern = leg.path("trip").path("pattern");
                if (usable) {
                    for (JsonNode candidates : List.of(leg.path("alerts"), pattern.path("alerts"))) {
                        for (JsonNode alert : candidates) {
                            if (alertActiveDuringLeg(alert, leg)) {
                                String identity = alert.hasNonNull("alertHash") ? alert.path("alertHash").asText()
                                    : alert.path("alertHeaderText").asText() + "\n" + alert.path("alertDescriptionText").asText();
                                matches.putIfAbsent(identity, alert);
                            }
                        }
                    }
                }
                var alerts = leg.putArray("alerts");
                matches.values().forEach(alerts::add);
                if (!usable && pattern instanceof ObjectNode object) object.putArray("alerts");
            }
        }
    }

    static boolean alertActiveDuringLeg(JsonNode alert, JsonNode leg) {
        JsonNode periods = alert.path("activityPeriods");
        if (!periods.isArray()) return false; // Validity must be requested, never guessed.
        if (periods.isEmpty()) return true; // GTFS-RT: no periods means always active.
        try {
            var start = OffsetDateTime.parse(leg.path("start").path("scheduledTime").asText()).toInstant();
            var end = OffsetDateTime.parse(leg.path("end").path("scheduledTime").asText()).toInstant();
            for (JsonNode period : periods) {
                boolean beginsBeforeEnd = !period.hasNonNull("start") ||
                    OffsetDateTime.parse(period.path("start").asText()).toInstant().isBefore(end);
                boolean endsAfterStart = !period.hasNonNull("end") ||
                    OffsetDateTime.parse(period.path("end").asText()).toInstant().isAfter(start);
                if (beginsBeforeEnd && endsAfterStart) return true;
            }
        } catch (java.time.DateTimeException ignored) { }
        return false;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 18086;
        int otpPort = args.length > 2 ? Integer.parseInt(args[2]) : 18085;
        boolean testing = args.length > 3 && args[3].equals("--test-controls");
        JsonNode config = JSON.readTree(root.resolve("stm-upstream.json").toFile());
        String key = null;
        for (JsonNode updater : config.path("updaters")) {
            if (updater.path("feedId").asText().equals("STM") && updater.path("headers").has("apiKey"))
                key = updater.path("headers").path("apiKey").asText();
        }
        if (key == null || key.isBlank()) throw new IOException("No saved STM apiKey in router-config.json");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        Fetcher fetcher = (url, apiKey) -> {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("apiKey", apiKey).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("STM HTTP " + response.statusCode());
            return response.body();
        };
        var adapter = new StmRealtimeAdapter(root, Clock.systemUTC(), fetcher,
            JSON.readTree(root.resolve("stm-adapter-index.json").toFile()), key,
            URI.create("http://127.0.0.1:" + otpPort + "/otp/gtfs/v1"), testing);
        adapter.exo = ExoRealtimeFeed.load(root, adapter.clock, client);
        adapter.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).proxy(HttpClient.Builder.NO_PROXY).build();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", adapter::handle);
        server.setExecutor(Executors.newFixedThreadPool(6));
        var polling = Executors.newScheduledThreadPool(adapter.exo.download == null ? 1 : 2);
        polling.scheduleWithFixedDelay(adapter::refresh, 0, 60, TimeUnit.SECONDS);
        if (adapter.exo.download != null) {
            polling.scheduleWithFixedDelay(adapter.exo::refresh, 0, adapter.exo.pollSeconds, TimeUnit.SECONDS);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0); polling.shutdownNow();
        }));
        server.start();
        System.out.println("STM adapter running on 127.0.0.1:" + port + "; test controls=" + testing);
    }
}
