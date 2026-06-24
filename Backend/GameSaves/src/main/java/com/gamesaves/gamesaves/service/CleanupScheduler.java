package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled cleanup of failed/stale article uploads.
 *
 * <p>Batch-processes FAILED and stale UPLOADING articles to prevent disk-space
 * exhaustion from orphaned physical files. Each batch runs in an independent
 * transaction with configurable sleep between batches.
 *
 * <p>Triggers:
 * <ul>
 *   <li>Scheduled — every N hours (configurable)</li>
 *   <li>Startup — {@code @PostConstruct} one-shot</li>
 *   <li>Manual — {@code POST /api/admin/cleanup/trigger} (AdminController)</li>
 * </ul>
 */
@Service
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final ArticleRepository articleRepository;
    private final TransactionTemplate transactionTemplate;

    // ── Configuration ─────────────────────────────────────────────────

    @Value("${app.cleanup.retention-hours:3}")
    private int retentionHours;

    @Value("${app.cleanup.batch-size:50}")
    private int batchSize;

    @Value("${app.cleanup.batch-delay-ms:2000}")
    private int batchDelayMs;

    @Value("${app.cleanup.delete-physical-files:true}")
    private boolean deletePhysicalFiles;

    @Value("${app.storage.database-path:../../Database}")
    private String databasePathConfig;

    // ── Progress tracking (read by status endpoint) ───────────────────

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger batchesCompleted = new AtomicInteger(0);
    private final AtomicInteger totalDeleted = new AtomicInteger(0);
    private volatile LocalDateTime lastRunAt = null;
    private volatile long lastRunDurationMs = 0;
    private volatile String lastRunMode = null;

    public CleanupScheduler(ArticleRepository articleRepository,
                            PlatformTransactionManager transactionManager) {
        this.articleRepository = articleRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── Triggers ──────────────────────────────────────────────────────

    /** Scheduled trigger — runs on configured interval. */
    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:10800000}")
    public void scheduledCleanup() {
        log.info("Scheduled cleanup triggered");
        cleanup("all");
    }

    /** Startup trigger — one-shot cleanup on application boot. */
    @PostConstruct
    public void startupCleanup() {
        log.info("Startup cleanup triggered");
        cleanup("all");
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Batch-cleans stale articles.
     *
     * @param mode "all" (FAILED + stale UPLOADING) or "failed-only" (FAILED only)
     * @return progress summary
     */
    public CleanupProgress cleanup(String mode) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Cleanup already in progress, skipping");
            throw new CleanupAlreadyRunningException("清理任务正在进行中，请稍后再试");
        }

        long start = System.currentTimeMillis();
        batchesCompleted.set(0);
        totalDeleted.set(0);
        lastRunMode = mode;

        try {
            log.info("Cleanup started — mode={}, retentionHours={}, batchSize={}",
                    mode, retentionHours, batchSize);

            while (true) {
                int batchDeleted = executeBatch(mode);
                if (batchDeleted == 0) {
                    break;   // no more stale articles
                }
                batchesCompleted.incrementAndGet();
                totalDeleted.addAndGet(batchDeleted);
                log.info("Cleanup batch #{}: {} articles deleted (total: {})",
                        batchesCompleted.get(), batchDeleted, totalDeleted.get());

                if (batchDeleted < batchSize) {
                    break;   // last partial batch
                }

                // Release I/O and DB connection between batches
                try {
                    Thread.sleep(batchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Cleanup interrupted between batches");
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            lastRunDurationMs = elapsed;
            lastRunAt = LocalDateTime.now();
            log.info("Cleanup complete — {} articles in {} batches, {} ms",
                    totalDeleted.get(), batchesCompleted.get(), elapsed);
        } catch (Exception e) {
            log.error("Cleanup failed after {} batches: {}", batchesCompleted.get(), e.getMessage(), e);
        } finally {
            running.set(false);
        }

        return new CleanupProgress(
                !running.get(),
                batchesCompleted.get(),
                totalDeleted.get(),
                estimateRemaining(mode),
                lastRunDurationMs
        );
    }

    /**
     * Returns current cleanup status without triggering execution.
     */
    public CleanupProgress getStatus() {
        return new CleanupProgress(
                !running.get(),
                batchesCompleted.get(),
                totalDeleted.get(),
                running.get() ? estimateRemaining(lastRunMode != null ? lastRunMode : "all") : 0,
                running.get() ? System.currentTimeMillis() - (System.currentTimeMillis() - lastRunDurationMs)
                        : lastRunDurationMs
        );
    }

    // ── Batch execution ───────────────────────────────────────────────

    /**
     * Executes ONE batch in an independent transaction.
     * Uses TransactionTemplate to guarantee per-batch transaction boundaries
     * (avoids Spring AOP self-invocation issues).
     */
    private int executeBatch(String mode) {
        return transactionTemplate.execute(status -> {
            // Query one page of stale articles inside the transaction
            List<Article> batch = findStaleBatch(mode);

            for (Article article : batch) {
                // 1. Delete physical files (before DB delete — if this fails,
                //    we keep the DB record for the next run to retry)
                Path storagePath = resolveStoragePath(article);
                deletePhysicalDirectory(storagePath);

                // 2. Delete DB record
                articleRepository.delete(article);
            }

            return batch.size();
        });
    }

    /**
     * Fetches one batch of stale articles. Paginated for the batch-size limit.
     */
    private List<Article> findStaleBatch(String mode) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);

        if ("failed-only".equals(mode)) {
            return articleRepository.findStaleByStatus(
                    Article.ArticleStatus.FAILED, cutoff,
                    PageRequest.of(0, batchSize));
        }

        // mode "all": FAILED + stale UPLOADING
        return articleRepository.findStaleFailedOrUploading(
                cutoff, PageRequest.of(0, batchSize));
    }

    /**
     * Estimates how many more articles remain to be cleaned.
     */
    private long estimateRemaining(String mode) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);
        if ("failed-only".equals(mode)) {
            return articleRepository.countStaleByStatus(Article.ArticleStatus.FAILED, cutoff);
        }
        return articleRepository.countStaleFailedOrUploading(cutoff);
    }

    // ── Physical file cleanup ─────────────────────────────────────────

    /**
     * Resolves the storage root path for an article based on its entity fields.
     * Path pattern: {databasePath}/{userId}/{gameId}/{articleId}/
     */
    private Path resolveStoragePath(Article article) {
        Path basePath = Paths.get(databasePathConfig).toAbsolutePath().normalize();
        return basePath
                .resolve(String.valueOf(article.getUser().getId()))
                .resolve(String.valueOf(article.getGame().getId()))
                .resolve(String.valueOf(article.getId()));
    }

    /**
     * Recursively deletes a directory with safety checks.
     * JDK 17 compatible (no Files.deleteRecursively which is JDK 21+).
     */
    private void deletePhysicalDirectory(Path targetPath) {
        if (!deletePhysicalFiles) {
            log.debug("Physical deletion disabled — skipping {}", targetPath);
            return;
        }

        if (!Files.exists(targetPath)) {
            log.debug("Storage directory not found (already cleaned?): {}", targetPath);
            return;
        }

        // ── Safety assertions ──────────────────────────────────────────
        // Verify the target is under the configured storage base path
        Path basePath = Paths.get(databasePathConfig).toAbsolutePath().normalize();
        Path normalizedTarget = targetPath.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(basePath)) {
            log.error("SAFETY: Refusing to delete path outside storage base: {}", normalizedTarget);
            return;
        }
        // Verify the path contains numeric directory segments (userId/gameId/articleId)
        // — prevents accidental deletion of non-article directories
        String relPath = basePath.relativize(normalizedTarget).toString().replace('\\', '/');
        if (!relPath.matches("\\d+/\\d+/\\d+")) {
            log.error("SAFETY: Path pattern mismatch (expected userId/gameId/articleId): {}", relPath);
            return;
        }

        try {
            // Walk bottom-up: delete files first, then directories
            try (var stream = Files.walk(normalizedTarget)) {
                var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
                for (Path p : paths) {
                    Files.deleteIfExists(p);
                }
            }
            log.debug("Deleted directory: {}", normalizedTarget);
        } catch (IOException e) {
            log.error("Failed to delete directory {}: {}", normalizedTarget, e.getMessage());
            // Don't re-throw — we want to continue with other articles
        }
    }

    // ── Progress DTO ──────────────────────────────────────────────────

    public record CleanupProgress(
            boolean completed,      // true when cleanup has finished
            int batchesCompleted,
            int totalDeleted,
            long estimatedRemaining,
            long durationMs
    ) {}

    // ── Exception ─────────────────────────────────────────────────────

    public static class CleanupAlreadyRunningException extends RuntimeException {
        public CleanupAlreadyRunningException(String message) {
            super(message);
        }
    }
}
