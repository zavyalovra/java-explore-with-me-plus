package ru.practicum.ewm.stats.server.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.stats.server.exception.BadRequestException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatsServiceIntegrationTest {

    private static final String APP = "ewm-main-service";
    private static final String FIRST_URI = "/events/1";
    private static final String SECOND_URI = "/events/2";
    private static final LocalDateTime START = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2024, 1, 2, 0, 0);

    @Autowired
    private StatsService statsService;

    @Test
    void shouldCountAllHitsAndSortByHitsDescending() {
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(1));
        saveHit(FIRST_URI, "10.0.0.2", START.plusHours(2));
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(3));
        saveHit(SECOND_URI, "10.0.0.3", START.plusHours(4));

        List<ViewStatsDto> stats = statsService.getStats(START, END, null, false);

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).getApp()).isEqualTo(APP);
        assertThat(stats.get(0).getUri()).isEqualTo(FIRST_URI);
        assertThat(stats.get(0).getHits()).isEqualTo(3L);
        assertThat(stats.get(1).getUri()).isEqualTo(SECOND_URI);
        assertThat(stats.get(1).getHits()).isEqualTo(1L);
    }

    @Test
    void shouldCountOnlyUniqueIps() {
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(1));
        saveHit(FIRST_URI, "10.0.0.2", START.plusHours(2));
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(3));
        saveHit(SECOND_URI, "10.0.0.3", START.plusHours(4));

        List<ViewStatsDto> stats = statsService.getStats(START, END, null, true);

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).getUri()).isEqualTo(FIRST_URI);
        assertThat(stats.get(0).getHits()).isEqualTo(2L);
        assertThat(stats.get(1).getUri()).isEqualTo(SECOND_URI);
        assertThat(stats.get(1).getHits()).isEqualTo(1L);
    }

    @Test
    void shouldFilterByUris() {
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(1));
        saveHit(SECOND_URI, "10.0.0.2", START.plusHours(2));

        List<ViewStatsDto> stats = statsService.getStats(START, END, List.of(SECOND_URI), false);

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getUri()).isEqualTo(SECOND_URI);
        assertThat(stats.get(0).getHits()).isEqualTo(1L);
    }

    @Test
    void shouldFilterByUrisAndCountUniqueIps() {
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(1));
        saveHit(FIRST_URI, "10.0.0.1", START.plusHours(2));
        saveHit(FIRST_URI, "10.0.0.2", START.plusHours(3));
        saveHit(SECOND_URI, "10.0.0.3", START.plusHours(4));

        List<ViewStatsDto> all = statsService.getStats(START, END, List.of(FIRST_URI), false);

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getUri()).isEqualTo(FIRST_URI);
        assertThat(all.get(0).getHits()).isEqualTo(3L);

        List<ViewStatsDto> unique = statsService.getStats(START, END, List.of(FIRST_URI), true);

        assertThat(unique).hasSize(1);
        assertThat(unique.get(0).getUri()).isEqualTo(FIRST_URI);
        assertThat(unique.get(0).getHits()).isEqualTo(2L);
    }

    @Test
    void shouldRejectStartAfterEnd() {
        assertThatThrownBy(() -> statsService.getStats(END, START, null, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("start");
    }

    private void saveHit(String uri, String ip, LocalDateTime timestamp) {
        statsService.save(EndpointHitDto.builder()
                .app(APP)
                .uri(uri)
                .ip(ip)
                .timestamp(timestamp)
                .build());
    }
}
