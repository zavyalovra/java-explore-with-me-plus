package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventResponseService {

    private static final String APP_NAME = "ewm-main-service";
    private static final LocalDateTime STATS_START = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final EventMapper eventMapper;
    private final StatsClient statsClient;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EventFullDto toFullDto(Event event) {
        List<Event> events = List.of(event);

        Map<Long, Long> confirmed = getConfirmedRequests(events);
        Map<Long, Long> views = getViews(events);

        return eventMapper.toFullDto(
                event,
                confirmed.getOrDefault(event.getId(), 0L),
                views.getOrDefault(event.getId(), 0L)
        );
    }

    public List<EventShortDto> toShortDtos(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> confirmed = getConfirmedRequests(events);
        Map<Long, Long> views = getViews(events);

        return events.stream()
                .map(event -> eventMapper.toShortDto(
                        event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }

    private Map<Long, Long> getConfirmedRequests(List<Event> events) {
        List<Long> ids = events.stream()
                .map(Event::getId)
                .toList();

        String sql = """
                SELECT event_id, COUNT(*) AS confirmed_count
                FROM participation_requests
                WHERE status = 'CONFIRMED'
                  AND event_id IN (:ids)
                GROUP BY event_id
                """;

        Map<Long, Long> result = new HashMap<>();

        jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    result.put(
                            rs.getLong("event_id"),
                            rs.getLong("confirmed_count")
                    );
                }
        );

        return result;
    }

    private Map<Long, Long> getViews(List<Event> events) {
        Map<String, Long> uriToId = events.stream()
                .collect(Collectors.toMap(
                        event -> "/events/" + event.getId(),
                        Event::getId
                ));

        List<ViewStatsDto> stats = statsClient.getStats(
                STATS_START,
                LocalDateTime.now(),
                List.copyOf(uriToId.keySet()),
                true
        );

        Map<Long, Long> views = new HashMap<>();

        for (ViewStatsDto stat : stats) {
            if (!APP_NAME.equals(stat.getApp())) {
                continue;
            }

            Long eventId = uriToId.get(stat.getUri());

            if (eventId != null) {
                views.merge(eventId, stat.getHits(), Long::sum);
            }
        }

        return views;
    }

    public List<EventFullDto> toFullDtos(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> confirmed = getConfirmedRequests(events);
        Map<Long, Long> views = getViews(events);

        return events.stream()
                .map(event -> eventMapper.toFullDto(
                        event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)
                ))
                .toList();
    }
}