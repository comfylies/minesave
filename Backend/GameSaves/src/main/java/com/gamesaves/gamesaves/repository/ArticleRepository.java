package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    // Get article with user, game, and tags for detail
    @Query("SELECT DISTINCT a FROM Article a " +
           "JOIN FETCH a.user " +
           "JOIN FETCH a.game " +
           "LEFT JOIN FETCH a.tags " +
           "WHERE a.id = :id")
    Optional<Article> findByIdWithUserAndGame(@Param("id") Long id);

    // Admin: search articles by keyword (title or username)
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game WHERE a.title LIKE %:keyword% OR a.user.username LIKE %:keyword%")
    Page<Article> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Admin: filter articles by status (with tags for search indexing)
    @Query("SELECT DISTINCT a FROM Article a " +
           "JOIN FETCH a.user JOIN FETCH a.game " +
           "LEFT JOIN FETCH a.tags " +
           "WHERE a.status = :status")
    Page<Article> findByStatusWithDetails(@Param("status") Article.ArticleStatus status, Pageable pageable);

    // Admin: all articles with user and game eager loaded
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game")
    Page<Article> findAllWithDetails(Pageable pageable);

    // ── Admin: game listing (batch-optimized) ───────────────────────────

    /** Batch: for each gameId, get the earliest READY article with a cover image. */
    @Query("SELECT a FROM Article a WHERE a.game.id IN :gameIds AND a.status = :status AND a.coverImage IS NOT NULL AND a.id IN " +
           "(SELECT MIN(a2.id) FROM Article a2 WHERE a2.game.id = a.game.id AND a2.status = :status AND a2.coverImage IS NOT NULL)")
    List<Article> findEarliestReadyWithCoverByGameIds(@Param("gameIds") List<Long> gameIds,
                                                       @Param("status") Article.ArticleStatus status);

    /** Batch: count READY articles per game. Returns Object[]{gameId, count}. */
    @Query("SELECT a.game.id, COUNT(a) FROM Article a WHERE a.game.id IN :gameIds AND a.status = :status GROUP BY a.game.id")
    List<Object[]> countReadyByGameIds(@Param("gameIds") List<Long> gameIds,
                                        @Param("status") Article.ArticleStatus status);

    // Legacy: single-game query kept for backward compatibility
    /** Find the earliest READY article with a cover image for a game (for thumbnail URL). */
    @Query("SELECT a FROM Article a WHERE a.game.id = :gameId AND a.status = :status AND a.coverImage IS NOT NULL ORDER BY a.createdAt ASC")
    List<Article> findEarliestReadyWithCover(@Param("gameId") Long gameId,
                                             @Param("status") Article.ArticleStatus status,
                                             Pageable pageable);

    // ── Admin: game merge ──────────────────────────────────────────────

    /** Bulk-migrate articles from one game to another (admin merge). */
    @Modifying
    @Query("UPDATE Article a SET a.game.id = :targetId WHERE a.game.id = :sourceId")
    int updateGameId(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    // ── Utility (testing / admin) ─────────────────────────────────────

    /** Directly update updatedAt timestamp, bypassing @PreUpdate. */
    @Modifying
    @Query("UPDATE Article a SET a.updatedAt = :updatedAt WHERE a.id = :id")
    void setUpdatedAt(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    // ── Cleanup queries ───────────────────────────────────────────────

    /** Find stale articles with a single status (for failed-only mode). */
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game " +
           "WHERE a.status = :status AND a.updatedAt < :cutoff " +
           "ORDER BY a.updatedAt ASC")
    List<Article> findStaleByStatus(@Param("status") Article.ArticleStatus status,
                                    @Param("cutoff") LocalDateTime cutoff,
                                    Pageable pageable);

    /** Find stale FAILED + UPLOADING articles (for "all" mode). */
    @Query("SELECT a FROM Article a JOIN FETCH a.user JOIN FETCH a.game " +
           "WHERE a.status IN (com.gamesaves.gamesaves.entity.Article.ArticleStatus.FAILED, " +
           "com.gamesaves.gamesaves.entity.Article.ArticleStatus.UPLOADING) " +
           "AND a.updatedAt < :cutoff " +
           "ORDER BY a.updatedAt ASC")
    List<Article> findStaleFailedOrUploading(@Param("cutoff") LocalDateTime cutoff,
                                             Pageable pageable);

    /** Count stale articles with a single status. */
    @Query("SELECT COUNT(a) FROM Article a " +
           "WHERE a.status = :status AND a.updatedAt < :cutoff")
    long countStaleByStatus(@Param("status") Article.ArticleStatus status,
                            @Param("cutoff") LocalDateTime cutoff);

    /** Count stale FAILED + UPLOADING articles. */
    @Query("SELECT COUNT(a) FROM Article a " +
           "WHERE a.status IN (com.gamesaves.gamesaves.entity.Article.ArticleStatus.FAILED, " +
           "com.gamesaves.gamesaves.entity.Article.ArticleStatus.UPLOADING) " +
           "AND a.updatedAt < :cutoff")
    long countStaleFailedOrUploading(@Param("cutoff") LocalDateTime cutoff);

    // ── Cascade delete / admin ─────────────────────────────────────────

    /** Find all articles for a game (any status). */
    @Query("SELECT a FROM Article a WHERE a.game.id = :gameId")
    List<Article> findAllByGameId(@Param("gameId") Long gameId);

    /** Count articles by status (for dashboard). */
    long countByStatus(Article.ArticleStatus status);
}
