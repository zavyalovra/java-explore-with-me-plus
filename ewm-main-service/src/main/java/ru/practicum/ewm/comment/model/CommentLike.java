package ru.practicum.ewm.comment.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment_likes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentLike {

    @EmbeddedId
    private CommentLikeId id;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;
}
