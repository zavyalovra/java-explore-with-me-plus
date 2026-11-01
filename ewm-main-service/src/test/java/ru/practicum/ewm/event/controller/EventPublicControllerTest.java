package ru.practicum.ewm.event.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventPublicControllerTest {

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
    void shouldReturnPublishedEventAndRecordHit() throws Exception {
        Event event = createEvent(EventState.PUBLISHED);

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

        mockMvc.perform(get("/events/{id}", event.getId())
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.views").value(7))
                .andExpect(jsonPath("$.confirmedRequests").value(0));

        ArgumentCaptor<EndpointHitDto> captor =
                ArgumentCaptor.forClass(EndpointHitDto.class);

        verify(statsClient).saveHit(captor.capture());

        EndpointHitDto hit = captor.getValue();

        assertEquals("ewm-main-service", hit.getApp());
        assertEquals("/events/" + event.getId(), hit.getUri());
        assertEquals("192.0.2.10", hit.getIp());
        assertNotNull(hit.getTimestamp());
    }

    @Test
    void shouldTakeClientIpFromXForwardedFor() throws Exception {
        Event event = createEvent(EventState.PUBLISHED);

        mockMvc.perform(get("/events/{id}", event.getId())
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<EndpointHitDto> captor =
                ArgumentCaptor.forClass(EndpointHitDto.class);

        verify(statsClient).saveHit(captor.capture());

        assertEquals("203.0.113.7", captor.getValue().getIp());
    }

    @Test
    void shouldFallBackToRemoteAddrWhenForwardedForHasNoAddress() throws Exception {
        Event event = createEvent(EventState.PUBLISHED);

        mockMvc.perform(get("/events/{id}", event.getId())
                        .header("X-Forwarded-For", " , unknown")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<EndpointHitDto> captor =
                ArgumentCaptor.forClass(EndpointHitDto.class);

        verify(statsClient).saveHit(captor.capture());

        assertEquals("192.0.2.10", captor.getValue().getIp());
    }

    @Test
    void shouldReturnNotFoundForPendingEvent() throws Exception {
        Event event = createEvent(EventState.PENDING);

        mockMvc.perform(get("/events/{id}", event.getId()))
                .andExpect(status().isNotFound());

        verify(statsClient, never()).saveHit(any());
    }

    @Test
    void shouldReturnNotFoundForCanceledEvent() throws Exception {
        Event event = createEvent(EventState.CANCELED);

        mockMvc.perform(get("/events/{id}", event.getId()))
                .andExpect(status().isNotFound());

        verify(statsClient, never()).saveHit(any());
    }

    @Test
    void shouldReturnNotFoundForMissingEvent() throws Exception {
        mockMvc.perform(get("/events/{id}", 999999L))
                .andExpect(status().isNotFound());

        verify(statsClient, never()).saveHit(any());
    }

    @Test
    void shouldFilterPublishedEventsByTextAndPayment() throws Exception {
        Event matching = createEvent(EventState.PUBLISHED);
        matching.setDescription(
                "Большая конференция Java разработчиков"
        );
        matching.setPaid(true);
        eventRepository.saveAndFlush(matching);

        Event pending = createEvent(EventState.PENDING);
        pending.setDescription(
                "Большая конференция Java разработчиков"
        );
        pending.setPaid(true);
        eventRepository.saveAndFlush(pending);

        Event free = createEvent(EventState.PUBLISHED);
        free.setDescription(
                "Большая конференция Java разработчиков"
        );
        free.setPaid(false);
        eventRepository.saveAndFlush(free);

        mockMvc.perform(get("/events")
                        .param("text", "JAVA")
                        .param("paid", "true")
                        .param("categories",
                                String.valueOf(category.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id")
                        .value(matching.getId()));
    }

    @Test
    void shouldReturnOnlyFutureEventsByDefault() throws Exception {
        Event past = createEvent(EventState.PUBLISHED);
        past.setEventDate(LocalDateTime.now().minusDays(1));
        eventRepository.saveAndFlush(past);

        Event future = createEvent(EventState.PUBLISHED);

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id")
                        .value(future.getId()));
    }

    @Test
    void shouldFilterAvailableEventsBeforePagination() throws Exception {
        Event full = createEvent(EventState.PUBLISHED);
        full.setEventDate(LocalDateTime.now().plusDays(2));
        full.setParticipantLimit(1);
        eventRepository.saveAndFlush(full);

        Event available = createEvent(EventState.PUBLISHED);
        available.setEventDate(LocalDateTime.now().plusDays(3));
        available.setParticipantLimit(5);
        eventRepository.saveAndFlush(available);

        User participant = userRepository.save(
                User.builder()
                        .name("Участник")
                        .email("participant@example.com")
                        .build()
        );

        jdbcTemplate.update(
                """
                INSERT INTO participation_requests
                    (event_id, requester_id, status)
                VALUES (?, ?, 'CONFIRMED')
                """,
                full.getId(),
                participant.getId()
        );

        mockMvc.perform(get("/events")
                        .param("onlyAvailable", "true")
                        .param("from", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id")
                        .value(available.getId()));
    }

    @Test
    void shouldSortByViewsBeforePagination() throws Exception {
        Event first = createEvent(EventState.PUBLISHED);
        Event second = createEvent(EventState.PUBLISHED);
        Event third = createEvent(EventState.PUBLISHED);

        when(statsClient.getStats(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyList(),
                eq(true)
        )).thenReturn(List.of(
                new ViewStatsDto(
                        "ewm-main-service",
                        "/events/" + first.getId(),
                        2L
                ),
                new ViewStatsDto(
                        "ewm-main-service",
                        "/events/" + second.getId(),
                        9L
                ),
                new ViewStatsDto(
                        "ewm-main-service",
                        "/events/" + third.getId(),
                        5L
                )
        ));

        mockMvc.perform(get("/events")
                        .param("sort", "VIEWS")
                        .param("from", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id")
                        .value(third.getId()))
                .andExpect(jsonPath("$[0].views").value(5));
    }

    @Test
    void shouldRecordHitForEmptyPublicSearch() throws Exception {
        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        ArgumentCaptor<EndpointHitDto> captor =
                ArgumentCaptor.forClass(EndpointHitDto.class);

        verify(statsClient).saveHit(captor.capture());

        assertEquals("/events", captor.getValue().getUri());
        assertEquals("ewm-main-service",
                captor.getValue().getApp());
    }

    private Event createEvent(EventState state) {
        return eventRepository.saveAndFlush(
                Event.builder()
                        .title("Музыкальный фестиваль")
                        .annotation("Большой фестиваль под открытым небом")
                        .description("Подробная информация о музыкальном фестивале")
                        .initiator(user)
                        .category(category)
                        .eventDate(LocalDateTime.now().plusDays(5))
                        .location(new Location(55.75, 37.61))
                        .state(state)
                        .build()
        );
    }

    @Test
    void shouldRejectInvalidPublicDateRange() throws Exception {

        mockMvc.perform(get("/events")
                        .param("rangeStart", "2026-12-20 12:00:00")
                        .param("rangeEnd", "2026-12-10 12:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnlimitedEventsWhenOnlyAvailableIsTrue()
            throws Exception {

        Event event = createEvent(EventState.PUBLISHED);

        event.setParticipantLimit(0);
        eventRepository.saveAndFlush(event);

        mockMvc.perform(get("/events")
                        .param("onlyAvailable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(event.getId()));
    }
}