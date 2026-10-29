package ru.practicum.ewm.compilation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationEventRepository;
import ru.practicum.ewm.compilation.repository.CompilationRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompilationPublicControllerTest {

    private static final String PUBLIC_PATH = "/compilations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompilationRepository compilationRepository;

    @Autowired
    private CompilationEventRepository compilationEventRepository;

    private Compilation first;

    @BeforeEach
    void setUp() {
        compilationEventRepository.deleteAll();
        compilationRepository.deleteAll();

        first = compilationRepository.save(
                Compilation.builder().title("Первая").pinned(true).build());
        compilationRepository.save(Compilation.builder().title("Вторая").pinned(false).build());
        compilationRepository.save(Compilation.builder().title("Третья").pinned(true).build());
    }

    @Test
    void shouldReturnAllCompilations() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("Первая"))
                .andExpect(jsonPath("$[0].events").isEmpty());
    }

    @Test
    void shouldFilterByPinned() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).param("pinned", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pinned").value(true))
                .andExpect(jsonPath("$[1].pinned").value(true));
    }

    @Test
    void shouldApplyPagination() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).param("from", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Вторая"));
    }

    @Test
    void shouldReturnBadRequestWhenFromIsNegative() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).param("from", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsZero() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnBadRequestWhenPaginationParameterIsNotNumber() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).param("from", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnCompilationById() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{compId}", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.title").value("Первая"))
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void shouldReturnNotFoundForMissingCompilation() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{compId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Compilation with id=999 was not found"));
    }
}
