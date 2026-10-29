package ru.practicum.ewm.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.dto.NewUserRequest;
import ru.practicum.ewm.user.dto.UserDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

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
    void shouldCreateAndRetrieveUser() {
        NewUserRequest request = NewUserRequest.builder()
                .name("Сергей")
                .email("sergey@mail.ru")
                .build();

        UserDto created = userService.create(request);

        assertNotNull(created.getId());
        assertEquals("Сергей", created.getName());
        assertEquals("sergey@mail.ru", created.getEmail());

        User inDb = userRepository.findById(created.getId()).orElse(null);
        assertNotNull(inDb);
        assertEquals("Сергей", inDb.getName());
    }

    @Test
    void shouldThrowConflictWhenEmailAlreadyExists() {
        userService.create(NewUserRequest.builder().name("Аня").email("anya@mail.ru").build());

        assertThrows(ConflictException.class, () ->
                userService.create(NewUserRequest.builder().name("Другая Аня").email("anya@mail.ru").build()));
    }

    @Test
    void shouldDeleteUserSuccessfully() {
        UserDto created = userService.create(NewUserRequest.builder().name("Михаил").email("misha@mail.ru").build());

        userService.delete(created.getId());

        assertFalse(userRepository.existsById(created.getId()));
        assertThrows(NotFoundException.class, () -> userService.delete(created.getId()));
    }

    @Test
    void shouldFilterUsersByIds() {
        UserDto u1 = userService.create(NewUserRequest.builder().name("U1").email("u1@mail.ru").build());
        UserDto u2 = userService.create(NewUserRequest.builder().name("U2").email("u2@mail.ru").build());
        UserDto u3 = userService.create(NewUserRequest.builder().name("U3").email("u3@mail.ru").build());

        List<UserDto> filtered = userService.getAll(List.of(u1.getId(), u3.getId()), 0, 10);

        assertEquals(2, filtered.size());
        assertEquals(u1.getId(), filtered.get(0).getId());
        assertEquals(u3.getId(), filtered.get(1).getId());
    }

    @Test
    void shouldReturnPaginatedUsersWhenNoIdsProvided() {
        userService.create(NewUserRequest.builder().name("U1").email("u1@mail.ru").build());
        userService.create(NewUserRequest.builder().name("U2").email("u2@mail.ru").build());
        userService.create(NewUserRequest.builder().name("U3").email("u3@mail.ru").build());

        List<UserDto> page = userService.getAll(null, 0, 2);
        assertEquals(2, page.size());
        assertEquals("U1", page.get(0).getName());
        assertEquals("U2", page.get(1).getName());

        List<UserDto> nextPage = userService.getAll(List.of(), 2, 2);
        assertEquals(1, nextPage.size());
        assertEquals("U3", nextPage.get(0).getName());
    }

    @Test
    void shouldThrowBadRequestWhenPaginationIsInvalid() {
        assertThrows(BadRequestException.class, () -> userService.getAll(null, -1, 10));
        assertThrows(BadRequestException.class, () -> userService.getAll(null, 0, 0));
    }
}
