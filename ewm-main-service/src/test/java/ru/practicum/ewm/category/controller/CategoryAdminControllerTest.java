package ru.practicum.ewm.category.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryAdminControllerTest {

    private static final String ADMIN_PATH = "/admin/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM compilation_events");
        jdbcTemplate.execute("DELETE FROM participation_requests");
        jdbcTemplate.execute("DELETE FROM events");
        jdbcTemplate.execute("DELETE FROM categories");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    void shouldCreateCategory() throws Exception {
        NewCategoryDto newCategoryDto = NewCategoryDto.builder()
                .name("Выставки")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCategoryDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Выставки"));
    }

    @Test
    void shouldReturnConflictWhenCreatingDuplicateCategory() throws Exception {
        categoryRepository.save(Category.builder().name("Концерты").build());

        NewCategoryDto duplicateDto = NewCategoryDto.builder()
                .name("Концерты")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
        NewCategoryDto invalidDto = NewCategoryDto.builder()
                .name("   ")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateCategory() throws Exception {
        Category saved = categoryRepository.save(Category.builder().name("Старое имя").build());

        CategoryDto updateDto = CategoryDto.builder()
                .name("Новое имя")
                .build();

        mockMvc.perform(patch(ADMIN_PATH + "/{catId}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Новое имя"));
    }

    @Test
    void shouldReturnConflictWhenUpdatingToExistingName() throws Exception {
        Category first = categoryRepository.save(Category.builder().name("Кино").build());
        Category second = categoryRepository.save(Category.builder().name("Театр").build());

        CategoryDto updateDto = CategoryDto.builder()
                .name("Кино")
                .build();

        mockMvc.perform(patch(ADMIN_PATH + "/{catId}", second.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentCategory() throws Exception {
        CategoryDto updateDto = CategoryDto.builder()
                .name("Любое имя")
                .build();

        mockMvc.perform(patch(ADMIN_PATH + "/{catId}", 9999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteCategory() throws Exception {
        Category saved = categoryRepository.save(Category.builder().name("Фестивали").build());

        mockMvc.perform(delete(ADMIN_PATH + "/{catId}", saved.getId()))
                .andExpect(status().isNoContent());

        assertFalse(categoryRepository.existsById(saved.getId()));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentCategory() throws Exception {
        mockMvc.perform(delete(ADMIN_PATH + "/{catId}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenDeletingCategoryWithEvents() throws Exception {
        Category category = categoryRepository.save(Category.builder().name("Спорт").build());

        jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                1L, "Test User", "user@test.com");

        jdbcTemplate.update("INSERT INTO events (id, title, annotation, description, category_id, initiator_id, "
                        + "event_date, created_on, lat, lon, paid, participant_limit, request_moderation, state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1L, "Матч", "Аннотация матча длиной более 20 символов",
                "Описание матча длиной более 20 символов", category.getId(), 1L,
                Timestamp.valueOf(LocalDateTime.now().plusDays(1)),
                Timestamp.valueOf(LocalDateTime.now()), 55.75, 37.61, false, 0, true, "PENDING");

        mockMvc.perform(delete(ADMIN_PATH + "/{catId}", category.getId()))
                .andExpect(status().isConflict());
    }
}
