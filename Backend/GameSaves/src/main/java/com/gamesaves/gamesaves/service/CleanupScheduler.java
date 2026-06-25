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

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled cleanup of failed/stale article uploads.
 *
 * <p>Batch-processes FAILED and stale UPLOADING articles to prevent storage
 * exhaustion from orphaned files. Each batch runs in an independent
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
    private final StorageService storageService;

    // ── Configuration ─────────────────────────────────────────────────

    @Value("${app.cleanup.retention-hours:3}")
    private int retentionHours;

    @Value("${app.cleanup.batch-size:50}")
    private int batchSize;

    @Value("${app.cleanup.batch-delay-ms:2000}")
    private int batchDelayMs;

    @Value("${app.cleanup.delete-physical-files:true}")
    private boolean deletePhysicalFiles;

    // ── Progress tracking (read by status endpoint) ───────────────────

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger batchesCompleted = new AtomicInteger(0);
    private final AtomicInteger totalDeleted = new AtomicInteger(0);
    private volatile LocalDateTime lastRunAt = null;
    private volatile long lastRunDurationMs = 0;
    private volatile String lastRunMode = null;

    public CleanupScheduler(ArticleRepository articleRepository,
                            PlatformTransactionManager transactionManager,
                            StorageService storageService) {
        this.articleRepository = articleRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.storageService = storageService;
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
     */
    private int executeBatch(String mode) {
        return transactionTemplate.execute(status -> {
            List<Article> batch = findStaleBatch(mode);

            for (Article article : batch) {
                // 1. Delete physical files via storage service
                if (deletePhysicalFiles) {
                    String prefix = storageService.articleKey(
                            article.getUser().getId(), article.getGame().getId(),
                            article.getId(), "");
                    storageService.deleteDirectory(prefix);
                }

                // 2. Delete DB record
                articleRepository.delete(article);
            }

            return batch.size();
        });
    }

    /**
     * Fetches one batch of stale articles.
     */
    private List<Article> findStaleBatch(String mode) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);

        if ("failed-only".equals(mode)) {
            return articleRepository.findStaleByStatus(
                    Article.ArticleStatus.FAILED, cutoff,
                    PageRequest.of(0, batchSize));
        }

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

    // ── Progress DTO ──────────────────────────────────────────────────

    public record CleanupProgress(
            boolean completed,
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
