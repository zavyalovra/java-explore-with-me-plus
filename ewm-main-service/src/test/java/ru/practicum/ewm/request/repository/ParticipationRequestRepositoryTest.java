package ru.practicum.ewm.request.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ParticipationRequestRepositoryTest {

    @Autowired
    private ParticipationRequestRepository requestRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldSaveRequestWithPendingStatus() {
        User initiator = userRepository.save(
                User.builder()
                        .name("Иван")
                        .email("ivan@example.com")
                        .build()
        );

        User participant = userRepository.save(
                User.builder()
                        .name("Алексей")
                        .email("alex@example.com")
                        .build()
        );

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Концерты")
                        .build()
        );

        Event event = eventRepository.save(
                Event.builder()
                        .title("Музыкальный фестиваль")
                        .annotation("Большой музыкальный фестиваль")
                        .description("Мероприятие с участием музыкантов")
                        .initiator(initiator)
                        .category(category)
                        .eventDate(LocalDateTime.now().plusDays(5))
                        .location(new Location(55.75, 37.61))
                        .build()
        );

        ParticipationRequest request =
                ParticipationRequest.builder()
                        .event(event)
                        .requester(participant)
                        .build();

        ParticipationRequest saved =
                requestRepository.saveAndFlush(request);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreated());
        assertEquals(RequestStatus.PENDING, saved.getStatus());

        assertEquals(event.getId(), saved.getEvent().getId());
        assertEquals(participant.getId(), saved.getRequester().getId());

        assertTrue(requestRepository.existsByEvent_IdAndRequester_Id(
                event.getId(),
                participant.getId()
        ));

        assertEquals(
                0L,
                requestRepository.countByEvent_IdAndStatus(
                        event.getId(),
                        RequestStatus.CONFIRMED
                )
        );
    }
}