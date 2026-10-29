package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Временный источник данных о событиях и пользователях.
 * Доменов событий и пользователей пока нет, поэтому читаем напрямую из таблиц.
 * После появления EventRepository и UserRepository заменить вызовы на них.
 */
@Component
@RequiredArgsConstructor
public class ReferenceDataService {

    private final JdbcTemplate jdbcTemplate;

    public Optional<String> findEventState(Long eventId) {
        List<String> states = jdbcTemplate.queryForList(
                "select state from events where id = ?", String.class, eventId);
        return states.stream().findFirst();
    }

    public Optional<String> findUserName(Long userId) {
        List<String> names = jdbcTemplate.queryForList(
                "select name from users where id = ?", String.class, userId);
        return names.stream().findFirst();
    }

    public Map<Long, String> findUserNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = userIds.stream()
                .map(userId -> "?")
                .collect(Collectors.joining(", "));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, name from users where id in (" + placeholders + ")", userIds.toArray());

        Map<Long, String> names = new HashMap<>();
        for (Map<String, Object> row : rows) {
            names.put(((Number) row.get("id")).longValue(), (String) row.get("name"));
        }
        return names;
    }
}
