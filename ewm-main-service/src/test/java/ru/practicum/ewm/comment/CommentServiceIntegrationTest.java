package ru.practicum.ewm.comment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.ModerationDecision;
import ru.practicum.ewm.comment.dto.ModerationRequest;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentRequest;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.model.CommentStatus;
import ru.practicum.ewm.comment.service.CommentService;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.ForbiddenException;
import ru.practicum.ewm.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentServiceIntegrationTest extends CommentControllerTestBase {

    @Autowired
    private CommentService commentService;

    @Test
    void shouldCreatePendingComment() {
        CommentDto created = commentService.create(AUTHOR_ID,
                new NewCommentDto(PUBLISHED_EVENT_ID, "Отличное событие"));

        assertNotNull(created.getId());
        assertEquals(CommentStatus.PENDING, created.getStatus());
        assertEquals("Иван", created.getAuthor().getName());
        assertEquals(0L, created.getLikes());
        assertNotNull(created.getCreatedOn());
    }

    @Test
    void shouldResetModerationFieldsAfterUpdate() {
        Comment comment = saveComment(AUTHOR_ID, "Старый текст", CommentStatus.APPROVED);
        comment.setModerationReason("Всё хорошо");
        comment.setModeratedOn(LocalDateTime.now().minusHours(1));
        commentRepository.save(comment);

        CommentDto updated = commentService.update(AUTHOR_ID, comment.getId(),
                new UpdateCommentRequest("Новый текст"));

        assertEquals("Новый текст", updated.getText());
        assertEquals(CommentStatus.PENDING, updated.getStatus());
        assertNull(updated.getModerationReason());
        assertNull(updated.getModeratedOn());
        assertNotNull(updated.getUpdatedOn());
    }

    @Test
    void shouldCountLikesForEachComment() {
        Comment first = saveComment(AUTHOR_ID, "Первый", CommentStatus.APPROVED);
        Comment second = saveComment(AUTHOR_ID, "Второй", CommentStatus.APPROVED);

        commentService.like(AUTHOR_ID, first.getId());
        commentService.like(OTHER_USER_ID, first.getId());
        commentService.like(OTHER_USER_ID, second.getId());

        List<CommentDto> own = commentService.findOwn(AUTHOR_ID, 0, 10);
        Map<String, Long> likesByText = own.stream()
                .collect(Collectors.toMap(CommentDto::getText, CommentDto::getLikes));

        assertEquals(2, own.size());
        assertEquals(2L, likesByText.get("Первый"));
        assertEquals(1L, likesByText.get("Второй"));
    }

    @Test
    void shouldRemoveLikesWithComment() {
        Comment comment = saveComment(AUTHOR_ID, "С лайком", CommentStatus.APPROVED);
        commentService.like(OTHER_USER_ID, comment.getId());

        commentService.delete(AUTHOR_ID, comment.getId());

        assertEquals(0, commentLikeRepository.count());
        assertTrue(commentRepository.findById(comment.getId()).isEmpty());
    }

    @Test
    void shouldReturnOnlyApprovedComments() {
        saveComment(AUTHOR_ID, "Одобрен", CommentStatus.APPROVED);
        saveComment(AUTHOR_ID, "На модерации", CommentStatus.PENDING);

        List<CommentDto> comments = commentService.findByEvent(PUBLISHED_EVENT_ID, 0, 10);

        assertEquals(1, comments.size());
        assertEquals("Одобрен", comments.get(0).getText());
    }

    @Test
    void shouldThrowForbiddenForForeignComment() {
        Comment comment = saveComment(AUTHOR_ID, "Чужой", CommentStatus.APPROVED);

        assertThrows(ForbiddenException.class, () -> commentService.update(OTHER_USER_ID, comment.getId(),
                new UpdateCommentRequest("Подмена")));
        assertThrows(ForbiddenException.class, () -> commentService.delete(OTHER_USER_ID, comment.getId()));
    }

    @Test
    void shouldThrowConflictWhenEventIsNotPublished() {
        assertThrows(ConflictException.class, () -> commentService.create(AUTHOR_ID,
                new NewCommentDto(PENDING_EVENT_ID, "Рано писать")));
    }

    @Test
    void shouldThrowNotFoundForMissingEvent() {
        assertThrows(NotFoundException.class, () -> commentService.findByEvent(999L, 0, 10));
    }

    @Test
    void shouldRejectCommentAndHideItFromPublicList() {
        Comment comment = saveComment(AUTHOR_ID, "Спам", CommentStatus.PENDING);

        CommentDto moderated = commentService.moderate(comment.getId(),
                new ModerationRequest(ModerationDecision.REJECTED, "Реклама"));

        assertEquals(CommentStatus.REJECTED, moderated.getStatus());
        assertTrue(commentService.findByEvent(PUBLISHED_EVENT_ID, 0, 10).isEmpty());
    }
}
