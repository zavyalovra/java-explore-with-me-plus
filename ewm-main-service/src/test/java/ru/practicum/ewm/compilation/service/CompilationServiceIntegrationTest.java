package ru.practicum.ewm.compilation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.model.CompilationEvent;
import ru.practicum.ewm.compilation.repository.CompilationEventRepository;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CompilationServiceIntegrationTest {

    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 1L;

    @Autowired
    private CompilationService compilationService;

    @Autowired
    private CompilationRepository compilationRepository;

    @Autowired
    private CompilationEventRepository compilationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        compilationEventRepository.deleteAll();
        compilationRepository.deleteAll();
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from categories");
        jdbcTemplate.update("delete from users");

        jdbcTemplate.update("insert into users (id, name, email) values (?, ?, ?)",
                USER_ID, "Иван", "ivan@mail.ru");
        jdbcTemplate.update("insert into categories (id, name) values (?, ?)",
                CATEGORY_ID, "Концерты");
        insertEvent(1L);
        insertEvent(2L);
    }

    @Test
    void shouldSaveCompilationWithEvents() {
        NewCompilationDto dto = new NewCompilationDto("С событиями", true, Set.of(1L, 2L));

        CompilationDto created = compilationService.create(dto);

        assertNotNull(created.id());
        assertEquals("С событиями", created.title());
        assertTrue(created.pinned());
        assertTrue(created.events().isEmpty());

        List<CompilationEvent> links = compilationEventRepository.findByIdCompilationId(created.id());
        assertEquals(2, links.size());
        assertEquals(Set.of(1L, 2L), Set.copyOf(links.stream()
                .map(link -> link.getId().getEventId())
                .toList()));
    }

    @Test
    void shouldReplaceEventsOnUpdate() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L, 2L)));

        compilationService.update(created.id(), new UpdateCompilationRequest(null, null, Set.of(2L)));

        List<CompilationEvent> links = compilationEventRepository.findByIdCompilationId(created.id());
        assertEquals(1, links.size());
        assertEquals(2L, links.getFirst().getId().getEventId());
    }

    @Test
    void shouldClearEventsWhenEmptySetPassed() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L)));

        compilationService.update(created.id(), new UpdateCompilationRequest(null, null, Set.of()));

        assertTrue(compilationEventRepository.findByIdCompilationId(created.id()).isEmpty());
    }

    @Test
    void shouldKeepEventsWhenUpdateDoesNotContainThem() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L)));

        compilationService.update(created.id(), new UpdateCompilationRequest("Новое имя", true, null));

        assertEquals(1, compilationEventRepository.findByIdCompilationId(created.id()).size());
        assertEquals("Новое имя", compilationService.getById(created.id()).title());
    }

    @Test
    void shouldDeleteCompilationWithEvents() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Удаляемая", false, Set.of(1L, 2L)));

        compilationService.delete(created.id());

        assertTrue(compilationRepository.findById(created.id()).isEmpty());
        assertTrue(compilationEventRepository.findByIdCompilationId(created.id()).isEmpty());
    }

    @Test
    void shouldThrowConflictForDuplicateTitle() {
        compilationService.create(new NewCompilationDto("Одинаковая", false, null));

        assertThrows(ConflictException.class,
                () -> compilationService.create(new NewCompilationDto("Одинаковая", true, null)));
    }

    @Test
    void shouldThrowNotFoundForMissingCompilation() {
        assertThrows(NotFoundException.class, () -> compilationService.getById(999L));
        assertThrows(NotFoundException.class,
                () -> compilationService.update(999L, new UpdateCompilationRequest("Имя", null, null)));
        assertThrows(NotFoundException.class, () -> compilationService.delete(999L));
    }

    private void insertEvent(long eventId) {
        jdbcTemplate.update("insert into events (id, title, annotation, description, category_id, "
                        + "initiator_id, event_date, lat, lon) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, "Событие " + eventId, "Аннотация события", "Описание события",
                CATEGORY_ID, USER_ID, Timestamp.valueOf(LocalDateTime.now().plusDays(1)), 55.75, 37.61);
    }
}
