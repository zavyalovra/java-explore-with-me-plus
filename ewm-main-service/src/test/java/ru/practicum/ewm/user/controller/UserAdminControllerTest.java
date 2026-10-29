package ru.practicum.ewm.user.controller;

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
import ru.practicum.ewm.user.dto.NewUserRequest;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdminControllerTest {

    private static final String ADMIN_PATH = "/admin/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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
    void shouldCreateUser() throws Exception {
        NewUserRequest request = NewUserRequest.builder()
                .name("Иван Иванов")
                .email("ivan.ivanov@mail.ru")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Иван Иванов"))
                .andExpect(jsonPath("$.email").value("ivan.ivanov@mail.ru"));
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        userRepository.save(User.builder().name("Петр").email("petr@mail.ru").build());

        NewUserRequest duplicateRequest = NewUserRequest.builder()
                .name("Другой Петр")
                .email("petr@mail.ru")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
        NewUserRequest invalidRequest = NewUserRequest.builder()
                .name(" ")
                .email("test@mail.ru")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsInvalid() throws Exception {
        NewUserRequest invalidRequest = NewUserRequest.builder()
                .name("Пользователь")
                .email("invalid-email")
                .build();

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUsersWithoutIdsFilter() throws Exception {
        userRepository.save(User.builder().name("User 1").email("u1@mail.ru").build());
        userRepository.save(User.builder().name("User 2").email("u2@mail.ru").build());
        userRepository.save(User.builder().name("User 3").email("u3@mail.ru").build());

        mockMvc.perform(get(ADMIN_PATH)
                        .param("from", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("User 1"))
                .andExpect(jsonPath("$[1].name").value("User 2"));
    }

    @Test
    void shouldReturnUsersWithIdsFilter() throws Exception {
        User u1 = userRepository.save(User.builder().name("User 1").email("u1@mail.ru").build());
        userRepository.save(User.builder().name("User 2").email("u2@mail.ru").build());
        User u3 = userRepository.save(User.builder().name("User 3").email("u3@mail.ru").build());

        mockMvc.perform(get(ADMIN_PATH)
                        .param("ids", String.valueOf(u1.getId()), String.valueOf(u3.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(u1.getId()))
                .andExpect(jsonPath("$[1].id").value(u3.getId()));
    }

    @Test
    void shouldReturnBadRequestWhenPaginationIsNegative() throws Exception {
        mockMvc.perform(get(ADMIN_PATH)
                        .param("from", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteUser() throws Exception {
        User user = userRepository.save(User.builder().name("Удаляемый").email("del@mail.ru").build());

        mockMvc.perform(delete(ADMIN_PATH + "/{userId}", user.getId()))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(user.getId()));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentUser() throws Exception {
        mockMvc.perform(delete(ADMIN_PATH + "/{userId}", 9999L))
                .andExpect(status().isNotFound());
    }
}
