package ru.practicum.ewm.comment.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.user.dto.UserShortDto;

import java.util.List;
import java.util.Map;

@Component
public class CommentMapper {

    public CommentDto toDto(Comment comment, UserShortDto author, long likes) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .author(author)
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
                                      Map<Long, String> authorNames,
                                      Map<Long, Long> likes) {
        return comments.stream()
                .map(comment -> toDto(comment, toAuthor(comment, authorNames),
                        likes.getOrDefault(comment.getId(), 0L)))
                .toList();
    }

    private UserShortDto toAuthor(Comment comment, Map<Long, String> authorNames) {
        return UserShortDto.builder()
                .id(comment.getAuthorId())
                .name(authorNames.get(comment.getAuthorId()))
                .build();
    }
}
