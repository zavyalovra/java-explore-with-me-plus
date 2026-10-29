package ru.practicum.ewm.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventSort;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.event.util.OffsetPageRequest;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final EventResponseService eventResponseService;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    @Transactional
    public EventFullDto create(Long userId, NewEventDto dto) {

        if (dto.eventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Событие должно начинаться не раньше чем через 2 часа");
        }

        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с ID " + userId + " не найден"));

        Category category = categoryRepository.findById(dto.category())
                .orElseThrow(() -> new NotFoundException("Категория с ID " + dto.category() + " не найдена"));

        Event event = eventMapper.toEntity(dto, initiator, category);

        Event savedEvent = eventRepository.save(event);

        return eventMapper.toFullDto(savedEvent, 0L, 0L);
    }

    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        Pageable pageable = new OffsetPageRequest(from, size, DEFAULT_SORT);

        List<Event> events = eventRepository.findByInitiator_Id(userId, pageable).getContent();

        return eventResponseService.toShortDtos(events);
    }

    public EventFullDto getUserEventById(Long userId, Long eventId) {
        Event event = eventRepository
                .findByIdAndInitiator_Id(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено у пользователя с ID" + userId));

        return eventResponseService.toFullDto(event);
    }

    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        Event event = eventRepository
                .findByIdAndInitiator_Id(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено у пользователя с ID" + userId));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Организатор не может изменять опубликованное событие");
        }

        LocalDateTime eventDate = request.eventDate() != null ? request.eventDate() : event.getEventDate();

        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата события должна быть не раньше чем через 2 часа");
        }

        applyCommonUpdates(event, request);

        if (request.stateAction() != null) {
            switch (request.stateAction()) {
                case SEND_TO_REVIEW ->
                        event.setState(EventState.PENDING);

                case CANCEL_REVIEW ->
                        event.setState(EventState.CANCELED);
            }
        }

        return eventResponseService.toFullDto(event);
    }

    @Transactional
    public EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено"));

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime finalEventDate =
                request.eventDate() != null
                        ? request.eventDate()
                        : event.getEventDate();

        AdminEventStateAction action = request.stateAction();

        boolean publishing = action == AdminEventStateAction.PUBLISH_EVENT;

        if (request.eventDate() != null || publishing) {
            if (finalEventDate.isBefore(now.plusHours(1))) {
                throw new BadRequestException("Событие должно начинаться не раньше чем через 1 час");
            }
        }

        if (publishing && event.getState() != EventState.PENDING) {
            throw new ConflictException("Опубликовать можно только событие в статусе PENDING");
        }

        if (action == AdminEventStateAction.REJECT_EVENT && event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя отклонить опубликованное событие");
        }

        applyCommonUpdates(event, request);

        if (action != null) {
            switch (action) {
                case PUBLISH_EVENT -> {
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(now);
                }

                case REJECT_EVENT ->
                        event.setState(EventState.CANCELED);
            }
        }

        return eventResponseService.toFullDto(event);
    }

    public List<EventFullDto> searchAdminEvents(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            int from,
            int size
    ) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Параметр rangeStart не может быть позже rangeEnd");
        }

        Pageable pageable = new OffsetPageRequest(from, size, DEFAULT_SORT);

        Specification<Event> specification =
                EventSpecifications.forAdmin(
                        users,
                        states,
                        categories,
                        rangeStart,
                        rangeEnd
                );

        List<Event> events = eventRepository
                .findAll(specification, pageable)
                .getContent();

        return eventResponseService.toFullDtos(events);
    }

    public EventFullDto getPublicEventById(Long eventId, HttpServletRequest request) {
        Event event = eventRepository
                .findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено"));

        EndpointHitDto hit = EndpointHitDto.builder()
                .app("ewm-main-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();

        statsClient.saveHit(hit);

        return eventResponseService.toFullDto(event);
    }

    public List<EventShortDto> searchPublicEvents(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            boolean onlyAvailable,
            EventSort sort,
            int from,
            int size,
            HttpServletRequest request
    ) {
        if (text != null && (text.isBlank() || text.length() > 7000)) {
            throw new BadRequestException("Некорректный текст поиска");
        }

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Параметр rangeStart не может быть позже rangeEnd");
        }

        if (rangeStart == null && rangeEnd == null) {
            rangeStart = LocalDateTime.now();
        }

        Sort dateSort = Sort.by(Sort.Order.asc("eventDate"), Sort.Order.asc("id"));

        Pageable pageable = new OffsetPageRequest(from, size, dateSort);

        Specification<Event> specification =
                EventSpecifications.forPublic(
                        text,
                        categories,
                        paid,
                        rangeStart,
                        rangeEnd
                );

        List<EventShortDto> result;

        if (!onlyAvailable && sort != EventSort.VIEWS) {
            List<Event> events = eventRepository
                    .findAll(specification, pageable)
                    .getContent();

            result = eventResponseService.toShortDtos(events);
        } else {
            List<Event> events = eventRepository
                    .findAll(specification, dateSort);

            List<EventShortDto> dtos =
                    eventResponseService.toShortDtos(events);

            Map<Long, Integer> limits = events.stream()
                    .collect(Collectors.toMap(Event::getId, Event::getParticipantLimit));

            Stream<EventShortDto> stream = dtos.stream();

            if (onlyAvailable) {
                stream = stream.filter(dto -> {
                    int limit = limits.get(dto.getId());

                    return limit == 0
                            || dto.getConfirmedRequests() < limit;
                });
            }

            if (sort == EventSort.VIEWS) {
                Comparator<EventShortDto> byViews =
                        Comparator.comparingLong(
                                        (EventShortDto dto) -> dto.getViews()
                                ).reversed()
                                .thenComparing(EventShortDto::getEventDate)
                                .thenComparing(EventShortDto::getId);

                stream = stream.sorted(byViews);
            }

            result = stream
                    .skip(from)
                    .limit(size)
                    .toList();
        }

        recordPublicHit(request);

        return result;
    }

    private void recordPublicHit(HttpServletRequest request) {
        EndpointHitDto hit = EndpointHitDto.builder()
                .app("ewm-main-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();

        statsClient.saveHit(hit);
    }

    private void applyCommonUpdates(Event event, EventUpdateData request) {
        if (request.title() != null) {
            event.setTitle(request.title());
        }

        if (request.annotation() != null) {
            event.setAnnotation(request.annotation());
        }

        if (request.description() != null) {
            event.setDescription(request.description());
        }

        if (request.eventDate() != null) {
            event.setEventDate(request.eventDate());
        }

        if (request.location() != null) {
            event.setLocation(new Location(request.location().lat(), request.location().lon()));
        }

        if (request.paid() != null) {
            event.setPaid(request.paid());
        }

        if (request.participantLimit() != null) {
            event.setParticipantLimit(request.participantLimit());
        }

        if (request.requestModeration() != null) {
            event.setRequestModeration(request.requestModeration());
        }

        if (request.category() != null) {
            Category category = categoryRepository
                    .findById(request.category())
                    .orElseThrow(() -> new NotFoundException("Категория с ID " + request.category() + " не найдена"));

            event.setCategory(category);
        }
    }
}