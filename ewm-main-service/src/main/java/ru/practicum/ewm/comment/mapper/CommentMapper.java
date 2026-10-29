package ru.practicum.ewm.comment.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.user.dto.UserShortDto;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommentMapper {

    private final UserMapper userMapper;

    public CommentDto toDto(Comment comment, Map<Long, User> authors, long likes) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .author(toAuthor(comment.getAuthorId(), authors))
                .eventId(comment.getEventId())
                .status(comment.getStatus())
                .createdOn(comment.getCreatedOn())
                .updatedOn(comment.getUpdatedOn())
                .moderatedOn(comment.getModeratedOn())
                .moderationReason(comment.getModerationReason())
                .likes(likes)
                .build();
    }

    public List<CommentDto> toDtoList(List<Comment> comments,
                                      Map<Long, User> authors,
                                      Map<Long, Long> likes) {
        return comments.stream()
                .map(comment -> toDto(comment, authors, likes.getOrDefault(comment.getId(), 0L)))
                .toList();
    }

    private UserShortDto toAuthor(Long authorId, Map<Long, User> authors) {
        User author = authors.get(authorId);
        return author == null
                ? UserShortDto.builder().id(authorId).build()
                : userMapper.toShortDto(author);
    }
}
