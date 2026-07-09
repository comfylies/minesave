package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.request.ArticleCreateRequest;
import com.gamesaves.gamesaves.dto.request.ArticleUpdateRequest;
import com.gamesaves.gamesaves.dto.response.ArticleDetailResponse;
import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.Savings;
import com.gamesaves.gamesaves.entity.Tag;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.repository.TagRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.ArticleService;
import com.gamesaves.gamesaves.service.SearchSyncService;
import com.gamesaves.gamesaves.service.StorageService;
import com.gamesaves.gamesaves.service.ZipExtractionService;
import com.gamesaves.gamesaves.util.ArchiveFormat;
import com.gamesaves.gamesaves.util.ImageThumbnailService;
import com.gamesaves.gamesaves.util.XssFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.gamesaves.gamesaves.util.PathTraversalValidator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ArticleServiceImpl implements ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleServiceImpl.class);

    private final ArticleRepository articleRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final SavingsRepository savingsRepository;
    private final TagRepository tagRepository;
    private final ZipExtractionService zipExtractionService;
    private final SearchSyncService searchSyncService;
    private final StorageService storageService;

    public ArticleServiceImpl(ArticleRepository articleRepository,
                               GameRepository gameRepository,
                               UserRepository userRepository,
                               SavingsRepository savingsRepository,
                               TagRepository tagRepository,
                               ZipExtractionService zipExtractionService,
                               SearchSyncService searchSyncService,
                               StorageService storageService) {
        this.articleRepository = articleRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.savingsRepository = savingsRepository;
        this.tagRepository = tagRepository;
        this.zipExtractionService = zipExtractionService;
        this.searchSyncService = searchSyncService;
        this.storageService = storageService;
    }

    @Override
    public ArticleDetailResponse createArticle(ArticleCreateRequest request, MultipartFile file,
                                                MultipartFile readmeFile, MultipartFile coverFile) {
        // 1. 基本校验
        Game game = gameRepository.findById(request.getGameId())
                .orElseThrow(() -> new ResourceNotFoundException("Game", request.getGameId()));
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("ZIP file is required");
        }

        // 2. README 解析（Priority: 手动输入 > 上传 .md > 从ZIP提取）
        String resolvedReadmeRaw = request.getReadmeRaw();
        if ((resolvedReadmeRaw == null || resolvedReadmeRaw.isBlank())
                && readmeFile != null && !readmeFile.isEmpty()) {
            try {
                resolvedReadmeRaw = new String(readmeFile.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                log.info("README loaded from uploaded file: {} ({} bytes)",
                        readmeFile.getOriginalFilename(), readmeFile.getSize());
            } catch (IOException e) {
                log.warn("Failed to read uploaded README file, will fall back to ZIP extraction", e);
            }
        }

        // 3. ZIP 本地预检（DB 写入和 COS 上传之前，本地完成）
        Path tempZip = null;
        long zipSize;
        try {
            tempZip = Files.createTempFile("upload-", ".tmp");
            file.transferTo(tempZip.toFile());
            zipSize = Files.size(tempZip);
            validateArchiveFile(tempZip, zipSize, file.getOriginalFilename());
        } catch (BadRequestException e) {
            // 预检失败 → 清理 temp 文件，直接返回错误（DB 和 COS 完全未动）
            cleanupTempFile(tempZip);
            throw e;
        } catch (IOException e) {
            cleanupTempFile(tempZip);
            throw new FileProcessingException("Failed to read uploaded ZIP: " + e.getMessage(), e);
        }

        // 4. 预检通过 → 创建 Article 记录
        final String finalReadmeRaw = resolvedReadmeRaw;
        String storagePrefix = user.getId() + "/" + game.getId() + "/";
        Article article = Article.builder()
                .title(XssFilter.sanitize(request.getTitle()))
                .version(XssFilter.sanitize(request.getVersion()))
                .game(game)
                .user(user)
                .description(request.getDescription() != null
                        ? XssFilter.sanitize(request.getDescription()) : null)
                .readmeRaw(finalReadmeRaw)
                .zipFilename(file.getOriginalFilename() != null
                        ? XssFilter.sanitize(file.getOriginalFilename()) : "archive.zip")
                .storageRoot(storagePrefix)      // placeholder, 拿到 ID 后更新
                .fileSize(zipSize)               // 预检时已获取
                .status(Article.ArticleStatus.UPLOADING)
                .build();

        article = articleRepository.save(article);

        // 拿到 ID → 设置真实的 storageRoot
        String storageRoot = storagePrefix + article.getId() + "/";
        article.setStorageRoot(storageRoot);

        // 关联标签
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            article.setTags(new HashSet<>(tags));
        }

        article = articleRepository.save(article);

        // 5. 上传 ZIP 到 COS（复用预检步骤落盘的 temp 文件，不重复写盘）
        try {
            String cosPrefix = storageService.articleKey(user.getId(), game.getId(), article.getId(), "");
            String zipKey = cosPrefix + article.getZipFilename();
            storageService.storeFromPath(zipKey, tempZip);
            log.info("Article {} created, ZIP uploaded to {}", article.getId(), zipKey);
        } catch (Exception e) {
            log.error("Failed to upload ZIP to COS for article {}: {}", article.getId(), e.getMessage());
            throw new FileProcessingException("Failed to save uploaded file: " + e.getMessage(), e);
        } finally {
            cleanupTempFile(tempZip);
        }

        // 6. 封面图处理（如果前端提供）
        if (coverFile != null && !coverFile.isEmpty()) {
            try {
                String coverExt = validateAndGetImageExtension(coverFile);
                Path tempCover = Files.createTempFile("cover-", "." + coverExt);
                try {
                    coverFile.transferTo(tempCover.toFile());

                    // 上传原图
                    String coverFilename = "cover." + coverExt;
                    String coverKey = storageService.articleKey(user.getId(), game.getId(), article.getId(), coverFilename);
                    storageService.storeFromPath(coverKey, tempCover);
                    article.setCoverImage(storageService.generatePresignedUrl(coverKey, 1440));

                    // 生成缩略图并上传
                    Path thumbDir = Files.createTempDirectory("thumbs-");
                    try {
                        ImageThumbnailService.generateCoverThumbnails(tempCover, thumbDir);
                        for (int size : new int[]{270, 360, 720}) {
                            Path thumbPath = thumbDir.resolve("cover_thumb_" + size + ".jpg");
                            if (Files.exists(thumbPath)) {
                                String thumbKey = storageService.articleKey(
                                        user.getId(), game.getId(), article.getId(), "cover_thumb_" + size + ".jpg");
                                storageService.storeFromPath(thumbKey, thumbPath);
                            }
                        }
                    } finally {
                        deleteRecursively(thumbDir);
                    }
                    // 360h 缩略图（按比例，高 360px）
                    Path thumb360h = ImageThumbnailService.generateThumbnail360h(tempCover);
                    if (thumb360h != null && Files.exists(thumb360h)) {
                        try {
                            String thumb360hKey = storageService.articleKey(
                                    user.getId(), game.getId(), article.getId(), "cover_thumb_360.jpg");
                            storageService.storeFromPath(thumb360hKey, thumb360h);
                        } finally {
                            Files.deleteIfExists(thumb360h);
                        }
                    }
                    log.info("Cover image & thumbnails uploaded for article {}: {}", article.getId(), coverFilename);
                } finally {
                    Files.deleteIfExists(tempCover);
                }
            } catch (BadRequestException e) {
                throw e;
            } catch (IOException e) {
                log.error("Failed to save cover image for article {}: {}", article.getId(), e.toString());
                throw new FileProcessingException("Failed to save cover image: " + e.getMessage(), e);
            }
        }

        article = articleRepository.save(article);

        // 7. 事务提交后触发异步提取
        final Long savedArticleId = article.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        zipExtractionService.extractAsync(savedArticleId);
                    }
                });

        return ArticleDetailResponse.fromEntity(article);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleDetailResponse getArticleDetail(Long id) {
        Article article = articleRepository.findByIdWithUserAndGame(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        Savings savings = savingsRepository.findByArticleId(id).orElse(null);
        return ArticleDetailResponse.fromEntity(article, savings);
    }

    @Override
    public ArticleDetailResponse updateArticle(Long id, ArticleUpdateRequest request) {
        Article article = articleRepository.findByIdWithUserAndGame(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        if (request.getTitle() != null) article.setTitle(XssFilter.sanitize(request.getTitle()));
        if (request.getVersion() != null) article.setVersion(XssFilter.sanitize(request.getVersion()));
        if (request.getDescription() != null) article.setDescription(XssFilter.sanitize(request.getDescription()));

        // Update tag associations if tagIds is provided
        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            article.setTags(new HashSet<>(tags));
        }

        article = articleRepository.save(article);
        return ArticleDetailResponse.fromEntity(article);
    }

    @Override
    public void deleteArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        // 使用 storageRoot 而非 article.game.id — 合并游戏后 gameId 会变但文件在原路径
        String prefix = storageService.articleKeyFromRoot(article.getStorageRoot(), "");
        storageService.deleteDirectory(prefix);

        // Remove from search index
        searchSyncService.deleteArticle(id);

        // DB cascade handles savings, saving_items, comments, download_logs
        articleRepository.delete(article);
        log.info("Article {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getArticleStatus(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        return Map.of(
                "status", article.getStatus().name(),
                "errorMessage", article.getErrorMessage() != null ? article.getErrorMessage() : ""
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ArticleListItemResponse> getArticlesByGame(Long gameId, int page, int size) {
        long total = articleRepository.countByGameIdAndStatus(gameId, Article.ArticleStatus.READY);
        List<Article> articles = articleRepository.findByGameIdAndStatusWithUser(
                gameId, Article.ArticleStatus.READY,
                PageRequest.of(page, size));

        List<ArticleListItemResponse> content = articles.stream()
                .map(ArticleListItemResponse::fromEntity)
                .collect(Collectors.toList());

        return PageDTO.of(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ArticleListItemResponse> getUserArticles(Long userId, int page, int size) {
        long total = articleRepository.countByUserId(userId);
        List<Article> articles = articleRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));

        List<ArticleListItemResponse> content = articles.stream()
                .map(ArticleListItemResponse::fromEntity)
                .collect(Collectors.toList());

        return PageDTO.of(content, page, size, total);
    }

    /**
     * Validate cover image by magic bytes and return the file extension.
     * Accepts PNG, JPEG, GIF, WebP. Throws BadRequestException for invalid images.
     */
    private String validateAndGetImageExtension(MultipartFile file) {
        try {
            byte[] header = new byte[12];
            int read;
            try (var in = file.getInputStream()) {
                read = in.read(header);
            }
            if (read < 4) {
                throw new BadRequestException("Cover image file is too small");
            }

            // PNG: 89 50 4E 47
            if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50
                    && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                return "png";
            }
            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8
                    && header[2] == (byte) 0xFF) {
                return "jpg";
            }
            // GIF: 47 49 46 38 (GIF8)
            if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49
                    && header[2] == (byte) 0x46 && header[3] == (byte) 0x38) {
                return "gif";
            }
            // WebP: 52 49 46 46 ... 57 45 42 50 (RIFF....WEBP)
            if (header[0] == (byte) 0x52 && header[1] == (byte) 0x49
                    && header[2] == (byte) 0x46 && header[3] == (byte) 0x46
                    && read >= 12
                    && header[8] == (byte) 0x57 && header[9] == (byte) 0x45
                    && header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
                return "webp";
            }

            throw new BadRequestException(
                    "Unsupported cover image format. Accepted: PNG, JPEG, GIF, WebP");
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Failed to read cover image: " + e.getMessage());
        }
    }

    /**
     * 本地压缩包预检（不访问网络，只读 temp 文件）。
     *
     * <p>支持 ZIP / 7z / TAR / TAR.GZ / RAR 格式检测。
     * RAR 不支持提取，检测到后返回友好错误提示。
     * 在 DB 写入和 COS 上传之前调用，拦截无效文件。
     */
    private void validateArchiveFile(Path tempZip, long zipSize, String originalFilename) {
        if (zipSize == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (zipSize > 500 * 1024 * 1024) {
            throw new BadRequestException(
                    "File too large (" + zipSize / 1024 / 1024 + "MB, max 500MB)");
        }

        // 格式检测（magic bytes 优先，扩展名回退）
        ArchiveFormat format;
        try {
            format = ArchiveFormat.detect(tempZip)
                    .orElseGet(() -> ArchiveFormat.detectByExtension(originalFilename)
                            .orElseThrow(() -> new BadRequestException(
                                    "Unrecognized file format. Supported: ZIP, 7z, tar.gz, tar")));
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Cannot read uploaded file: " + e.getMessage());
        }

        // RAR 不支持提取
        if (!format.isExtractionSupported()) {
            throw new BadRequestException(
                    "RAR format is not yet supported. Please convert to ZIP, 7z, or tar.gz.");
        }

        // ZIP 仍做完整的结构预检；7z/tar 的结构校验由提取阶段完成
        if (format == ArchiveFormat.ZIP) {
            validateZipStructure(tempZip, zipSize);
        } else {
            log.info("Archive pre-validation passed: format={}, size={} bytes", format, zipSize);
        }
    }

    /** ZIP 结构完整性预检（Commons Compress 打开 + 条目扫描） */
    private void validateZipStructure(Path tempZip, long zipSize) {
        try (ZipFile zipFile = ZipFile.builder().setPath(tempZip).get()) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

            if (!entries.hasMoreElements()) {
                throw new BadRequestException("ZIP archive is empty (no files inside)");
            }

            int count = 0;
            long totalUncompressed = 0;
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                count++;

                if (count > 10_000) {
                    throw new BadRequestException(
                            "Archive contains too many files (" + count + " so far, max 10,000)");
                }

                try {
                    PathTraversalValidator.validate(entry.getName());
                } catch (Exception e) {
                    throw new BadRequestException(
                            "Archive contains unsafe file path: " + entry.getName());
                }

                long size = entry.getSize();
                if (size > 100 * 1024 * 1024) {
                    throw new BadRequestException(
                            "Archive contains a file that is too large: " + entry.getName()
                                    + " (" + size / 1024 / 1024 + "MB, max 100MB per file)");
                }
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > 500 * 1024 * 1024) {
                        throw new BadRequestException(
                                "Archive total uncompressed size exceeds 500MB limit");
                    }
                }
            }

            log.info("ZIP pre-validation passed: {} entries, {} bytes uncompressed, {} bytes compressed",
                    count, totalUncompressed, zipSize);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException(
                    "Cannot open archive — file may be corrupted: " + e.getMessage());
        }
    }

    /** 安全清理 temp 文件，不抛异常 */
    private void cleanupTempFile(Path tempZip) {
        if (tempZip != null) {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException e) {
                log.warn("Failed to clean temp file {}: {}", tempZip, e.getMessage());
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
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
