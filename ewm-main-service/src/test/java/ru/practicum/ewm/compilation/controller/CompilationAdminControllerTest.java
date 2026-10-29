package ru.practicum.ewm.compilation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationEventRepository;
import ru.practicum.ewm.compilation.repository.CompilationRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompilationAdminControllerTest {

    private static final String ADMIN_PATH = "/admin/compilations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompilationRepository compilationRepository;

    @Autowired
    private CompilationEventRepository compilationEventRepository;

    @BeforeEach
    void cleanUp() {
        compilationEventRepository.deleteAll();
        compilationRepository.deleteAll();
    }

    @Test
    void shouldCreateCompilation() throws Exception {
        NewCompilationDto dto = new NewCompilationDto("Лучшие концерты", null, null);

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Лучшие концерты"))
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.events").isEmpty());
    }

    @Test
    void shouldCreatePinnedCompilation() throws Exception {
        NewCompilationDto dto = new NewCompilationDto("Закреплённая подборка", true, null);

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void shouldReturnBadRequestWhenTitleIsBlank() throws Exception {
        NewCompilationDto dto = new NewCompilationDto("   ", null, null);

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnConflictWhenTitleAlreadyExists() throws Exception {
        compilationRepository.save(Compilation.builder().title("Дубликат").pinned(false).build());
        NewCompilationDto dto = new NewCompilationDto("Дубликат", null, null);

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.reason").value("Integrity constraint has been violated."));
    }

    @Test
    void shouldUpdateCompilation() throws Exception {
        Compilation compilation = compilationRepository.save(
                Compilation.builder().title("Старое имя").pinned(false).build());
        UpdateCompilationRequest request = new UpdateCompilationRequest("Новое имя", true, null);

        mockMvc.perform(patch(ADMIN_PATH + "/{compId}", compilation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(compilation.getId()))
                .andExpect(jsonPath("$.title").value("Новое имя"))
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void shouldReturnConflictWhenUpdatingToExistingTitle() throws Exception {
        compilationRepository.save(Compilation.builder().title("Занятое имя").pinned(false).build());
        Compilation compilation = compilationRepository.save(
                Compilation.builder().title("Свободное имя").pinned(false).build());
        UpdateCompilationRequest request = new UpdateCompilationRequest("Занятое имя", null, null);

        mockMvc.perform(patch(ADMIN_PATH + "/{compId}", compilation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("CONFLICT"));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingCompilation() throws Exception {
        UpdateCompilationRequest request = new UpdateCompilationRequest("Имя", null, null);

        mockMvc.perform(patch(ADMIN_PATH + "/{compId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.reason").value("The required object was not found."))
                .andExpect(jsonPath("$.message").value("Compilation with id=999 was not found"));
    }

    @Test
    void shouldDeleteCompilation() throws Exception {
        Compilation compilation = compilationRepository.save(
                Compilation.builder().title("Удаляемая").pinned(false).build());

        mockMvc.perform(delete(ADMIN_PATH + "/{compId}", compilation.getId()))
                .andExpect(status().isNoContent());

        assertFalse(compilationRepository.existsById(compilation.getId()));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingCompilation() throws Exception {
        mockMvc.perform(delete(ADMIN_PATH + "/{compId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Compilation with id=999 was not found"));
    }
}
