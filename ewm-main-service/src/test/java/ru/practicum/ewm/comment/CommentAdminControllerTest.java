package ru.practicum.ewm.comment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.practicum.ewm.comment.dto.ModerationDecision;
import ru.practicum.ewm.comment.dto.ModerationRequest;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.model.CommentStatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentAdminControllerTest extends CommentControllerTestBase {

    private static final String BASE_PATH = "/admin/comments";

    @Test
    void shouldReturnAllComments() throws Exception {
        saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);
        saveComment(AUTHOR_ID, "Одобрен", CommentStatus.APPROVED);

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldFilterByStatus() throws Exception {
        saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);
        saveComment(AUTHOR_ID, "Одобрен", CommentStatus.APPROVED);

        mockMvc.perform(get(BASE_PATH).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void shouldApproveComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);
        ModerationRequest request = new ModerationRequest(ModerationDecision.APPROVED, null);

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.moderatedOn").isString());
    }

    @Test
    void shouldRejectCommentWithReason() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Спам", CommentStatus.PENDING);
        ModerationRequest request = new ModerationRequest(ModerationDecision.REJECTED, "Реклама");

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.moderationReason").value("Реклама"));
    }

    @Test
    void shouldReturnBadRequestWhenRejectingWithoutReason() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Спам", CommentStatus.PENDING);
        ModerationRequest request = new ModerationRequest(ModerationDecision.REJECTED, null);

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnBadRequestWhenStatusIsPending() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnConflictWhenModeratingTwice() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "Уже одобрен", CommentStatus.APPROVED);
        ModerationRequest request = new ModerationRequest(ModerationDecision.APPROVED, null);

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", comment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Comment with id=" + comment.getId()
                        + " has already been moderated"));
    }

    @Test
    void shouldReturnNotFoundWhenModeratingMissingComment() throws Exception {
        ModerationRequest request = new ModerationRequest(ModerationDecision.APPROVED, null);

        mockMvc.perform(patch(BASE_PATH + "/{commentId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment with id=999 was not found"));
    }

    @Test
    void shouldDeleteAnyComment() throws Exception {
        Comment comment = saveComment(AUTHOR_ID, "На удаление", CommentStatus.APPROVED);
        saveLike(comment.getId(), OTHER_USER_ID);

        mockMvc.perform(delete(BASE_PATH + "/{commentId}", comment.getId()))
                .andExpect(status().isNoContent());

        assertFalse(commentRepository.existsById(comment.getId()));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingComment() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{commentId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment with id=999 was not found"));
    }
}
