package ru.practicum.ewm.comment;

import org.junit.jupiter.api.Test;
import ru.practicum.ewm.comment.model.CommentStatus;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentPublicControllerTest extends CommentControllerTestBase {

    private static final String BASE_PATH = "/events/{eventId}/comments";

    @Test
    void shouldReturnOnlyApprovedComments() throws Exception {
        saveComment(AUTHOR_ID, "Одобрен", CommentStatus.APPROVED);
        saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);
        saveComment(AUTHOR_ID, "Отклонён", CommentStatus.REJECTED);

        mockMvc.perform(get(BASE_PATH, PUBLISHED_EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Одобрен"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void shouldReturnCommentsNewestFirst() throws Exception {
        saveComment(AUTHOR_ID, "Старый", CommentStatus.APPROVED, LocalDateTime.now().minusDays(2));
        saveComment(AUTHOR_ID, "Новый", CommentStatus.APPROVED, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get(BASE_PATH, PUBLISHED_EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Новый"))
                .andExpect(jsonPath("$[1].text").value("Старый"));
    }

    @Test
    void shouldApplyPagination() throws Exception {
        saveComment(AUTHOR_ID, "Старый", CommentStatus.APPROVED, LocalDateTime.now().minusDays(2));
        saveComment(AUTHOR_ID, "Новый", CommentStatus.APPROVED, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get(BASE_PATH, PUBLISHED_EVENT_ID)
                        .param("from", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Старый"));
    }

    @Test
    void shouldReturnBadRequestWhenFromIsNegative() throws Exception {
        mockMvc.perform(get(BASE_PATH, PUBLISHED_EVENT_ID).param("from", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"));
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsZero() throws Exception {
        mockMvc.perform(get(BASE_PATH, PUBLISHED_EVENT_ID).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Incorrectly made request."));
    }

    @Test
    void shouldReturnNotFoundWhenEventMissing() throws Exception {
        mockMvc.perform(get(BASE_PATH, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event with id=999 was not found"));
    }

    @Test
    void shouldCountLikes() throws Exception {
        var comment = saveComment(AUTHOR_ID, "С лайками", CommentStatus.APPROVED);
        saveLike(comment.getId(), AUTHOR_ID);
        saveLike(comment.getId(), OTHER_USER_ID);

        mockMvc.perform(get(BASE_PATH, PUBLISHED_EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].likes").value(2));
    }
}
