package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.mapper.CompilationMapper;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.model.CompilationEvent;
import ru.practicum.ewm.compilation.model.CompilationEventId;
import ru.practicum.ewm.compilation.repository.CompilationEventRepository;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.service.EventResponseService;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final CompilationRepository compilationRepository;
    private final CompilationEventRepository compilationEventRepository;
    private final CompilationMapper compilationMapper;
    private final EventRepository eventRepository;
    private final EventResponseService eventResponseService;

    @Transactional
    public CompilationDto create(NewCompilationDto newCompilationDto) {
        if (compilationRepository.existsByTitle(newCompilationDto.getTitle())) {
            throw new ConflictException("Compilation with title=" + newCompilationDto.getTitle()
                    + " already exists");
        }

        Compilation compilation = compilationRepository.save(compilationMapper.toEntity(newCompilationDto));
        List<EventShortDto> events = replaceEvents(compilation.getId(), newCompilationDto.getEvents());

        return compilationMapper.toDto(compilation, events);
    }

    @Transactional
    public CompilationDto update(Long compilationId, UpdateCompilationRequest request) {
        Compilation compilation = findCompilation(compilationId);

        if (request.getTitle() != null) {
            boolean titleChanged = !request.getTitle().equals(compilation.getTitle());
            if (titleChanged && compilationRepository.existsByTitleAndIdNot(request.getTitle(), compilationId)) {
                throw new ConflictException("Compilation with title=" + request.getTitle()
                        + " already exists");
            }
            compilation.setTitle(request.getTitle());
        }

        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }

        List<EventShortDto> events;
        if (request.getEvents() != null) {
            compilationEventRepository.deleteByCompilationId(compilationId);
            events = replaceEvents(compilationId, request.getEvents());
        } else {
            events = findEventsFor(compilation);
        }

        return compilationMapper.toDto(compilation, events);
    }

    @Transactional
    public void delete(Long compilationId) {
        Compilation compilation = findCompilation(compilationId);
        compilationEventRepository.deleteByCompilationId(compilationId);
        compilationRepository.delete(compilation);
    }

    public CompilationDto getById(Long compilationId) {
        return toDtoList(List.of(findCompilation(compilationId))).get(0);
    }

    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        validatePagination(from, size);

        Pageable pageable = PageRequest.of(from / size, size, DEFAULT_SORT);
        Page<Compilation> page = pinned == null
                ? compilationRepository.findAll(pageable)
                : compilationRepository.findAllByPinned(pinned, pageable);

        return toDtoList(page.getContent());
    }

    /**
     * Собирает DTO для набора подборок, не допуская N+1:
     * связки и события достаются пакетом на всю страницу.
     */
    private List<CompilationDto> toDtoList(List<Compilation> compilations) {
        if (compilations.isEmpty()) {
            return List.of();
        }

        List<Long> compilationIds = compilations.stream()
                .map(Compilation::getId)
                .toList();
        Map<Long, List<Long>> eventIdsByCompilation = findEventIdsByCompilation(compilationIds);
        Map<Long, EventShortDto> events = findEvents(eventIdsByCompilation.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet()));

        return compilations.stream()
                .map(compilation -> compilationMapper.toDto(compilation, pickEvents(
                        eventIdsByCompilation.getOrDefault(compilation.getId(), List.of()), events)))
                .toList();
    }

    private Map<Long, List<Long>> findEventIdsByCompilation(Collection<Long> compilationIds) {
        return compilationEventRepository.findByIdCompilationIdIn(compilationIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getId().getCompilationId(),
                        Collectors.mapping(link -> link.getId().getEventId(), Collectors.toList())));
    }

    private List<EventShortDto> findEventsFor(Compilation compilation) {
        Map<Long, List<Long>> eventIds = findEventIdsByCompilation(List.of(compilation.getId()));
        Map<Long, EventShortDto> events = findEvents(eventIds.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet()));

        return pickEvents(eventIds.getOrDefault(compilation.getId(), List.of()), events);
    }

    private Map<Long, EventShortDto> findEvents(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }

        List<Event> events = eventRepository.findAllById(eventIds).stream()
                .sorted(Comparator.comparing(Event::getId))
                .toList();

        return eventResponseService.toShortDtos(events).stream()
                .collect(Collectors.toMap(EventShortDto::getId, dto -> dto, (first, second) -> first));
    }

    private List<EventShortDto> pickEvents(List<Long> eventIds, Map<Long, EventShortDto> events) {
        return eventIds.stream()
                .sorted()
                .map(events::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Заменяет состав подборки. Сначала проверяет, что все события существуют,
     * и только потом пишет связки — иначе на несуществующий id вернулся бы 409
     * с текстом нарушения внешнего ключа вместо 404.
     */
    private List<EventShortDto> replaceEvents(Long compilationId, Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        List<Long> sortedIds = eventIds.stream().sorted().toList();
        Map<Long, EventShortDto> events = findEvents(sortedIds);

        if (events.size() != sortedIds.size()) {
            Long missingId = sortedIds.stream()
                    .filter(eventId -> !events.containsKey(eventId))
                    .findFirst()
                    .orElseThrow();
            throw new NotFoundException("Event with id=" + missingId + " was not found");
        }

        List<CompilationEvent> links = sortedIds.stream()
                .map(eventId -> new CompilationEvent(new CompilationEventId(compilationId, eventId)))
                .toList();

        compilationEventRepository.saveAll(links);

        return pickEvents(sortedIds, events);
    }

    private void validatePagination(int from, int size) {
        if (from < 0) {
            throw new BadRequestException("Параметр 'from' должен быть больше или равен 0");
        }
        if (size <= 0) {
            throw new BadRequestException("Параметр 'size' должен быть больше 0");
        }
    }

    private Compilation findCompilation(Long compilationId) {
        return compilationRepository.findById(compilationId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compilationId
                        + " was not found"));
    }
}
