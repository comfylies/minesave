package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Savings;
import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.ExtractionTimeoutException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.SavingItemRepository;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.util.ArchiveExtractionResult;
import com.gamesaves.gamesaves.util.ArchiveFormat;
import com.gamesaves.gamesaves.util.ArchiveUtils;
import com.gamesaves.gamesaves.util.ImageThumbnailService;
import com.gamesaves.gamesaves.util.MagicNumberValidator;
import com.gamesaves.gamesaves.util.SevenZExtractor;
import com.gamesaves.gamesaves.util.TarArchiveExtractor;
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

/**
 * 压缩包提取服务 — 异步提取存档文件并上传到存储。
 *
 * <p>支持 ZIP / 7z / TAR / TAR.GZ 四种格式，通过 {@link ArchiveFormat} 自动检测并分发到对应提取器。
 * 提取限制从配置文件 {@code app.extraction.*} 读取，确保与上传预检阶段一致。
 */
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

    // 提取限制 — 从配置文件读取，与 ArticleServiceImpl 预检保持一致
    @Value("${app.extraction.max-entry-size:104857600}")
    private long maxEntrySize;

    @Value("${app.extraction.max-total-uncompressed-size:524288000}")
    private long maxTotalUncompressedSize;

    @Value("${app.extraction.max-entry-count:10000}")
    private int maxEntryCount;

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
     * 异步提取存档（带超时控制）。
     * 调用方通过 {@code TransactionSynchronization.afterCommit()} 触发，
     * 确保提取线程能看到已提交的 Article 记录。
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

    /**
     * 提取主流程：
     * 1. 从存储获取压缩包本地路径
     * 2. 检测格式 → 分发到对应提取器
     * 3. 并行上传提取的文件到存储
     * 4. 上传 README + README 图片
     * 5. 更新 Article 状态为 READY + Meilisearch 索引
     */
    private void extract(Long articleId) throws Exception {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));

        updateArticleStatus(articleId, Article.ArticleStatus.EXTRACTING);

        String cosPrefix = storageService.articleKey(
                article.getUser().getId(), article.getGame().getId(), articleId, "");

        String zipFilename = article.getZipFilename();
        Path tempDir = Files.createTempDirectory("extract-" + articleId + "-");
        try {
            // 从存储获取压缩包本地路径（local 模式返回原路径，COS 模式下载到临时目录）
            Optional<Path> localZipOpt = storageService.getLocalPath(cosPrefix + zipFilename);
            Path zipPath;
            if (localZipOpt.isPresent()) {
                zipPath = localZipOpt.get();
            } else {
                throw new RuntimeException("ZIP not found in storage: " + cosPrefix + zipFilename);
            }

            Path extractRoot = tempDir.resolve("extracted");
            Files.createDirectories(extractRoot);

            // 创建 Savings 快照记录
            Savings savings = Savings.builder()
                    .articleId(articleId)
                    .userId(article.getUser().getId())
                    .gameId(article.getGame().getId())
                    .zipPath(cosPrefix + zipFilename)          // storage key，非本地路径
                    .extractRoot(cosPrefix + "extracted/")     // storage key 前缀
                    .zipHash("pending")
                    .fileCount(0)
                    .totalSize(0L)
                    .build();
            savings = savingsRepository.save(savings);

            // ── 格式检测 + 分发提取 ──
            ArchiveFormat fmt = ArchiveFormat.detect(zipPath)
                    .orElseGet(() -> ArchiveFormat.detectByExtension(zipFilename)
                            .orElseThrow(() -> new RuntimeException(
                                    "Unrecognized archive format: " + zipFilename)));

            if (!fmt.isExtractionSupported()) {
                throw new RuntimeException(
                        fmt.name() + " format is not yet supported for extraction. "
                                + "Please convert to ZIP, 7z, or tar.gz.");
            }

            // 统一提取入口：所有提取器共享相同的限制参数（从配置文件注入）
            ArchiveExtractionResult.ExtractionResult result;
            switch (fmt) {
                case ZIP -> {
                    ZipExtractor extractor = new ZipExtractor(zipPath, extractRoot, savings.getId(),
                            magicNumberValidator, maxEntrySize, maxTotalUncompressedSize, maxEntryCount);
                    result = extractor.extract();
                }
                case SEVEN_Z -> {
                    SevenZExtractor extractor = new SevenZExtractor(zipPath, extractRoot, savings.getId(),
                            magicNumberValidator, maxEntrySize, maxTotalUncompressedSize, maxEntryCount);
                    result = extractor.extract();
                }
                case TAR_GZ, TAR -> {
                    TarArchiveExtractor extractor = new TarArchiveExtractor(zipPath, extractRoot, savings.getId(),
                            fmt, magicNumberValidator, maxEntrySize, maxTotalUncompressedSize, maxEntryCount);
                    result = extractor.extract();
                }
                default -> throw new RuntimeException("Unexpected archive format: " + fmt);
            }

            // 预创建 extracted/ 目录（单线程），防止 Windows NTFS 下并行 storeFromPath 竞态
            // Linux 不受影响，但一次调用成本为零
            storageService.store(cosPrefix + "extracted/.placeholder", new byte[0]);

            // ── 并行上传提取的文件到存储（COS: N 并发网络往返 vs 顺序单线程）──
            List<SavingItem> items = result.getItems();
            items.parallelStream().forEach(item -> {
                if (!item.getIsDirectory()) {
                    Path localFile = extractRoot.resolve(item.getPhysicalKey());
                    if (Files.exists(localFile)) {
                        String fileKey = cosPrefix + "extracted/" + item.getPhysicalKey();
                        storageService.storeFromPath(fileKey, localFile);
                    }
                }
            });

            // 批量写入 saving_items 数据库记录
            if (!items.isEmpty()) {
                savingItemRepository.saveAll(items);
            }
            log.info("Saved {} saving_items for article {}", items.size(), articleId);

            // ── 上传 README.md（从压缩包中提取的优先，用户手写的不覆盖）──
            String readmeRawFromZip = null;
            if (result.getReadmeContents() != null && !result.getReadmeContents().isEmpty()) {
                readmeRawFromZip = result.getReadmeContents().get(0);
            }
            if (readmeRawFromZip != null && !readmeRawFromZip.isBlank()) {
                Path readmePath = tempDir.resolve("README.md");
                Files.writeString(readmePath, readmeRawFromZip, java.nio.charset.StandardCharsets.UTF_8);
                storageService.storeFromPath(cosPrefix + "readme/README.md", readmePath);
            }

            // ── 上传 README 图片（含缩略图）──
            if (result.getReadmeImages() != null) {
                Path imagesDir = tempDir.resolve("images");
                Files.createDirectories(imagesDir);
                for (ArchiveExtractionResult.ReadmeImageEntry img : result.getReadmeImages()) {
                    Path imgPath = imagesDir.resolve(img.getRelativePath());
                    Files.createDirectories(imgPath.getParent());
                    Files.write(imgPath, img.getData());

                    String imgKey = cosPrefix + "readme/images/" + img.getRelativePath();
                    storageService.storeFromPath(imgKey, imgPath);

                    // 生成 720px 宽等比缩略图并上传
                    try {
                        if (ImageThumbnailService.generateReadmeThumbnail(imgPath)) {
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

            // 更新 Savings 快照统计
            savings.setZipHash(result.getZipHash());
            savings.setFileManifestHash(result.getFileManifestHash());
            savings.setFileCount(result.getFileCount());
            savings.setTotalSize(result.getTotalSize());
            savingsRepository.save(savings);

            // 更新 Article：优先使用用户手写 README，否则用压缩包内提取的内容
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
            // 清理临时目录
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

        // 同步到 Meilisearch 搜索索引
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
