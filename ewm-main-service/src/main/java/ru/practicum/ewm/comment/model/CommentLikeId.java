package ru.practicum.ewm.comment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CommentLikeId implements Serializable {

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "user_id")
    private Long userId;
}
