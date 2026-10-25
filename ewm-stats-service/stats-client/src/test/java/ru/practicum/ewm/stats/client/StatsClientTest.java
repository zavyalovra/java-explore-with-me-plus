package ru.practicum.ewm.stats.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StatsClientTest {

    private HttpServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // -------- POST /hit --------

    @Test
    void saveHitShouldSendCorrectRequest() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();

        server.createContext("/hit", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            contentType.set(
                    exchange.getRequestHeaders().getFirst("Content-Type")
            );
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));

            respond(exchange, 201, "");
        });

        StatsClient client = startClient();

        EndpointHitDto hit = createHit();

        assertDoesNotThrow(() -> client.saveHit(hit));

        assertEquals("POST", requestMethod.get());
        assertEquals("application/json", contentType.get());

        JsonNode json = objectMapper.readTree(requestBody.get());

        assertEquals("ewm-main-service", json.get("app").asText());
        assertEquals("/events/1", json.get("uri").asText());
        assertEquals("192.168.1.10", json.get("ip").asText());
        assertEquals(
                "2026-10-24 18:00:00",
                json.get("timestamp").asText()
        );
    }

    @Test
    void saveHitShouldThrowExceptionWhenServerReturns500() throws Exception {
        server.createContext("/hit", exchange ->
                respond(exchange, 500, "")
        );

        StatsClient client = startClient();

        StatsClientException exception = assertThrows(
                StatsClientException.class,
                () -> client.saveHit(createHit())
        );

        assertTrue(exception.getMessage().contains("500"));
    }

    // -------- GET /stats --------

    @Test
    void getStatsShouldReturnCorrectStatistics() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> rawQuery = new AtomicReference<>();

        String responseJson = "["
                + "{\"app\":\"ewm-main-service\",\"uri\":\"/events/1\",\"hits\":10},"
                + "{\"app\":\"ewm-main-service\",\"uri\":\"/events/2\",\"hits\":5}"
                + "]";

        server.createContext("/stats", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            rawQuery.set(exchange.getRequestURI().getRawQuery());

            respond(exchange, 200, responseJson);
        });

        StatsClient client = startClient();

        List<ViewStatsDto> stats = client.getStats(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 24, 18, 0),
                List.of("/events/1", "/events/2"),
                true
        );

        assertEquals("GET", requestMethod.get());

        assertEquals(
                "start=2026-10-01+00%3A00%3A00"
                        + "&end=2026-10-24+18%3A00%3A00"
                        + "&uris=%2Fevents%2F1"
                        + "&uris=%2Fevents%2F2"
                        + "&unique=true",
                rawQuery.get()
        );

        assertEquals(2, stats.size());

        assertEquals("ewm-main-service", stats.get(0).getApp());
        assertEquals("/events/1", stats.get(0).getUri());
        assertEquals(10L, stats.get(0).getHits());

        assertEquals("ewm-main-service", stats.get(1).getApp());
        assertEquals("/events/2", stats.get(1).getUri());
        assertEquals(5L, stats.get(1).getHits());
    }

    @Test
    void getStatsShouldWorkWithoutUris() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();

        server.createContext("/stats", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "[]");
        });

        StatsClient client = startClient();

        List<ViewStatsDto> stats = client.getStats(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 24, 18, 0),
                null,
                false
        );

        assertNotNull(stats);
        assertTrue(stats.isEmpty());

        assertFalse(rawQuery.get().contains("uris="));
        assertTrue(rawQuery.get().contains("unique=false"));
    }

    @Test
    void getStatsShouldThrowExceptionWhenServerReturns404() throws Exception {
        server.createContext("/stats", exchange ->
                respond(exchange, 404, "")
        );

        StatsClient client = startClient();

        StatsClientException exception = assertThrows(
                StatsClientException.class,
                () -> client.getStats(
                        LocalDateTime.of(2026, 10, 1, 0, 0),
                        LocalDateTime.of(2026, 10, 24, 18, 0),
                        List.of("/events/1"),
                        false
                )
        );

        assertTrue(exception.getMessage().contains("404"));
    }

    @Test
    void getStatsShouldThrowExceptionWhenResponseIsInvalidJson() throws Exception {
        server.createContext("/stats", exchange ->
                respond(exchange, 200, "invalid-json")
        );

        StatsClient client = startClient();

        StatsClientException exception = assertThrows(
                StatsClientException.class,
                () -> client.getStats(
                        LocalDateTime.of(2026, 10, 1, 0, 0),
                        LocalDateTime.of(2026, 10, 24, 18, 0),
                        null,
                        false
                )
        );

        assertTrue(
                exception.getMessage().contains("deserialize")
        );
    }

    // -------- Validation --------

    @Test
    void getStatsShouldRejectInvalidDateRange() {
        StatsClient client = createClient();

        LocalDateTime start = LocalDateTime.of(2026, 10, 24, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.getStats(start, end, null, false)
        );
    }

    @Test
    void getStatsShouldRejectNullOrBlankUris() {
        StatsClient client = createClient();

        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 24, 18, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.getStats(start, end, List.of(""), false)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> client.getStats(start, end, List.of("   "), false)
        );
    }

    @Test
    void getStatsShouldRejectNullDates() {
        StatsClient client = createClient();

        LocalDateTime date = LocalDateTime.of(2026, 10, 24, 18, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.getStats(null, date, null, false)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> client.getStats(date, null, null, false)
        );
    }

    // -------- Helpers --------

    private StatsClient createClient() {
        String serverUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();

        return new StatsClient(serverUrl, objectMapper);
    }

    private StatsClient startClient() {
        server.start();
        return createClient();
    }

    private EndpointHitDto createHit() {
        return EndpointHitDto.builder()
                .app("ewm-main-service")
                .uri("/events/1")
                .ip("192.168.1.10")
                .timestamp(LocalDateTime.of(2026, 10, 24, 18, 0))
                .build();
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders()
                    .set("Content-Type", "application/json");

            exchange.sendResponseHeaders(
                    status,
                    bytes.length == 0 ? -1 : bytes.length
            );

            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
        } finally {
            exchange.close();
        }
    }
}
