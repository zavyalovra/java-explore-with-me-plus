package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.ModerationDecision;
import ru.practicum.ewm.comment.dto.ModerationRequest;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentRequest;
import ru.practicum.ewm.comment.mapper.CommentMapper;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.model.CommentLike;
import ru.practicum.ewm.comment.model.CommentLikeId;
import ru.practicum.ewm.comment.model.CommentStatus;
import ru.practicum.ewm.comment.repository.CommentLikeCount;
import ru.practicum.ewm.comment.repository.CommentLikeRepository;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.ForbiddenException;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdOn")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentMapper commentMapper;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    public CommentDto create(Long userId, NewCommentDto newCommentDto) {
        requireUser(userId);
        Event event = eventRepository.findById(newCommentDto.getEventId())
                .orElseThrow(() -> new NotFoundException("Event with id=" + newCommentDto.getEventId()
                        + " was not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Event with id=" + newCommentDto.getEventId() + " is not published");
        }

        Comment comment = Comment.builder()
                .eventId(newCommentDto.getEventId())
                .authorId(userId)
                .text(newCommentDto.getText())
                .status(CommentStatus.PENDING)
                .createdOn(LocalDateTime.now())
                .build();

        return toDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto update(Long userId, Long commentId, UpdateCommentRequest request) {
        Comment comment = findComment(commentId);
        requireAuthor(comment, userId);

        comment.setText(request.getText());
        comment.setStatus(CommentStatus.PENDING);
        comment.setModerationReason(null);
        comment.setModeratedOn(null);
        comment.setUpdatedOn(LocalDateTime.now());

        return toDto(comment);
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        Comment comment = findComment(commentId);
        requireAuthor(comment, userId);
        removeComment(comment);
    }

    @Transactional
    public void deleteByAdmin(Long commentId) {
        removeComment(findComment(commentId));
    }

    public List<CommentDto> findOwn(Long userId, int from, int size) {
        validatePagination(from, size);
        requireUser(userId);

        Page<Comment> page = commentRepository.findAllByAuthorId(userId, toPageable(from, size));
        return toDtoList(page.getContent());
    }

    public List<CommentDto> findByEvent(Long eventId, int from, int size) {
        validatePagination(from, size);
        requireEvent(eventId);

        Page<Comment> page = commentRepository.findAllByEventIdAndStatus(
                eventId, CommentStatus.APPROVED, toPageable(from, size));
        return toDtoList(page.getContent());
    }

    public List<CommentDto> findForModeration(CommentStatus status, int from, int size) {
        validatePagination(from, size);

        Pageable pageable = toPageable(from, size);
        Page<Comment> page = status == null
                ? commentRepository.findAll(pageable)
                : commentRepository.findAllByStatus(status, pageable);

        return toDtoList(page.getContent());
    }

    @Transactional
    public CommentDto moderate(Long commentId, ModerationRequest request) {
        Comment comment = findComment(commentId);
        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Comment with id=" + commentId + " has already been moderated");
        }

        boolean rejected = request.getStatus() == ModerationDecision.REJECTED;
        if (rejected && isBlank(request.getModerationReason())) {
            throw new BadRequestException("Причина отклонения обязательна для статуса REJECTED");
        }

        comment.setStatus(CommentStatus.valueOf(request.getStatus().name()));
        comment.setModerationReason(rejected ? request.getModerationReason() : null);
        comment.setModeratedOn(LocalDateTime.now());

        return toDto(comment);
    }

    @Transactional
    public CommentDto like(Long userId, Long commentId) {
        Comment comment = findComment(commentId);
        requireUser(userId);

        if (comment.getStatus() != CommentStatus.APPROVED) {
            throw new ConflictException("Comment with id=" + commentId + " is not approved");
        }

        CommentLikeId likeId = new CommentLikeId(commentId, userId);
        if (commentLikeRepository.existsById(likeId)) {
            throw new ConflictException("User with id=" + userId + " has already liked comment with id=" + commentId);
        }

        commentLikeRepository.save(new CommentLike(likeId, LocalDateTime.now()));
        return toDto(comment);
    }

    @Transactional
    public void unlike(Long userId, Long commentId) {
        findComment(commentId);

        CommentLikeId likeId = new CommentLikeId(commentId, userId);
        if (!commentLikeRepository.existsById(likeId)) {
            throw new NotFoundException("Like of user with id=" + userId
                    + " for comment with id=" + commentId + " was not found");
        }

        commentLikeRepository.deleteById(likeId);
    }

    private void removeComment(Comment comment) {
        commentLikeRepository.deleteByCommentId(comment.getId());
        commentRepository.delete(comment);
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }

    private void requireEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
    }

    private void requireAuthor(Comment comment, Long userId) {
        if (!comment.getAuthorId().equals(userId)) {
            throw new ForbiddenException("User with id=" + userId
                    + " is not the author of comment with id=" + comment.getId());
        }
    }

    private CommentDto toDto(Comment comment) {
        long likes = commentLikeRepository.countById_CommentId(comment.getId());
        Map<Long, User> authors = findAuthors(Set.of(comment.getAuthorId()));
        return commentMapper.toDto(comment, authors, likes);
    }

    private List<CommentDto> toDtoList(List<Comment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }

        Set<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        List<Long> commentIds = comments.stream()
                .map(Comment::getId)
                .toList();

        Map<Long, User> authors = findAuthors(authorIds);
        Map<Long, Long> likes = commentLikeRepository.countByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(CommentLikeCount::getCommentId, CommentLikeCount::getLikes));

        return commentMapper.toDtoList(comments, authors, likes);
    }

    private Map<Long, User> findAuthors(Collection<Long> authorIds) {
        return userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private void validatePagination(int from, int size) {
        if (from < 0) {
            throw new BadRequestException("Параметр 'from' должен быть больше или равен 0");
        }
        if (size <= 0) {
            throw new BadRequestException("Параметр 'size' должен быть больше 0");
        }
    }

    private Pageable toPageable(int from, int size) {
        return PageRequest.of(from / size, size, DEFAULT_SORT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
