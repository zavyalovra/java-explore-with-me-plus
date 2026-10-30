package ru.practicum.ewm.comment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentRequest;
import ru.practicum.ewm.comment.service.CommentService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/comments")
@RequiredArgsConstructor
public class CommentPrivateController {

    private final CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto create(@PathVariable Long userId,
                             @Valid @RequestBody NewCommentDto newCommentDto) {
        return commentService.create(userId, newCommentDto);
    }

    @PatchMapping("/{commentId}")
    public CommentDto update(@PathVariable Long userId,
                             @PathVariable Long commentId,
                             @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(userId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long userId, @PathVariable Long commentId) {
        commentService.delete(userId, commentId);
    }

    @GetMapping
    public List<CommentDto> findOwn(@PathVariable Long userId,
                                    @RequestParam(defaultValue = "0") int from,
                                    @RequestParam(defaultValue = "10") int size) {
        return commentService.findOwn(userId, from, size);
    }

    @PostMapping("/{commentId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto like(@PathVariable Long userId, @PathVariable Long commentId) {
        return commentService.like(userId, commentId);
    }

    @DeleteMapping("/{commentId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@PathVariable Long userId, @PathVariable Long commentId) {
        commentService.unlike(userId, commentId);
    }
}
