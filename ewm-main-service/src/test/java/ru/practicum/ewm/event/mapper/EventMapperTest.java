package ru.practicum.ewm.event.mapper;

import org.junit.jupiter.api.Test;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.LocationDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EventMapperTest {

    private final EventMapper mapper = new EventMapper(
            new UserMapper(),
            new CategoryMapper()
    );

    @Test
    void shouldMapNewEventDtoToEntity() {
        User user = User.builder()
                .id(1L)
                .name("Иван")
                .build();

        Category category = Category.builder()
                .id(2L)
                .name("Концерты")
                .build();

        LocalDateTime eventDate = LocalDateTime.now().plusDays(3);

        NewEventDto dto = new NewEventDto(
                "Большой музыкальный фестиваль",
                2L,
                "Фестиваль с участием известных музыкантов",
                eventDate,
                new LocationDto(55.75, 37.61),
                null,
                null,
                null,
                "Музыкальный фестиваль"
        );

        Event event = mapper.toEntity(dto, user, category);

        assertEquals("Музыкальный фестиваль", event.getTitle());
        assertEquals(user, event.getInitiator());
        assertEquals(category, event.getCategory());
        assertEquals(eventDate, event.getEventDate());
        assertEquals(EventState.PENDING, event.getState());

        assertFalse(event.isPaid());
        assertEquals(0, event.getParticipantLimit());
        assertTrue(event.isRequestModeration());

        assertEquals(55.75, event.getLocation().getLat());
        assertEquals(37.61, event.getLocation().getLon());
    }

    @Test
    void shouldMapEventToResponseDtos() {
        User user = User.builder()
                .id(1L)
                .name("Иван")
                .build();

        Category category = Category.builder()
                .id(2L)
                .name("Концерты")
                .build();

        Event event = Event.builder()
                .id(10L)
                .title("Концерт")
                .annotation("Большой концерт под открытым небом")
                .description("Музыкальное мероприятие с участием артистов")
                .initiator(user)
                .category(category)
                .eventDate(LocalDateTime.now().plusDays(3))
                .createdOn(LocalDateTime.now())
                .location(new Location(55.75, 37.61))
                .build();

        EventFullDto fullDto = mapper.toFullDto(event, 15L, 100L);
        EventShortDto shortDto = mapper.toShortDto(event, 15L, 100L);

        assertEquals(10L, fullDto.getId());
        assertEquals("Концерт", fullDto.getTitle());
        assertEquals(EventState.PENDING, fullDto.getState());

        assertEquals(1L, fullDto.getInitiator().getId());
        assertEquals(2L, fullDto.getCategory().getId());

        assertEquals(15L, fullDto.getConfirmedRequests());
        assertEquals(100L, fullDto.getViews());

        assertEquals(55.75, fullDto.getLocation().getLat());

        assertEquals(10L, shortDto.getId());
        assertEquals(15L, shortDto.getConfirmedRequests());
        assertEquals(100L, shortDto.getViews());
    }
}