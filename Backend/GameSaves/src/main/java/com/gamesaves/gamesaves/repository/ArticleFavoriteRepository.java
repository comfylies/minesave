package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.ArticleFavorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleFavoriteRepository extends JpaRepository<ArticleFavorite, Long> {

    Optional<ArticleFavorite> findByArticleIdAndUserId(Long articleId, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT a FROM ArticleFavorite f JOIN Article a ON a.id = f.articleId " +
            "JOIN FETCH a.user JOIN FETCH a.game LEFT JOIN FETCH a.tags " +
            "WHERE f.userId = :userId ORDER BY f.createdAt DESC")
    List<Article> findFavoriteArticlesByUserId(@Param("userId") Long userId, Pageable pageable);
}
