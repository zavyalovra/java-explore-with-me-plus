package ru.practicum.ewm.category.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryPublicControllerTest {

    private static final String PUBLIC_PATH = "/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category first;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM compilation_events");
        jdbcTemplate.execute("DELETE FROM participation_requests");
        jdbcTemplate.execute("DELETE FROM events");
        jdbcTemplate.execute("DELETE FROM categories");
        jdbcTemplate.execute("DELETE FROM users");

        first = categoryRepository.save(Category.builder().name("Концерты").build());
        categoryRepository.save(Category.builder().name("Театры").build());
        categoryRepository.save(Category.builder().name("Выставки").build());
    }

    @Test
    void shouldReturnAllCategoriesWithPagination() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH)
                        .param("from", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Концерты"))
                .andExpect(jsonPath("$[1].name").value("Театры"));
    }

    @Test
    void shouldReturnCategoryById() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{catId}", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.name").value("Концерты"));
    }

    @Test
    void shouldReturnNotFoundWhenCategoryDoesNotExist() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{catId}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenPaginationIsInvalid() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH)
                        .param("from", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }
}
