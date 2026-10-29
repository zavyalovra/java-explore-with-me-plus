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
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventPrivateControllerTest {

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

        user = userRepository.save(
                User.builder()
                        .name("Иван")
                        .email("ivan@example.com")
                        .build()
        );

        category = categoryRepository.save(
                Category.builder()
                        .name("Концерты")
                        .build()
        );

        when(statsClient.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyList(),
                eq(true)
        )).thenReturn(List.of());
    }

    @Test
    void shouldReturnUserEvents() throws Exception {
        Event first = createEvent("Первое событие", user);
        Event second = createEvent("Второе событие", user);

        mockMvc.perform(get("/users/{userId}/events", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()));
    }

    @Test
    void shouldApplyExactOffsetPagination() throws Exception {
        createEvent("Первое событие", user);

        Event second = createEvent("Второе событие", user);
        Event third = createEvent("Третье событие", user);

        mockMvc.perform(get("/users/{userId}/events", user.getId())
                        .param("from", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(third.getId()));
    }

    @Test
    void shouldReturnBadRequestForNegativeOffset() throws Exception {
        mockMvc.perform(get("/users/{userId}/events", user.getId())
                        .param("from", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoEvents() throws Exception {
        mockMvc.perform(get("/users/{userId}/events", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldReturnEventByIdWithStatsAndRequests() throws Exception {
        Event event = createEvent("Музыкальный фестиваль", user);

        User participant = userRepository.save(
                User.builder()
                        .name("Алексей")
                        .email("alex@example.com")
                        .build()
        );

        jdbcTemplate.update(
                """
                INSERT INTO participation_requests
                    (event_id, requester_id, status)
                VALUES (?, ?, ?)
                """,
                event.getId(),
                participant.getId(),
                "CONFIRMED"
        );

        when(statsClient.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyList(),
                eq(true)
        )).thenReturn(List.of(
                new ViewStatsDto(
                        "ewm-main-service",
                        "/events/" + event.getId(),
                        7L
                )
        ));

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}",
                        user.getId(),
                        event.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.title")
                        .value("Музыкальный фестиваль"))
                .andExpect(jsonPath("$.initiator.id")
                        .value(user.getId()))
                .andExpect(jsonPath("$.category.id")
                        .value(category.getId()))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.confirmedRequests").value(1))
                .andExpect(jsonPath("$.views").value(7));
    }

    @Test
    void shouldReturnNotFoundForAnotherUsersEvent() throws Exception {
        Event event = createEvent("Чужое событие", user);

        User anotherUser = userRepository.save(
                User.builder()
                        .name("Пётр")
                        .email("petr@example.com")
                        .build()
        );

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}",
                        anotherUser.getId(),
                        event.getId()
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForMissingEvent() throws Exception {
        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}",
                        user.getId(),
                        999999L
                ))
                .andExpect(status().isNotFound());
    }

    private Event createEvent(String title, User initiator) {
        Event event = Event.builder()
                .title(title)
                .annotation("Описание интересного события для участников")
                .description("Подробная информация о предстоящем мероприятии")
                .category(category)
                .initiator(initiator)
                .eventDate(LocalDateTime.now().plusDays(5))
                .location(new Location(55.75, 37.61))
                .build();

        return eventRepository.saveAndFlush(event);
    }

    @Test
    void shouldUpdateEventFields() throws Exception {
        Event event = createEvent("Старое название", user);

        String json = "{\"title\":\"Новое название\","
                + "\"paid\":true,\"participantLimit\":25}";

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}",
                        user.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title")
                        .value("Новое название"))
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.participantLimit").value(25))
                .andExpect(jsonPath("$.state").value("PENDING"));

        Event updated = eventRepository.findById(event.getId())
                .orElseThrow();

        assertEquals("Новое название", updated.getTitle());
        assertEquals(25, updated.getParticipantLimit());
        assertTrue(updated.isPaid());
    }

    @Test
    void shouldRejectUpdateOfPublishedEvent() throws Exception {
        Event event = createEvent("Опубликованное событие", user);

        event.setState(EventState.PUBLISHED);
        eventRepository.saveAndFlush(event);

        String json = "{\"title\":\"Попытка изменения\"}";

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}",
                        user.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldSendCanceledEventToReview() throws Exception {
        Event event = createEvent("Отменённое событие", user);

        event.setState(EventState.CANCELED);
        eventRepository.saveAndFlush(event);

        String json = "{\"stateAction\":\"SEND_TO_REVIEW\"}";

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}",
                        user.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));

        Event updated = eventRepository.findById(event.getId())
                .orElseThrow();

        assertEquals(EventState.PENDING, updated.getState());
    }

    @Test
    void shouldRejectEventDateLessThanTwoHours() throws Exception {
        Event event = createEvent("Событие с датой", user);

        String invalidDate = LocalDateTime.now()
                .plusMinutes(30)
                .format(DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd HH:mm:ss"
                ));

        String json = "{\"eventDate\":\"%s\"}"
                .formatted(invalidDate);

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}",
                        user.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }
}