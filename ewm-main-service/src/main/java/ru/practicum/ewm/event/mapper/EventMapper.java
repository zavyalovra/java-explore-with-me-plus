package ru.practicum.ewm.event.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.LocationDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;

    public Event toEntity(NewEventDto dto, User initiator, Category category) {
        return Event.builder()
                .title(dto.getTitle())
                .annotation(dto.getAnnotation())
                .description(dto.getDescription())
                .initiator(initiator)
                .category(category)
                .eventDate(dto.getEventDate())
                .location(toLocation(dto.getLocation()))
                .paid(dto.getPaid())
                .participantLimit(dto.getParticipantLimit())
                .requestModeration(dto.getRequestModeration())
                .build();
    }

    public EventFullDto toFullDto(Event event, long confirmedRequests, long views) {
        return EventFullDto.builder()
                .id(event.getId())
                .title(event.getTitle())
                .annotation(event.getAnnotation())
                .description(event.getDescription())
                .category(categoryMapper.toDto(event.getCategory()))
                .initiator(userMapper.toShortDto(event.getInitiator()))
                .eventDate(event.getEventDate())
                .createdOn(event.getCreatedOn())
                .publishedOn(event.getPublishedOn())
                .location(toLocationDto(event.getLocation()))
                .paid(event.isPaid())
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.isRequestModeration())
                .state(event.getState())
                .confirmedRequests(confirmedRequests)
                .views(views)
                .build();
    }

    public EventShortDto toShortDto(Event event, long confirmedRequests, long views) {
        return EventShortDto.builder()
                .id(event.getId())
                .title(event.getTitle())
                .annotation(event.getAnnotation())
                .category(categoryMapper.toDto(event.getCategory()))
                .initiator(userMapper.toShortDto(event.getInitiator()))
                .eventDate(event.getEventDate())
                .paid(event.isPaid())
                .confirmedRequests(confirmedRequests)
                .views(views)
                .build();
    }

    private Location toLocation(LocationDto dto) {
        return new Location(dto.getLat(), dto.getLon());
    }

    private LocationDto toLocationDto(Location location) {
        return new LocationDto(location.getLat(), location.getLon());
    }
}