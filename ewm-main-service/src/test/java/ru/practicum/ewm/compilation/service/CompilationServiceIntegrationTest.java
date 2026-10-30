package ru.practicum.ewm.compilation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.model.CompilationEvent;
import ru.practicum.ewm.compilation.repository.CompilationEventRepository;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.stats.client.StatsClient;

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

    @MockBean
    private StatsClient statsClient;

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

        assertNotNull(created.getId());
        assertEquals("С событиями", created.getTitle());
        assertTrue(created.getPinned());
        assertEquals(List.of(1L, 2L), created.getEvents().stream().map(EventShortDto::getId).toList());
        assertEquals("Событие 1", created.getEvents().get(0).getTitle());

        List<CompilationEvent> links = compilationEventRepository.findByIdCompilationId(created.getId());
        assertEquals(2, links.size());
        assertEquals(Set.of(1L, 2L), Set.copyOf(links.stream()
                .map(link -> link.getId().getEventId())
                .toList()));
    }

    @Test
    void shouldReturnEventsInGetById() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L)));

        CompilationDto found = compilationService.getById(created.getId());

        assertEquals(1, found.getEvents().size());
        assertEquals(1L, found.getEvents().get(0).getId());
    }

    @Test
    void shouldReturnEventsInGetAll() {
        compilationService.create(new NewCompilationDto("Первая", true, Set.of(1L, 2L)));
        compilationService.create(new NewCompilationDto("Вторая", false, null));

        List<CompilationDto> compilations = compilationService.getAll(null, 0, 10);

        assertEquals(2, compilations.size());
        assertEquals(2, compilations.get(0).getEvents().size());
        assertTrue(compilations.get(1).getEvents().isEmpty());
    }

    @Test
    void shouldReplaceEventsOnUpdate() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L, 2L)));

        CompilationDto updated = compilationService.update(created.getId(),
                new UpdateCompilationRequest(null, null, Set.of(2L)));

        assertEquals(List.of(2L), updated.getEvents().stream().map(EventShortDto::getId).toList());

        List<CompilationEvent> links = compilationEventRepository.findByIdCompilationId(created.getId());
        assertEquals(1, links.size());
        assertEquals(2L, links.get(0).getId().getEventId());
    }

    @Test
    void shouldClearEventsWhenEmptySetPassed() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L)));

        CompilationDto updated = compilationService.update(created.getId(),
                new UpdateCompilationRequest(null, null, Set.of()));

        assertTrue(updated.getEvents().isEmpty());
        assertTrue(compilationEventRepository.findByIdCompilationId(created.getId()).isEmpty());
    }

    @Test
    void shouldKeepEventsWhenUpdateDoesNotContainThem() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L)));

        CompilationDto updated = compilationService.update(created.getId(),
                new UpdateCompilationRequest("Новое имя", true, null));

        assertEquals("Новое имя", updated.getTitle());
        assertEquals(1, updated.getEvents().size());
        assertEquals(1, compilationEventRepository.findByIdCompilationId(created.getId()).size());
    }

    @Test
    void shouldThrowNotFoundForMissingEventOnCreate() {
        NewCompilationDto dto = new NewCompilationDto("С фантомом", false, Set.of(1L, 999L));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> compilationService.create(dto));

        assertEquals("Event with id=999 was not found", exception.getMessage());
        assertTrue(compilationRepository.findAll().isEmpty());
    }

    @Test
    void shouldThrowNotFoundForMissingEventOnUpdate() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Подборка", false, Set.of(1L)));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> compilationService.update(created.getId(),
                        new UpdateCompilationRequest(null, null, Set.of(999L))));

        assertEquals("Event with id=999 was not found", exception.getMessage());
    }

    @Test
    void shouldDeleteCompilationWithEvents() {
        CompilationDto created = compilationService.create(
                new NewCompilationDto("Удаляемая", false, Set.of(1L, 2L)));

        compilationService.delete(created.getId());

        assertTrue(compilationRepository.findById(created.getId()).isEmpty());
        assertTrue(compilationEventRepository.findByIdCompilationId(created.getId()).isEmpty());
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
