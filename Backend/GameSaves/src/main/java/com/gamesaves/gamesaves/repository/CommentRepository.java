package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Comments with user nickname, ordered by time
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.articleId = :articleId ORDER BY c.createdAt ASC")
    List<Comment> findByArticleIdWithUser(@Param("articleId") Long articleId);

    List<Comment> findByUserId(Long userId);

    long countByArticleId(Long articleId);

    void deleteByArticleId(Long articleId);

    void deleteByParentId(Long parentId);

    boolean existsById(Long id);
}
