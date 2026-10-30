package ru.practicum.ewm.comment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.model.CommentLike;
import ru.practicum.ewm.comment.model.CommentLikeId;
import ru.practicum.ewm.comment.model.CommentStatus;
import ru.practicum.ewm.comment.repository.CommentLikeRepository;
import ru.practicum.ewm.comment.repository.CommentRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class CommentControllerTestBase {

    protected static final long AUTHOR_ID = 1L;
    protected static final long OTHER_USER_ID = 2L;
    protected static final long PUBLISHED_EVENT_ID = 1L;
    protected static final long PENDING_EVENT_ID = 2L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected CommentRepository commentRepository;

    @Autowired
    protected CommentLikeRepository commentLikeRepository;

    @BeforeEach
    void resetData() {
        commentLikeRepository.deleteAll();
        commentRepository.deleteAll();
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from categories");
        jdbcTemplate.update("delete from users");

        insertUser(AUTHOR_ID, "Иван", "ivan@mail.ru");
        insertUser(OTHER_USER_ID, "Пётр", "petr@mail.ru");
        jdbcTemplate.update("insert into categories (id, name) values (?, ?)", 1L, "Концерты");
        insertEvent(PUBLISHED_EVENT_ID, "PUBLISHED");
        insertEvent(PENDING_EVENT_ID, "PENDING");
    }

    protected void insertUser(long userId, String name, String email) {
        jdbcTemplate.update("insert into users (id, name, email) values (?, ?, ?)", userId, name, email);
    }

    protected void insertEvent(long eventId, String state) {
        jdbcTemplate.update("insert into events (id, title, annotation, description, category_id, initiator_id, "
                        + "event_date, lat, lon, state) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, "Событие " + eventId, "Аннотация события", "Описание события", 1L, AUTHOR_ID,
                Timestamp.valueOf(LocalDateTime.now().plusDays(1)), 55.75, 37.61, state);
    }

    protected Comment saveComment(long authorId, String text, CommentStatus status) {
        return saveComment(authorId, text, status, LocalDateTime.now());
    }

    protected Comment saveComment(long authorId, String text, CommentStatus status, LocalDateTime createdOn) {
        boolean rejected = status == CommentStatus.REJECTED;
        return commentRepository.save(Comment.builder()
                .eventId(PUBLISHED_EVENT_ID)
                .authorId(authorId)
                .text(text)
                .status(status)
                .moderationReason(rejected ? "Причина отклонения" : null)
                .moderatedOn(rejected ? LocalDateTime.now() : null)
                .createdOn(createdOn)
                .build());
    }

    protected void saveLike(long commentId, long userId) {
        commentLikeRepository.save(new CommentLike(
                new CommentLikeId(commentId, userId), LocalDateTime.now()));
    }
}
