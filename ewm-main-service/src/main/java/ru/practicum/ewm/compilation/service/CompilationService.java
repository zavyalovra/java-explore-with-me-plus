package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.common.PageRequestFactory;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.mapper.CompilationMapper;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.model.CompilationEvent;
import ru.practicum.ewm.compilation.model.CompilationEventId;
import ru.practicum.ewm.compilation.repository.CompilationEventRepository;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final CompilationRepository compilationRepository;
    private final CompilationEventRepository compilationEventRepository;
    private final CompilationMapper compilationMapper;

    @Transactional
    public CompilationDto create(NewCompilationDto newCompilationDto) {
        if (compilationRepository.existsByTitle(newCompilationDto.getTitle())) {
            throw new ConflictException("Compilation with title=" + newCompilationDto.getTitle()
                    + " already exists");
        }

        Compilation compilation = compilationRepository.save(compilationMapper.toEntity(newCompilationDto));
        saveEvents(compilation.getId(), newCompilationDto.getEvents());

        return compilationMapper.toDto(compilation);
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

        if (request.getEvents() != null) {
            compilationEventRepository.deleteByCompilationId(compilationId);
            saveEvents(compilationId, request.getEvents());
        }

        return compilationMapper.toDto(compilation);
    }

    @Transactional
    public void delete(Long compilationId) {
        Compilation compilation = findCompilation(compilationId);
        compilationEventRepository.deleteByCompilationId(compilationId);
        compilationRepository.delete(compilation);
    }

    public CompilationDto getById(Long compilationId) {
        return compilationMapper.toDto(findCompilation(compilationId));
    }

    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        Pageable pageable = PageRequestFactory.create(from, size, DEFAULT_SORT);
        Page<Compilation> page = pinned == null
                ? compilationRepository.findAll(pageable)
                : compilationRepository.findAllByPinned(pinned, pageable);

        return page.getContent().stream()
                .map(compilationMapper::toDto)
                .toList();
    }

    private Compilation findCompilation(Long compilationId) {
        return compilationRepository.findById(compilationId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compilationId
                        + " was not found"));
    }

    private void saveEvents(Long compilationId, Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }

        List<CompilationEvent> links = eventIds.stream()
                .map(eventId -> new CompilationEvent(new CompilationEventId(compilationId, eventId)))
                .toList();

        compilationEventRepository.saveAll(links);
    }
}
