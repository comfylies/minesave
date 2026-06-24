package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "app.cleanup.interval-ms=31536000000",
        "app.cleanup.delete-physical-files=false",
        "app.cleanup.retention-hours=1",
        "app.cleanup.batch-size=10",
        "app.cleanup.batch-delay-ms=100"
})
class CleanupSchedulerTest {

    @Autowired
    private CleanupScheduler cleanupScheduler;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);

        // Clean up remnants from previous tests, then recreate test data.
        // All writes use explicit TransactionTemplate to avoid @Modifying proxy issues.
        tx.executeWithoutResult(status -> {
            // Remove any test data left over
            articleRepository.findAll().stream()
                    .filter(a -> a.getTitle() != null && a.getTitle().startsWith("__test_"))
                    .forEach(a -> articleRepository.delete(a));
            articleRepository.flush();

            User user = new User(); user.setId(1L);
            Game game = new Game(); game.setId(1L);
            LocalDateTime now = LocalDateTime.now();

            // Recent FAILED — within 1h retention, should NOT be cleaned
            articleRepository.save(Article.builder()
                    .title("__test_recent_failed").version("1.0")
                    .user(user).game(game)
                    .storageRoot("test/recent").zipFilename("recent.zip")
                    .status(Article.ArticleStatus.FAILED).errorMessage("err")
                    .build());

            // Stale FAILED — outside retention, SHOULD be cleaned
            Article sf = articleRepository.save(Article.builder()
                    .title("__test_stale_failed").version("1.0")
                    .user(user).game(game)
                    .storageRoot("test/sf").zipFilename("sf.zip")
                    .status(Article.ArticleStatus.FAILED).errorMessage("err")
                    .build());

            // Stale UPLOADING — outside retention, SHOULD be cleaned
            Article su = articleRepository.save(Article.builder()
                    .title("__test_stale_uploading").version("1.0")
                    .user(user).game(game)
                    .storageRoot("test/su").zipFilename("su.zip")
                    .status(Article.ArticleStatus.UPLOADING)
                    .build());

            // READY — should NEVER be cleaned
            articleRepository.save(Article.builder()
                    .title("__test_ready").version("1.0")
                    .user(user).game(game)
                    .storageRoot("test/ready").zipFilename("ready.zip")
                    .status(Article.ArticleStatus.READY)
                    .build());

            // Backdate the stale articles (must be in a transaction for @Modifying)
            articleRepository.setUpdatedAt(sf.getId(), now.minusHours(5));
            articleRepository.setUpdatedAt(su.getId(), now.minusHours(5));
        });
    }

    // ── Tests ─────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void status_shouldReturnInitialState() {
        var s = cleanupScheduler.getStatus();
        assertNotNull(s);
        assertTrue(s.completed());
    }

    @Test
    @Order(2)
    void cleanupAll_shouldDeleteStaleArticles() {
        var r = cleanupScheduler.cleanup("all");
        assertTrue(r.completed());
        assertTrue(r.totalDeleted() >= 2,
                "Expected >= 2 stale deletions, got " + r.totalDeleted());
    }

    @Test
    @Order(3)
    void afterCleanup_readyArticleRemains() {
        cleanupScheduler.cleanup("all");

        boolean hasReady = articleRepository.findAll().stream()
                .anyMatch(a -> "__test_ready".equals(a.getTitle()));
        assertTrue(hasReady, "READY should NEVER be deleted");
    }

    @Test
    @Order(4)
    void afterCleanup_recentFailedRemains() {
        cleanupScheduler.cleanup("all");

        boolean hasRecent = articleRepository.findAll().stream()
                .anyMatch(a -> "__test_recent_failed".equals(a.getTitle()));
        assertTrue(hasRecent, "Recent FAILED (within retention) should remain");
    }

    @Test
    @Order(5)
    void secondCleanup_shouldDeleteNothing() {
        cleanupScheduler.cleanup("all");
        var r = cleanupScheduler.cleanup("all");
        assertEquals(0, r.totalDeleted(), "Second pass should find nothing");
    }

    @Test
    @Order(6)
    void failedOnlyMode_shouldNotTouchUploading() {
        cleanupScheduler.cleanup("failed-only");

        boolean hasUploading = articleRepository.findAll().stream()
                .anyMatch(a -> "__test_stale_uploading".equals(a.getTitle()));
        assertTrue(hasUploading, "failed-only should NOT delete UPLOADING");
    }
}
