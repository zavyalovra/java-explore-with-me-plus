package ru.practicum.ewm.stats.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.stats.server.exception.BadRequestException;
import ru.practicum.ewm.stats.server.model.Hit;
import ru.practicum.ewm.stats.server.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository statsRepository;

    @Transactional
    public void save(EndpointHitDto endpointHitDto) {
        Hit hit = Hit.builder()
                .app(endpointHitDto.getApp())
                .uri(endpointHitDto.getUri())
                .ip(endpointHitDto.getIp())
                .timestamp(endpointHitDto.getTimestamp())
                .build();
        statsRepository.save(hit);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, boolean unique) {
        if (start.isAfter(end)) {
            throw new BadRequestException("Параметр 'start' должен быть не позже 'end'");
        }

        boolean filterByUris = uris != null && !uris.isEmpty();

        if (unique) {
            return filterByUris
                    ? statsRepository.findUniqueStatsByUris(start, end, uris)
                    : statsRepository.findUniqueStats(start, end);
        }

        return filterByUris
                ? statsRepository.findStatsByUris(start, end, uris)
                : statsRepository.findStats(start, end);
    }
}
