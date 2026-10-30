package ru.practicum.ewm.category.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;

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
    void shouldCreateAndRetrieveCategory() {
        NewCategoryDto newCategoryDto = NewCategoryDto.builder()
                .name("Музеи")
                .build();

        CategoryDto created = categoryService.create(newCategoryDto);

        assertNotNull(created.getId());
        assertEquals("Музеи", created.getName());

        CategoryDto found = categoryService.getById(created.getId());
        assertEquals(created.getId(), found.getId());
        assertEquals("Музеи", found.getName());
    }

    @Test
    void shouldThrowConflictWhenCategoryNameAlreadyExists() {
        categoryService.create(NewCategoryDto.builder().name("Парки").build());

        assertThrows(ConflictException.class, () ->
                categoryService.create(NewCategoryDto.builder().name("Парки").build()));
    }

    @Test
    void shouldUpdateCategoryName() {
        CategoryDto created = categoryService.create(NewCategoryDto.builder().name("Лекции").build());

        CategoryDto updated = categoryService.update(created.getId(),
                CategoryDto.builder().name("Мастер-классы").build());

        assertEquals(created.getId(), updated.getId());
        assertEquals("Мастер-классы", updated.getName());
    }

    @Test
    void shouldThrowConflictWhenUpdatingToExistingCategoryName() {
        CategoryDto cat1 = categoryService.create(NewCategoryDto.builder().name("Кино").build());
        CategoryDto cat2 = categoryService.create(NewCategoryDto.builder().name("Театр").build());

        assertThrows(ConflictException.class, () ->
                categoryService.update(cat2.getId(), CategoryDto.builder().name("Кино").build()));
    }

    @Test
    void shouldDeleteCategorySuccessfully() {
        CategoryDto created = categoryService.create(NewCategoryDto.builder().name("Выставки").build());

        categoryService.delete(created.getId());

        assertFalse(categoryRepository.existsById(created.getId()));
        assertThrows(NotFoundException.class, () -> categoryService.getById(created.getId()));
    }

    @Test
    void shouldThrowWhenDeletingCategoryWithEvents() {
        Category category = categoryRepository.save(Category.builder().name("Спорт").build());

        jdbcTemplate.update("INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                1L, "User 1", "u1@test.com");

        jdbcTemplate.update("INSERT INTO events (id, title, annotation, description, category_id, initiator_id, "
                        + "event_date, created_on, lat, lon, paid, participant_limit, request_moderation, state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1L, "Матч", "Аннотация матча длиной более 20 символов",
                "Описание матча длиной более 20 символов", category.getId(), 1L,
                Timestamp.valueOf(LocalDateTime.now().plusDays(1)),
                Timestamp.valueOf(LocalDateTime.now()), 55.75, 37.61, false, 0, true, "PENDING");

        assertThrows(DataIntegrityViolationException.class, () ->
                categoryService.delete(category.getId()));
    }

    @Test
    void shouldReturnPaginatedCategories() {
        categoryService.create(NewCategoryDto.builder().name("А").build());
        categoryService.create(NewCategoryDto.builder().name("Б").build());
        categoryService.create(NewCategoryDto.builder().name("В").build());

        List<CategoryDto> page = categoryService.getAll(0, 2);
        assertEquals(2, page.size());
        assertEquals("А", page.get(0).getName());
        assertEquals("Б", page.get(1).getName());

        List<CategoryDto> nextPage = categoryService.getAll(2, 2);
        assertEquals(1, nextPage.size());
        assertEquals("В", nextPage.get(0).getName());
    }

    @Test
    void shouldThrowBadRequestForInvalidPagination() {
        assertThrows(BadRequestException.class, () -> categoryService.getAll(-1, 10));
        assertThrows(BadRequestException.class, () -> categoryService.getAll(0, 0));
    }
}
