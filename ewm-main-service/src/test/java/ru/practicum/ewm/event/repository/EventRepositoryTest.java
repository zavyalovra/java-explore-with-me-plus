package ru.practicum.ewm.event.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldSaveEventWithDefaultValues() {
        User user = userRepository.save(
                User.builder()
                        .name("Иван")
                        .email("ivan@example.com")
                        .build()
        );

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Концерты")
                        .build()
        );

        Event event = Event.builder()
                .title("Музыкальный фестиваль")
                .annotation("Большой музыкальный фестиваль на открытом воздухе")
                .description("Музыкальный фестиваль с участием известных исполнителей")
                .initiator(user)
                .category(category)
                .eventDate(LocalDateTime.now().plusDays(3))
                .location(new Location(55.75, 37.61))
                .build();

        Event saved = eventRepository.saveAndFlush(event);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedOn());

        assertEquals(EventState.PENDING, saved.getState());
        assertEquals(0, saved.getParticipantLimit());

        assertFalse(saved.isPaid());
        assertTrue(saved.isRequestModeration());

        assertEquals(user.getId(), saved.getInitiator().getId());
        assertEquals(category.getId(), saved.getCategory().getId());

        assertEquals(55.75, saved.getLocation().getLat());
        assertEquals(37.61, saved.getLocation().getLon());
    }

    @Test
    void shouldFindEventByInitiatorAndState() {
        User user = userRepository.save(
                User.builder()
                        .name("Алексей")
                        .email("alex@example.com")
                        .build()
        );

        Category category = categoryRepository.save(
                Category.builder()
                        .name("Выставки")
                        .build()
        );

        Event event = eventRepository.saveAndFlush(
                Event.builder()
                        .title("Художественная выставка")
                        .annotation("Большая выставка современного искусства")
                        .description("Выставка работ современных художников")
                        .initiator(user)
                        .category(category)
                        .eventDate(LocalDateTime.now().plusDays(5))
                        .location(new Location(55.75, 37.61))
                        .build()
        );

        assertTrue(eventRepository
                .findByIdAndInitiator_Id(event.getId(), user.getId())
                .isPresent());

        assertTrue(eventRepository
                .findByIdAndState(event.getId(), EventState.PENDING)
                .isPresent());

        assertTrue(eventRepository
                .findByIdAndState(event.getId(), EventState.PUBLISHED)
                .isEmpty());
    }
}