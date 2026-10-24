package ru.practicum.ewm.stats.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Component
public class StatsClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StatsClient(@Value("${stats-server.url}") String serverUrl, ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.objectMapper = objectMapper;
        this.baseUri = URI.create(serverUrl);
    }

    public void saveHit(EndpointHitDto hitDto) {
        try {
            String json = objectMapper.writeValueAsString(hitDto);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(baseUri.resolve("/hit"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            if (response.statusCode() != 201) {
                throw new StatsClientException("Failed to save hit. HTTP status: " + response.statusCode());
            }

        } catch (JsonProcessingException e) {
            throw new StatsClientException("Failed to serialize hit", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StatsClientException("Stats request interrupted", e);

        } catch (IOException e) {
            throw new StatsClientException("Failed to communicate with stats server", e);
        }
    }


    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        Objects.requireNonNull(start, "Start date is required");
        Objects.requireNonNull(end, "End date is required");

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }

        StringBuilder query = new StringBuilder()
                .append("start=")
                .append(encode(start.format(FORMATTER)))
                .append("&end=")
                .append(encode(end.format(FORMATTER)));

        if (uris != null) {
            for (String uri : uris) {
                if (uri == null || uri.isBlank()) {
                    throw new IllegalArgumentException("URI must not be null or blank");
                }

                query.append("&uris=").append(encode(uri));
            }
        }

        query.append("&unique=").append(unique);

        URI uri = URI.create(baseUri.resolve("/stats") + "?" + query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new StatsClientException("Failed to get stats. HTTP status: " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), new TypeReference<List<ViewStatsDto>>() {});

        } catch (JsonProcessingException e) {
            throw new StatsClientException("Failed to deserialize stats response", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StatsClientException("Stats request interrupted", e);

        } catch (IOException e) {
            throw new StatsClientException("Failed to communicate with stats server", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}