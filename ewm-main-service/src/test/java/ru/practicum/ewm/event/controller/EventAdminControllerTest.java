package ru.practicum.ewm.event.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StatsClient statsClient;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM compilation_events");
        jdbcTemplate.update("DELETE FROM participation_requests");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");

        user = userRepository.save(User.builder()
                .name("Иван")
                .email("ivan@example.com")
                .build());

        category = categoryRepository.save(Category.builder()
                .name("Концерты")
                .build());

        when(statsClient.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyList(),
                eq(true)
        )).thenReturn(List.of());
    }

    @Test
    void shouldPublishPendingEvent() throws Exception {
        Event event = createEvent();

        mockMvc.perform(patch("/admin/events/{eventId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedOn").isNotEmpty());

        Event updated = eventRepository.findById(event.getId())
                .orElseThrow();

        assertEquals(EventState.PUBLISHED, updated.getState());
        assertNotNull(updated.getPublishedOn());
    }

    @Test
    void shouldRejectPendingEvent() throws Exception {
        Event event = createEvent();

        mockMvc.perform(patch("/admin/events/{eventId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"REJECT_EVENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELED"));

        Event updated = eventRepository.findById(event.getId())
                .orElseThrow();

        assertEquals(EventState.CANCELED, updated.getState());
        assertNull(updated.getPublishedOn());
    }

    @Test
    void shouldRejectPublishingCanceledEvent() throws Exception {
        Event event = createEvent();
        event.setState(EventState.CANCELED);
        eventRepository.saveAndFlush(event);

        mockMvc.perform(patch("/admin/events/{eventId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectCancelingPublishedEvent() throws Exception {
        Event event = createEvent();
        event.setState(EventState.PUBLISHED);
        eventRepository.saveAndFlush(event);

        mockMvc.perform(patch("/admin/events/{eventId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"REJECT_EVENT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldUpdateEventWithoutChangingState() throws Exception {
        Event event = createEvent();

        mockMvc.perform(patch("/admin/events/{eventId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новое название\",\"paid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Новое название"))
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.state").value("PENDING"));

        Event updated = eventRepository.findById(event.getId())
                .orElseThrow();

        assertEquals("Новое название", updated.getTitle());
        assertTrue(updated.isPaid());
    }

    @Test
    void shouldReturnNotFoundForMissingEvent() throws Exception {
        mockMvc.perform(patch("/admin/events/{eventId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isNotFound());
    }

    private Event createEvent() {
        return eventRepository.saveAndFlush(
                Event.builder()
                        .title("Музыкальный фестиваль")
                        .annotation("Большой фестиваль под открытым небом")
                        .description("Подробное описание музыкального фестиваля")
                        .initiator(user)
                        .category(category)
                        .eventDate(LocalDateTime.now().plusDays(5))
                        .location(new Location(55.75, 37.61))
                        .build()
        );
    }

    @Test
    void shouldFilterEventsByState() throws Exception {
        Event published = createEvent();
        published.setState(EventState.PUBLISHED);
        eventRepository.saveAndFlush(published);

        createEvent();

        mockMvc.perform(get("/admin/events")
                        .param("states", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(published.getId()))
                .andExpect(jsonPath("$[0].state")
                        .value("PUBLISHED"));
    }

    @Test
    void shouldApplyExactOffsetInAdminSearch() throws Exception {
        createEvent();
        Event second = createEvent();
        createEvent();

        mockMvc.perform(get("/admin/events")
                        .param("from", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(second.getId()));
    }

    @Test
    void shouldRejectInvalidDateRange() throws Exception {
        mockMvc.perform(get("/admin/events")
                        .param("rangeStart", "2026-12-20 12:00:00")
                        .param("rangeEnd", "2026-12-10 12:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEmptyListWhenNoEventsMatch() throws Exception {
        createEvent();

        mockMvc.perform(get("/admin/events")
                        .param("states", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRejectPublishingEventLessThanOneHourBeforeStart()
            throws Exception {

        Event event = createEvent();
        event.setEventDate(LocalDateTime.now().plusMinutes(30));
        eventRepository.saveAndFlush(event);

        mockMvc.perform(patch("/admin/events/{eventId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateAction\":\"PUBLISH_EVENT\"}"))
                .andExpect(status().isBadRequest());

        Event saved = eventRepository.findById(event.getId())
                .orElseThrow();

        assertEquals(EventState.PENDING, saved.getState());
        assertNull(saved.getPublishedOn());
    }
}