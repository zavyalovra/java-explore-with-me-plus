package ru.practicum.ewm.comment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.comment.model.CommentLike;
import ru.practicum.ewm.comment.model.CommentLikeId;

import java.util.Collection;
import java.util.List;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {

    long countById_CommentId(Long commentId);

    @Query("select l.id.commentId as commentId, count(l) as likes from CommentLike l "
            + "where l.id.commentId in :commentIds group by l.id.commentId")
    List<CommentLikeCount> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    @Modifying
    @Query("delete from CommentLike l where l.id.commentId = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);
}
