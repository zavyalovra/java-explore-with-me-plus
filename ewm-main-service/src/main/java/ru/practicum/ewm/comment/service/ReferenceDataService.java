package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Временный источник данных о событиях.
 * Домена событий пока нет, поэтому читаем напрямую из таблицы events.
 * После появления EventRepository заменить вызов на него и удалить класс.
 * Пользователи уже переведены на UserRepository.
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
}
