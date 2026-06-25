package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Savings;
import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.ExtractionTimeoutException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.SavingItemRepository;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.util.ImageThumbnailService;
import com.gamesaves.gamesaves.util.MagicNumberValidator;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class ZipExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractionService.class);

    private final ArticleRepository articleRepository;
    private final SavingsRepository savingsRepository;
    private final SavingItemRepository savingItemRepository;
    private final SearchSyncService searchSyncService;
    private final MagicNumberValidator magicNumberValidator;
    private final StorageService storageService;

    @Value("${app.extraction.timeout-seconds:30}")
    private int timeoutSeconds;

    public ZipExtractionService(ArticleRepository articleRepository,
                                SavingsRepository savingsRepository,
                                SavingItemRepository savingItemRepository,
                                SearchSyncService searchSyncService,
                                MagicNumberValidator magicNumberValidator,
                                StorageService storageService) {
        this.articleRepository = articleRepository;
        this.savingsRepository = savingsRepository;
        this.savingItemRepository = savingItemRepository;
        this.searchSyncService = searchSyncService;
        this.magicNumberValidator = magicNumberValidator;
        this.storageService = storageService;
    }

    /**
     * Async ZIP extraction with timeout.
     * The ZIP file must already be saved to storage before calling this.
     */
    @Async("extractionExecutor")
    public CompletableFuture<Void> extractAsync(Long articleId) {
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

        String cosPrefix = storageService.articleKey(
                article.getUser().getId(), article.getGame().getId(), articleId, "");

        // Get ZIP from storage — local mode returns direct path, COS mode downloads to temp
        String zipFilename = article.getZipFilename();
        Path tempDir = Files.createTempDirectory("extract-" + articleId + "-");
        try {
            // Get ZIP locally for extraction (ZIP must be a local file for Commons Compress)
            Optional<Path> localZipOpt = storageService.getLocalPath(cosPrefix + zipFilename);
            Path zipPath;
            if (localZipOpt.isPresent()) {
                zipPath = localZipOpt.get();
            } else {
                throw new RuntimeException("ZIP not found in storage: " + cosPrefix + zipFilename);
            }

            Path extractRoot = tempDir.resolve("extracted");
            Files.createDirectories(extractRoot);

            // Create savings record
            Savings savings = Savings.builder()
                    .articleId(articleId)
                    .userId(article.getUser().getId())
                    .gameId(article.getGame().getId())
                    .zipPath(cosPrefix + zipFilename)          // storage key, not local path
                    .extractRoot(cosPrefix + "extracted/")       // storage key prefix
                    .zipHash("pending")
                    .fileCount(0)
                    .totalSize(0L)
                    .build();
            savings = savingsRepository.save(savings);

            // Run extraction to temp directory
            ZipExtractor extractor = new ZipExtractor(zipPath, extractRoot, savings.getId(), magicNumberValidator);
            ZipExtractor.ExtractionResult result = extractor.extract();

            // Upload extracted files to storage
            List<SavingItem> items = result.getItems();
            for (SavingItem item : items) {
                if (!item.getIsDirectory()) {
                    Path localFile = extractRoot.resolve(item.getPhysicalKey());
                    if (Files.exists(localFile)) {
                        String fileKey = cosPrefix + "extracted/" + item.getPhysicalKey();
                        storageService.storeFromPath(fileKey, localFile);
                    }
                }
            }

            // Save all file/directory entries in batch
            if (!items.isEmpty()) {
                savingItemRepository.saveAll(items);
            }
            log.info("Saved {} saving_items for article {}", items.size(), articleId);

            // Write README.md to temp and upload
            String readmeRawFromZip = null;
            if (result.getReadmeContents() != null && !result.getReadmeContents().isEmpty()) {
                readmeRawFromZip = result.getReadmeContents().get(0);
            }
            if (readmeRawFromZip != null && !readmeRawFromZip.isBlank()) {
                Path readmePath = tempDir.resolve("README.md");
                Files.writeString(readmePath, readmeRawFromZip, java.nio.charset.StandardCharsets.UTF_8);
                storageService.storeFromPath(cosPrefix + "readme/README.md", readmePath);
            }

            // Upload README images
            if (result.getReadmeImages() != null) {
                Path imagesDir = tempDir.resolve("images");
                Files.createDirectories(imagesDir);
                for (ZipExtractor.ReadmeImageEntry img : result.getReadmeImages()) {
                    Path imgPath = imagesDir.resolve(img.getRelativePath());
                    Files.createDirectories(imgPath.getParent());
                    Files.write(imgPath, img.getData());

                    String imgKey = cosPrefix + "readme/images/" + img.getRelativePath();
                    storageService.storeFromPath(imgKey, imgPath);

                    // Generate 720-wide proportional thumbnail, upload to storage
                    try {
                        if (ImageThumbnailService.generateReadmeThumbnail(imgPath)) {
                            // Thumbnail is at {name}_thumb.jpg next to original
                            String origName = imgPath.getFileName().toString();
                            String base = origName.contains(".")
                                    ? origName.substring(0, origName.lastIndexOf('.'))
                                    : origName;
                            Path thumbPath = imgPath.resolveSibling(base + "_thumb.jpg");
                            if (Files.exists(thumbPath)) {
                                String parentDir = img.getRelativePath().contains("/")
                                        ? img.getRelativePath().substring(0, img.getRelativePath().lastIndexOf('/') + 1)
                                        : "";
                                String thumbKey = cosPrefix + "readme/images/" + parentDir + base + "_thumb.jpg";
                                storageService.storeFromPath(thumbKey, thumbPath);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to generate README thumbnail for {}: {}",
                                img.getRelativePath(), e.getMessage());
                    }
                }
                log.info("Extracted {} readme images to storage", result.getReadmeImages().size());
            }

            // Update savings record
            savings.setZipHash(result.getZipHash());
            savings.setFileManifestHash(result.getFileManifestHash());
            savings.setFileCount(result.getFileCount());
            savings.setTotalSize(result.getTotalSize());
            savingsRepository.save(savings);

            // Update article: store raw markdown only, frontend renders with marked (GFM)
            String finalReadmeRaw = article.getReadmeRaw();
            if (finalReadmeRaw == null || finalReadmeRaw.isBlank()) {
                finalReadmeRaw = readmeRawFromZip;
            }

            long fileSize = article.getFileSize();
            if (fileSize <= 0) {
                fileSize = Files.size(zipPath);
            }
            completeArticle(articleId, finalReadmeRaw, null, fileSize);
            log.info("Article {} extraction complete: {} files, {} bytes",
                    articleId, result.getFileCount(), result.getTotalSize());

        } finally {
            // Clean up temp directory
            try {
                deleteRecursively(tempDir);
            } catch (Exception e) {
                log.warn("Failed to clean temp directory {}: {}", tempDir, e.getMessage());
            }
        }
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
        log.info("Article {} completed: readmeRaw={} chars, fileSize={}",
                articleId,
                readmeRaw != null ? readmeRaw.length() : 0,
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

    private void deleteRecursively(Path path) throws java.io.IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
