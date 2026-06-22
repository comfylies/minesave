package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    // Browse: articles by game with READY status, newest first
    @Query("SELECT a FROM Article a JOIN FETCH a.user WHERE a.game.id = :gameId AND a.status = :status ORDER BY a.createdAt DESC")
    List<Article> findByGameIdAndStatusWithUser(@Param("gameId") Long gameId,
                                                 @Param("status") Article.ArticleStatus status,
                                                 Pageable pageable);

    // User's own articles
    @Query("SELECT a FROM Article a JOIN FETCH a.game WHERE a.user.id = :userId ORDER BY a.createdAt DESC")
    List<Article> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    // Status query for polling
    List<Article> findByStatus(Article.ArticleStatus status);

    // Count for pagination
    long countByGameIdAndStatus(Long gameId, Article.ArticleStatus status);

    long countByUserId(Long userId);

    // Get article with user for detail
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game WHERE a.id = :id")
    Optional<Article> findByIdWithUserAndGame(@Param("id") Long id);

    // Admin: search articles by keyword (title or username)
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game WHERE a.title LIKE %:keyword% OR a.user.username LIKE %:keyword%")
    Page<Article> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Admin: filter articles by status
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game WHERE a.status = :status")
    Page<Article> findByStatusWithDetails(@Param("status") Article.ArticleStatus status, Pageable pageable);

    // Admin: all articles with user and game eager loaded
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game")
    Page<Article> findAllWithDetails(Pageable pageable);
}
