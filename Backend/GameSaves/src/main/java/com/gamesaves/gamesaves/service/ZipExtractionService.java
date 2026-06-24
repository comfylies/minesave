package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Savings;
import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.ExtractionTimeoutException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.SavingItemRepository;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.util.ImageThumbnailService;
import com.gamesaves.gamesaves.util.MarkdownRenderer;
import com.gamesaves.gamesaves.util.ZipExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

@Service
public class ZipExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractionService.class);

    private final ArticleRepository articleRepository;
    private final SavingsRepository savingsRepository;
    private final SavingItemRepository savingItemRepository;
    private final SearchSyncService searchSyncService;

    @Value("${app.extraction.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${app.storage.database-path:../../Database}")
    private String databasePathConfig;

    private Path storageBasePath;

    @PostConstruct
    public void init() {
        storageBasePath = Paths.get(databasePathConfig).toAbsolutePath().normalize();
        log.info("ZipExtraction storage base path: {}", storageBasePath);
    }

    public ZipExtractionService(ArticleRepository articleRepository,
                                SavingsRepository savingsRepository,
                                SavingItemRepository savingItemRepository,
                                SearchSyncService searchSyncService) {
        this.articleRepository = articleRepository;
        this.savingsRepository = savingsRepository;
        this.savingItemRepository = savingItemRepository;
        this.searchSyncService = searchSyncService;
    }

    /**
     * Async ZIP extraction with timeout.
     * The MultipartFile should already be saved to disk before calling this.
     */
    @Async("extractionExecutor")
    public CompletableFuture<Void> extractAsync(Long articleId) {
        // Run extraction directly on the extraction executor thread
        // (not via CompletableFuture.runAsync which uses ForkJoinPool).
        // Called from TransactionSynchronization.afterCommit(), so article is visible.
        try {
            extract(articleId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Extraction failed for article {}", articleId, e);
            markFailed(articleId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private void extract(Long articleId) throws Exception {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));

        // Set status to EXTRACTING
        updateArticleStatus(articleId, Article.ArticleStatus.EXTRACTING);

        Path storageRoot = storageBasePath
                .resolve(String.valueOf(article.getUser().getId()))
                .resolve(String.valueOf(article.getGame().getId()))
                .resolve(String.valueOf(articleId));
        Path extractRoot = storageRoot.resolve("extracted");

        // Create directories
        Files.createDirectories(extractRoot);

        // Create savings record (we need the ID for saving_items)
        Savings savings = Savings.builder()
                .articleId(articleId)
                .userId(article.getUser().getId())
                .gameId(article.getGame().getId())
                .zipPath(storageRoot.resolve(article.getZipFilename()).toString().replace("\\", "/"))
                .extractRoot(extractRoot.toString().replace("\\", "/"))
                .zipHash("pending")
                .fileCount(0)
                .totalSize(0L)
                .build();
        savings = savingsRepository.save(savings);

        // Run extraction
        Path zipPath = storageRoot.resolve(article.getZipFilename());
        ZipExtractor extractor = new ZipExtractor(zipPath, extractRoot, savings.getId());
        ZipExtractor.ExtractionResult result = extractor.extract();

        // Save all file/directory entries in batch
        List<SavingItem> items = result.getItems();
        if (!items.isEmpty()) {
            savingItemRepository.saveAll(items);
        }
        log.info("Saved {} saving_items for article {}", items.size(), articleId);

        // ---- 新建 readme/ 目录结构 ----
        Path readmeDir = storageRoot.resolve("readme");
        Path readmeImagesDir = readmeDir.resolve("images");
        Files.createDirectories(readmeImagesDir);

        // 写入 README.md 到 readme/ 目录
        String readmeRawFromZip = null;
        if (result.getReadmeContents() != null && !result.getReadmeContents().isEmpty()) {
            readmeRawFromZip = result.getReadmeContents().get(0);
        }
        if (readmeRawFromZip != null && !readmeRawFromZip.isBlank()) {
            Files.writeString(readmeDir.resolve("README.md"), readmeRawFromZip,
                    java.nio.charset.StandardCharsets.UTF_8);
        }

        // 写入 images 到 readme/images/
        if (result.getReadmeImages() != null) {
            for (ZipExtractor.ReadmeImageEntry img : result.getReadmeImages()) {
                Path imgPath = readmeImagesDir.resolve(img.getRelativePath());
                Files.createDirectories(imgPath.getParent());
                Files.write(imgPath, img.getData());

                // Generate 720-wide proportional thumbnail for README images
                try {
                    ImageThumbnailService.generateReadmeThumbnail(imgPath);
                } catch (Exception e) {
                    log.warn("Failed to generate README thumbnail for {}: {}",
                            img.getRelativePath(), e.getMessage());
                }
            }
            log.info("Extracted {} readme images to {}", result.getReadmeImages().size(), readmeImagesDir);
        }

        // Update savings record
        savings.setZipHash(result.getZipHash());
        savings.setFileManifestHash(result.getFileManifestHash());
        savings.setFileCount(result.getFileCount());
        savings.setTotalSize(result.getTotalSize());
        savingsRepository.save(savings);

        // Update article: render README
        String finalReadmeRaw = article.getReadmeRaw();
        String finalReadmeContent;
        if (finalReadmeRaw != null && !finalReadmeRaw.isBlank()) {
            // User provided readme in the upload request
            finalReadmeContent = MarkdownRenderer.render(finalReadmeRaw);
        } else if (readmeRawFromZip != null && !readmeRawFromZip.isBlank()) {
            // README.md extracted from ZIP
            finalReadmeRaw = readmeRawFromZip;
            finalReadmeContent = MarkdownRenderer.render(readmeRawFromZip);
        } else {
            finalReadmeContent = null;
        }

        long fileSize = Files.size(zipPath);
        completeArticle(articleId, finalReadmeRaw, finalReadmeContent, fileSize);
        log.info("Article {} extraction complete: {} files, {} bytes",
                articleId, result.getFileCount(), result.getTotalSize());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateArticleStatus(Long articleId, Article.ArticleStatus status) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));
        article.setStatus(status);
        articleRepository.save(article);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeArticle(Long articleId, String readmeRaw, String readmeContent, long fileSize) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));
        article.setStatus(Article.ArticleStatus.READY);
        article.setReadmeRaw(readmeRaw);
        article.setReadmeContent(readmeContent);
        article.setFileSize(fileSize);
        article = articleRepository.save(article);
        log.info("Article {} completed: readmeRaw={} chars, readmeContent={} chars, fileSize={}",
                articleId,
                readmeRaw != null ? readmeRaw.length() : 0,
                readmeContent != null ? readmeContent.length() : 0,
                fileSize);

        // Index in Meilisearch
        searchSyncService.indexArticle(article);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long articleId, String errorMessage) {
        try {
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));
            article.setStatus(Article.ArticleStatus.FAILED);
            article.setErrorMessage(errorMessage != null && errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000) : errorMessage);
            articleRepository.save(article);
        } catch (Exception e) {
            log.error("Failed to mark article {} as FAILED", articleId, e);
        }
    }
}
