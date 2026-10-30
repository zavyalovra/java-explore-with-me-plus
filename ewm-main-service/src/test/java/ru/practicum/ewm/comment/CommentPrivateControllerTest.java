package ru.practicum.ewm.comment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentRequest;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.model.CommentStatus;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentPrivateControllerTest extends CommentControllerTestBase {

    private static final String BASE_PATH = "/users/{userId}/comments";

    @Test
    void shouldCreateComment() throws Exception {
        NewCommentDto dto = new NewCommentDto(PUBLISHED_EVENT_ID, "Отличное событие");

        mockMvc.perform(post(BASE_PATH, AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.text").value("Отличное событие"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.author.id").value(1))
                .andExpect(jsonPath("$.author.name").value("Иван"))
                .andExpect(jsonPath("$.likes").value(0));
    }

    @Test
    void shouldReturnConflictWhenEventIsNotPublished() throws Exception {
        NewCommentDto dto = new NewCommentDto(PENDING_EVENT_ID, "Рано писать");

        mockMvc.perform(post(BASE_PATH, AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.reason").value("Integrity constraint has been violated."))
                .andExpect(jsonPath("$.message").value("Event with id=2 is not published"));
    }

    @Test
    void shouldReturnNotFoundWhenEventNotFound() throws Exception {
        NewCommentDto dto = new NewCommentDto(999L, "Нет такого события");

        mockMvc.perform(post(BASE_PATH, AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event with id=999 was not found"));
    }

    @Test
    void shouldReturnNotFoundWhenUserNotFound() throws Exception {
        NewCommentDto dto = new NewCommentDto(PUBLISHED_EVENT_ID, "Нет такого автора");

        mockMvc.perform(post(BASE_PATH, 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with id=999 was not found"));
    }

    @Test
    void shouldReturnBadRequestWhenTextIsBlank() throws Exception {
        NewCommentDto dto = new NewCommentDto(PUBLISHED_EVENT_ID, "   ");

        mockMvc.perform(post(BASE_PATH, AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnBadRequestWhenTextIsTooLong() throws Exception {
        NewCommentDto dto = new NewCommentDto(PUBLISHED_EVENT_ID, "a".repeat(2001));

        mockMvc.perform(post(BASE_PATH, AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnBadRequestWhenEventIdIsMissing() throws Exception {
        mockMvc.perform(post(BASE_PATH, AUTHOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Без события\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldResetStatusAfterUpdate() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Старый текст", CommentStatus.APPROVED);
        UpdateCommentRequest request = new UpdateCommentRequest("Новый текст");

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", AUTHOR_ID, comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Новый текст"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.moderationReason").doesNotExist())
                .andExpect(jsonPath("$.moderatedOn").doesNotExist());
    }

    @Test
    void shouldReturnForbiddenWhenUpdatingForeignComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Чужой комментарий", CommentStatus.APPROVED);
        UpdateCommentRequest request = new UpdateCommentRequest("Подмена");

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", OTHER_USER_ID, comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.reason").value("Access is denied."));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingComment() throws Exception {
        UpdateCommentRequest request = new UpdateCommentRequest("Текст");

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", AUTHOR_ID, 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment with id=999 was not found"));
    }

    @Test
    void shouldDeleteOwnComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Удаляемый", CommentStatus.APPROVED);
        saveLike(comment.getId(), OTHER_USER_ID);

        mockMvc.perform(delete(BASE_PATH + "/{commentId}", AUTHOR_ID, comment.getId()))
                .andExpect(status().isNoContent());

        assertFalse(commentRepository.existsById(comment.getId()));
        assertEquals(0, commentLikeRepository.count());
    }

    @Test
    void shouldReturnForbiddenWhenDeletingForeignComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Чужой комментарий", CommentStatus.APPROVED);

        mockMvc.perform(delete(BASE_PATH + "/{commentId}", OTHER_USER_ID, comment.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("Access is denied."));

        assertTrue(commentRepository.existsById(comment.getId()));
    }

    @Test
    void shouldReturnOwnComments() throws Exception {
        saveComment(AUTHOR_ID, "Первый", CommentStatus.APPROVED, LocalDateTime.now().minusDays(2));
        saveComment(AUTHOR_ID, "Второй", CommentStatus.REJECTED, LocalDateTime.now().minusDays(1));
        saveComment(OTHER_USER_ID, "Чужой", CommentStatus.APPROVED);

        mockMvc.perform(get(BASE_PATH, AUTHOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("Второй"))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].text").value("Первый"));
    }

    @Test
    void shouldLikeApprovedComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Хороший", CommentStatus.APPROVED);

        mockMvc.perform(post(BASE_PATH + "/{commentId}/likes", OTHER_USER_ID, comment.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likes").value(1));
    }

    @Test
    void shouldReturnConflictWhenLikingTwice() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Хороший", CommentStatus.APPROVED);
        saveLike(comment.getId(), OTHER_USER_ID);

        mockMvc.perform(post(BASE_PATH + "/{commentId}/likes", OTHER_USER_ID, comment.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("CONFLICT"));
    }

    @Test
    void shouldReturnConflictWhenLikingNotApprovedComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);

        mockMvc.perform(post(BASE_PATH + "/{commentId}/likes", OTHER_USER_ID, comment.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Comment with id=" + comment.getId() + " is not approved"));
    }

    @Test
    void shouldReturnNotFoundWhenLikingMissingComment() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/{commentId}/likes", OTHER_USER_ID, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment with id=999 was not found"));
    }

    @Test
    void shouldReturnNotFoundWhenLikingByMissingUser() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Хороший", CommentStatus.APPROVED);

        mockMvc.perform(post(BASE_PATH + "/{commentId}/likes", 999L, comment.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with id=999 was not found"));
    }

    @Test
    void shouldUnlikeComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Хороший", CommentStatus.APPROVED);
        saveLike(comment.getId(), OTHER_USER_ID);

        mockMvc.perform(delete(BASE_PATH + "/{commentId}/likes", OTHER_USER_ID, comment.getId()))
                .andExpect(status().isNoContent());

        assertEquals(0, commentLikeRepository.count());
    }

    @Test
    void shouldReturnNotFoundWhenUnlikingComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Хороший", CommentStatus.APPROVED);

        mockMvc.perform(delete(BASE_PATH + "/{commentId}/likes", OTHER_USER_ID, comment.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }
}
