package ru.practicum.ewm.request.controller;

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
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParticipationRequestControllerTest {

    @Autowired
    private ParticipationRequestRepository requestRepository;

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

    private User initiator;
    private User participant;
    private Category category;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM compilation_events");
        jdbcTemplate.update("DELETE FROM participation_requests");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");

        initiator = userRepository.save(
                User.builder()
                        .name("Иван")
                        .email("ivan@example.com")
                        .build()
        );

        participant = userRepository.save(
                User.builder()
                        .name("Алексей")
                        .email("alex@example.com")
                        .build()
        );

        category = categoryRepository.save(
                Category.builder()
                        .name("Концерты")
                        .build()
        );
    }

    @Test
    void shouldCreatePendingRequest() throws Exception {
        Event event = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.event").value(event.getId()))
                .andExpect(jsonPath("$.requester")
                        .value(participant.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.created").exists());
    }

    @Test
    void shouldAutoConfirmWithoutModeration() throws Exception {
        Event event = createEvent(5, false);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldAutoConfirmWhenLimitIsZero() throws Exception {
        Event event = createEvent(0, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldRejectDuplicateRequest() throws Exception {
        Event event = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectRequestFromInitiator() throws Exception {
        Event event = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        initiator.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectUnpublishedEvent() throws Exception {
        Event event = createEvent(5, true);

        event.setState(EventState.PENDING);
        eventRepository.saveAndFlush(event);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectWhenParticipantLimitReached() throws Exception {
        Event event = createEvent(1, false);

        User anotherParticipant = userRepository.save(
                User.builder()
                        .name("Пётр")
                        .email("petr@example.com")
                        .build()
        );

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/users/{userId}/requests",
                        anotherParticipant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectMissingEventId() throws Exception {
        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId()))
                .andExpect(status().isBadRequest());
    }

    private Event createEvent(
            int participantLimit,
            boolean requestModeration
    ) {
        return eventRepository.saveAndFlush(
                Event.builder()
                        .title("Музыкальный фестиваль")
                        .annotation("Большой фестиваль под открытым небом")
                        .description("Подробное описание музыкального фестиваля")
                        .initiator(initiator)
                        .category(category)
                        .eventDate(LocalDateTime.now().plusDays(5))
                        .location(new Location(55.75, 37.61))
                        .state(EventState.PUBLISHED)
                        .participantLimit(participantLimit)
                        .requestModeration(requestModeration)
                        .build()
        );
    }

    @Test
    void shouldReturnOnlyOwnRequests() throws Exception {
        Event event = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());

        User anotherUser = userRepository.save(
                User.builder()
                        .name("Пётр")
                        .email("petr@example.com")
                        .build()
        );

        mockMvc.perform(post("/users/{userId}/requests",
                        anotherUser.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/users/{userId}/requests",
                        participant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].requester")
                        .value(participant.getId()))
                .andExpect(jsonPath("$[0].event")
                        .value(event.getId()));
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoRequests() throws Exception {
        mockMvc.perform(get("/users/{userId}/requests",
                        participant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldReturnNotFoundForMissingUser() throws Exception {
        mockMvc.perform(get("/users/{userId}/requests", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCancelConfirmedRequest() throws Exception {
        Event event = createEvent(0, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Long requestId = requestRepository
                .findByRequester_Id(participant.getId())
                .get(0)
                .getId();

        mockMvc.perform(patch(
                        "/users/{userId}/requests/{requestId}/cancel",
                        participant.getId(),
                        requestId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.status").value("CANCELED"));

        RequestStatus savedStatus = requestRepository
                .findById(requestId)
                .orElseThrow()
                .getStatus();

        assertEquals(RequestStatus.CANCELED, savedStatus);

        assertEquals(
                0L,
                requestRepository.countByEvent_IdAndStatus(
                        event.getId(),
                        RequestStatus.CONFIRMED
                )
        );
    }

    @Test
    void shouldNotCancelAnotherUsersRequest() throws Exception {
        Event event = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());

        Long requestId = requestRepository
                .findByRequester_Id(participant.getId())
                .get(0)
                .getId();

        mockMvc.perform(patch(
                        "/users/{userId}/requests/{requestId}/cancel",
                        initiator.getId(),
                        requestId
                ))
                .andExpect(status().isNotFound());

        assertEquals(
                RequestStatus.PENDING,
                requestRepository.findById(requestId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldReturnRequestsForOwnEvent() throws Exception {
        Event event = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        event.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].event")
                        .value(event.getId()))
                .andExpect(jsonPath("$[0].requester")
                        .value(participant.getId()))
                .andExpect(jsonPath("$[0].status")
                        .value("PENDING"));
    }

    @Test
    void shouldReturnEmptyListForEventWithoutRequests()
            throws Exception {

        Event event = createEvent(5, true);

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        event.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldRejectAccessToAnotherUsersEventRequests()
            throws Exception {

        Event event = createEvent(5, true);

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}/requests",
                        participant.getId(),
                        event.getId()
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForMissingEventRequests()
            throws Exception {

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        999999L
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnRequestsOnlyForSelectedEvent()
            throws Exception {

        Event firstEvent = createEvent(5, true);
        Event secondEvent = createEvent(5, true);

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", firstEvent.getId().toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users/{userId}/requests",
                        participant.getId())
                        .param("eventId", secondEvent.getId().toString()))
                .andExpect(status().isCreated());

        mockMvc.perform(get(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        firstEvent.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].event")
                        .value(firstEvent.getId()));
    }

    @Test
    void shouldConfirmPendingRequest() throws Exception {
        Event event = createEvent(2, true);

        Long requestId = createPendingRequest(
                participant.getId(),
                event.getId()
        );

        String json = "{\"requestIds\": [%d], \"status\": \"CONFIRMED\"}"
                .formatted(requestId);

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedRequests", hasSize(1)))
                .andExpect(jsonPath("$.rejectedRequests", hasSize(0)))
                .andExpect(jsonPath("$.confirmedRequests[0].status")
                        .value("CONFIRMED"));

        assertEquals(
                RequestStatus.CONFIRMED,
                requestRepository.findById(requestId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectRemainingRequestsWhenLimitReached()
            throws Exception {

        Event event = createEvent(1, true);

        Long firstRequestId = createPendingRequest(
                participant.getId(),
                event.getId()
        );

        User secondParticipant = userRepository.save(
                User.builder()
                        .name("Пётр")
                        .email("petr@example.com")
                        .build()
        );

        Long secondRequestId = createPendingRequest(
                secondParticipant.getId(),
                event.getId()
        );

        String json = "{\"requestIds\": [%d], \"status\": \"CONFIRMED\"}"
                .formatted(firstRequestId);

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedRequests", hasSize(1)))
                .andExpect(jsonPath("$.rejectedRequests", hasSize(1)))
                .andExpect(jsonPath("$.rejectedRequests[0].id")
                        .value(secondRequestId))
                .andExpect(jsonPath("$.rejectedRequests[0].status")
                        .value("REJECTED"));

        assertEquals(
                RequestStatus.CONFIRMED,
                requestRepository.findById(firstRequestId)
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                RequestStatus.REJECTED,
                requestRepository.findById(secondRequestId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectPendingRequest() throws Exception {
        Event event = createEvent(5, true);

        Long requestId = createPendingRequest(
                participant.getId(),
                event.getId()
        );

        String json = "{\"requestIds\": [%d], \"status\": \"REJECTED\"}"
                .formatted(requestId);

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedRequests", hasSize(0)))
                .andExpect(jsonPath("$.rejectedRequests", hasSize(1)))
                .andExpect(jsonPath("$.rejectedRequests[0].status")
                        .value("REJECTED"));
    }

    @Test
    void shouldRejectConfirmationWhenNotEnoughPlaces()
            throws Exception {

        Event event = createEvent(1, true);

        Long firstId = createPendingRequest(
                participant.getId(),
                event.getId()
        );

        User secondParticipant = userRepository.save(
                User.builder()
                        .name("Александр")
                        .email("alexander@example.com")
                        .build()
        );

        Long secondId = createPendingRequest(
                secondParticipant.getId(),
                event.getId()
        );

        String json = "{\"requestIds\": [%d, %d], \"status\": \"CONFIRMED\"}"
                .formatted(firstId, secondId);

        mockMvc.perform(patch(
                        "/users/{userId}/events/{eventId}/requests",
                        initiator.getId(),
                        event.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());

        assertEquals(
                RequestStatus.PENDING,
                requestRepository.findById(firstId)
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                RequestStatus.PENDING,
                requestRepository.findById(secondId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    private Long createPendingRequest(Long userId, Long eventId) throws Exception {

        mockMvc.perform(post("/users/{userId}/requests", userId)
                        .param("eventId", eventId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        return requestRepository.findByRequester_Id(userId)
                .stream()
                .filter(request -> request.getEvent().getId().equals(eventId))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}