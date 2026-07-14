package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.request.ArticleCreateRequest;
import com.gamesaves.gamesaves.dto.request.ArticleFullUpdateRequest;
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
import com.gamesaves.gamesaves.repository.CommentRepository;
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
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.gamesaves.gamesaves.util.PathTraversalValidator;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章服务实现。
 *
 * <p>核心流程 createArticle 采用「预检→落库→COS 上传→异步提取」分段策略：
 * 预检在本地 temp 文件上完成（不访问网络），失败时 DB 和 COS 完全未动。
 * 提取限制从配置文件 {@code app.extraction.*} 读取，确保预检与异步提取阶段一致。
 */
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
    private final CommentRepository commentRepository;

    // ── 提取限制（从配置文件注入，与 ZipExtractionService 保持一致）──
    @Value("${app.extraction.max-file-size:524288000}")
    private long maxFileSize;

    @Value("${app.extraction.max-entry-size:104857600}")
    private long maxEntrySize;

    @Value("${app.extraction.max-total-uncompressed-size:524288000}")
    private long maxTotalUncompressedSize;

    @Value("${app.extraction.max-entry-count:10000}")
    private int maxEntryCount;

    @Value("${app.edit.max-daily-edits:2}")
    private int maxDailyEdits;

    public ArticleServiceImpl(ArticleRepository articleRepository,
                               GameRepository gameRepository,
                               UserRepository userRepository,
                               SavingsRepository savingsRepository,
                               TagRepository tagRepository,
                               ZipExtractionService zipExtractionService,
                               SearchSyncService searchSyncService,
                               StorageService storageService,
                               CommentRepository commentRepository) {
        this.articleRepository = articleRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.savingsRepository = savingsRepository;
        this.tagRepository = tagRepository;
        this.zipExtractionService = zipExtractionService;
        this.searchSyncService = searchSyncService;
        this.storageService = storageService;
        this.commentRepository = commentRepository;
    }

    /**
     * 创建文章（存档上传）完整流程：
     *
     * <ol>
     *   <li>基本校验（Game/User 存在性）</li>
     *   <li>README 解析（手动输入 &gt; 上传 .md &gt; 从压缩包提取）</li>
     *   <li>压缩包本地预检（格式检测 + 结构扫描 + 路径穿越 + 大小限制）</li>
     *   <li>预检通过 → 创建 Article 记录（DB）</li>
     *   <li>上传压缩包到 COS（复用预检的 temp 文件）</li>
     *   <li>封面图处理（可选）</li>
     *   <li>事务提交后触发异步提取</li>
     * </ol>
     *
     * <p>temp 文件在整个流程中通过 try-finally 保证清理，包括 DB 异常路径。
     */
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

        // 2. README 解析（Priority: 手动输入 > 上传 .md > 从压缩包提取）
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

        // 3. 压缩包本地预检（DB 写入和 COS 上传之前，本地 temp 文件完成）
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
            throw new FileProcessingException("Failed to read uploaded file: " + e.getMessage(), e);
        }

        // ── try-finally 保证 tempZip 在任意异常路径下都能被清理 ──
        try {
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

            // 5. 上传压缩包到 COS（复用预检步骤落盘的 temp 文件，不重复写盘）
            try {
                String cosPrefix = storageService.articleKey(user.getId(), game.getId(), article.getId(), "");
                String zipKey = cosPrefix + article.getZipFilename();
                storageService.storeFromPath(zipKey, tempZip);
                log.info("Article {} created, ZIP uploaded to {}", article.getId(), zipKey);
            } catch (Exception e) {
                log.error("Failed to upload ZIP to COS for article {}: {}", article.getId(), e.getMessage());
                throw new FileProcessingException("Failed to save uploaded file: " + e.getMessage(), e);
            }

            // 6. 封面图处理（如果前端提供）
            if (coverFile != null && !coverFile.isEmpty()) {
                try {
                    String coverExt = validateAndGetImageExtension(coverFile);
                    Path tempCover = Files.createTempFile("cover-", "." + coverExt);
                    try {
                        coverFile.transferTo(tempCover.toFile());

                        // 上传原图，DB 存储 key（非预签名 URL，避免链接过期）
                        String coverFilename = "cover." + coverExt;
                        String coverKey = storageService.articleKey(user.getId(), game.getId(), article.getId(), coverFilename);
                        storageService.storeFromPath(coverKey, tempCover);
                        article.setCoverImage(coverKey);

                        // 生成缩略图并上传（270p/360p/720p）
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
            //    afterCommit 确保异步线程（独立线程池）能看到已提交的 Article 行
            final Long savedArticleId = article.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            zipExtractionService.extractAsync(savedArticleId);
                        }
                    });

            return ArticleDetailResponse.fromEntity(article);
        } finally {
            // 无论成功或失败（包括 DB 异常、COS 异常），确保 temp 文件被清理
            cleanupTempFile(tempZip);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleDetailResponse getArticleDetail(Long id) {
        Article article = articleRepository.findByIdWithUserAndGame(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        Savings savings = savingsRepository.findByArticleId(id).orElse(null);
        ArticleDetailResponse response = ArticleDetailResponse.fromEntity(article, savings);
        // 将 storage key 解析为可公开访问的 URL（附加 updatedAt 作为缓存破坏参数，
        // 避免封面/缩略图修改后浏览器仍显示缓存的旧图）
        String coverKey = article.getCoverImage();
        String cacheBuster = buildCacheBuster(article.getUpdatedAt());
        response.setCoverImage(appendCacheBuster(resolveCoverUrl(coverKey), cacheBuster));
        response.setCoverThumbnail(appendCacheBuster(resolveCoverThumbnailUrl(coverKey, 360), cacheBuster));
        response.setCoverThumbnail720(appendCacheBuster(resolveCoverThumbnailUrl(coverKey, 720), cacheBuster));
        return response;
    }

    @Override
    public ArticleDetailResponse updateArticle(Long id, ArticleUpdateRequest request) {
        Article article = articleRepository.findByIdWithUserAndGame(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        if (request.getTitle() != null) article.setTitle(XssFilter.sanitize(request.getTitle()));
        if (request.getVersion() != null) article.setVersion(XssFilter.sanitize(request.getVersion()));
        if (request.getDescription() != null) article.setDescription(XssFilter.sanitize(request.getDescription()));

        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            article.setTags(new HashSet<>(tags));
        }

        article = articleRepository.save(article);
        return ArticleDetailResponse.fromEntity(article);
    }

    /**
     * 完整编辑文章 — 支持 README 文件替换、封面图替换、每日编辑次数限制。
     *
     * <ol>
     *   <li>所有权校验 — 只有文章作者本人可编辑</li>
     *   <li>状态检查 — 只有 READY 状态可编辑</li>
     *   <li>每日次数限制 — 每天最多 {@link #maxDailyEdits} 次</li>
     *   <li>文本字段更新 — title, version, description</li>
     *   <li>标签更新 — 如果提供了 tagIds</li>
     *   <li>README 更新 — 文件上传模式替换内容并删除旧批注；手写模式只替换内容</li>
     *   <li>封面替换 — 先上传新封面再删旧的（避免上传失败丢封面）</li>
     * </ol>
     */
    @Override
    public ArticleDetailResponse updateArticleFull(Long id, ArticleFullUpdateRequest request,
                                                    MultipartFile readmeFile, MultipartFile coverFile) {
        // 1. 查询文章
        Article article = articleRepository.findByIdWithUserAndGame(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        // 2. 所有权校验
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!article.getUser().getId().equals(currentUserId)) {
            throw new BadRequestException("只能编辑自己的存档");
        }

        // 3. 状态检查 — 只有 READY 状态可编辑
        if (article.getStatus() != Article.ArticleStatus.READY) {
            throw new BadRequestException("存档正在处理中，请等待处理完成后再编辑");
        }

        // 4. 每日编辑次数限制
        checkAndIncrementEditCount(article);

        // 5. 更新文本字段
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            article.setTitle(XssFilter.sanitize(request.getTitle()));
        }
        if (request.getVersion() != null && !request.getVersion().isBlank()) {
            article.setVersion(XssFilter.sanitize(request.getVersion()));
        }
        if (request.getDescription() != null) {
            article.setDescription(XssFilter.sanitize(request.getDescription()));
        }

        // 6. 更新标签
        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            article.setTags(new HashSet<>(tags));
        }

        // 7. README 更新
        boolean readmeReplaced = false;
        if (readmeFile != null && !readmeFile.isEmpty()) {
            // 上传文件模式 — 完全替换 README，删除旧批注
            try {
                String newReadme = new String(readmeFile.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                article.setReadmeRaw(newReadme);
                article.setReadmeContent(null); // 前端用 marked 实时渲染，不需要服务端 HTML
                readmeReplaced = true;
                log.info("Article {} README replaced via file upload ({} bytes)", id, readmeFile.getSize());
            } catch (IOException e) {
                throw new FileProcessingException("无法读取上传的 README 文件: " + e.getMessage(), e);
            }
        } else if (request.getReadmeRaw() != null) {
            // 手写模式 — 只替换内容，不删除批注
            article.setReadmeRaw(request.getReadmeRaw());
            article.setReadmeContent(null);
        }

        // 如果 README 被文件完全替换，删除旧批注
        if (readmeReplaced) {
            long deletedComments = commentRepository.countByArticleId(id);
            commentRepository.deleteByArticleId(id);
            log.info("Article {} README replaced — {} existing comments deleted", id, deletedComments);
        }

        // 8. 封面替换
        log.info("Article {} edit — coverFile present: {}, isEmpty: {}, size: {}, originalFilename: {}",
                id, coverFile != null, coverFile != null ? coverFile.isEmpty() : "N/A",
                coverFile != null ? coverFile.getSize() : "N/A",
                coverFile != null ? coverFile.getOriginalFilename() : "N/A");
        if (coverFile != null && !coverFile.isEmpty()) {
            log.info("Article {} edit — replacing cover image...", id);
            replaceCoverImage(article, coverFile);
        } else {
            log.info("Article {} edit — no coverFile, skipping cover replacement", id);
        }

        // 9. 保存
        article = articleRepository.save(article);

        // 10. 同步搜索索引
        try {
            searchSyncService.indexArticle(article);
        } catch (Exception e) {
            log.warn("Failed to re-index article {} after edit: {}", id, e.getMessage());
        }

        // 11. 构建响应（需要解析 cover URL，附加缓存破坏参数避免浏览器缓存旧图）
        Savings savings = savingsRepository.findByArticleId(id).orElse(null);
        ArticleDetailResponse response = ArticleDetailResponse.fromEntity(article, savings);
        String coverKey = article.getCoverImage();
        String cacheBuster = buildCacheBuster(article.getUpdatedAt());
        response.setCoverImage(appendCacheBuster(resolveCoverUrl(coverKey), cacheBuster));
        response.setCoverThumbnail(appendCacheBuster(resolveCoverThumbnailUrl(coverKey, 360), cacheBuster));
        response.setCoverThumbnail720(appendCacheBuster(resolveCoverThumbnailUrl(coverKey, 720), cacheBuster));
        return response;
    }

    /**
     * 检查并递增每日编辑次数。
     * 如果上次编辑日期不是今天，重置计数器；如果已达上限则拒绝。
     */
    private void checkAndIncrementEditCount(Article article) {
        LocalDate today = LocalDate.now();
        if (article.getLastEditDate() == null || !article.getLastEditDate().equals(today)) {
            article.setLastEditDate(today);
            article.setDailyEditCount(1);
            return;
        }
        if (article.getDailyEditCount() >= maxDailyEdits) {
            throw new BadRequestException("今日编辑次数已用完（每天最多 " + maxDailyEdits + " 次）");
        }
        article.setDailyEditCount(article.getDailyEditCount() + 1);
    }

    /**
     * 替换封面图 — 先上传新图再删除旧图，确保上传失败时不会丢失封面。
     */
    private void replaceCoverImage(Article article, MultipartFile coverFile) {
        String oldCoverKey = article.getCoverImage();

        // 1. 校验新封面格式
        String newExt = validateAndGetImageExtension(coverFile);

        // 2. 上传新封面 + 生成缩略图
        Long userId = article.getUser().getId();
        Long gameId = article.getGame().getId();
        Long articleId = article.getId();
        String coverFilename = "cover." + newExt;
        String newCoverKey = storageService.articleKey(userId, gameId, articleId, coverFilename);

        Path tempCover = null;
        Path thumbDir = null;
        try {
            tempCover = Files.createTempFile("edit-cover-", "." + newExt);
            coverFile.transferTo(tempCover.toFile());

            // 上传原图
            storageService.storeFromPath(newCoverKey, tempCover);
            article.setCoverImage(newCoverKey);

            // 生成并上传缩略图（270p/360p/720p）
            thumbDir = Files.createTempDirectory("edit-thumbs-");
            ImageThumbnailService.generateCoverThumbnails(tempCover, thumbDir);
            int thumbCount = 0;
            for (int size : new int[]{270, 360, 720}) {
                Path thumbPath = thumbDir.resolve("cover_thumb_" + size + ".jpg");
                if (Files.exists(thumbPath)) {
                    String thumbKey = storageService.articleKey(userId, gameId, articleId, "cover_thumb_" + size + ".jpg");
                    storageService.storeFromPath(thumbKey, thumbPath);
                    thumbCount++;
                }
            }
            if (thumbCount == 0) {
                log.warn("Article {} cover edit — no thumbnails were generated (ImageIO may have failed to decode the image). "
                        + "Original cover will be used for display.", articleId);
            } else {
                log.info("Article {} cover replaced: {} → {} ({} thumbnails)", articleId, oldCoverKey, newCoverKey, thumbCount);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to replace cover for article {}: {}", articleId, e.toString());
            throw new FileProcessingException("封面图替换失败: " + e.getMessage(), e);
        } finally {
            cleanupTempFile(tempCover);
            if (thumbDir != null) {
                try { deleteRecursively(thumbDir); } catch (IOException ignored) {}
            }
        }

        // 3. 删除旧封面图（仅当新旧 key 不同时 — 相同时已被 storeFromPath 覆盖）。
        //    缩略图 key 始终为 cover_thumb_{size}.jpg，与封面扩展名无关，
        //    已被上一步 storeFromPath（REPLACE_EXISTING）覆盖，无需删除。
        if (oldCoverKey != null && !oldCoverKey.isBlank() && !oldCoverKey.equals(newCoverKey)) {
            safeDelete(oldCoverKey);
            String baseKey = oldCoverKey.replaceAll("\\.[^.]+$", "");
            for (int size : new int[]{270, 360, 720}) {
                safeDelete(baseKey + "_thumb_" + size + ".jpg");
            }
            log.info("Article {} old cover artifacts deleted: {}", articleId, oldCoverKey);
        }
    }

    /** 安全删除存储对象，失败只记日志不抛异常 */
    private void safeDelete(String key) {
        try {
            storageService.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete old cover artifact '{}': {}", key, e.getMessage());
        }
    }

    @Override
    public void deleteArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        // 使用 storageRoot 而非 article.game.id — 合并游戏后 gameId 会变但文件在原路径
        String prefix = storageService.articleKeyFromRoot(article.getStorageRoot(), "");
        storageService.deleteDirectory(prefix);

        // 从搜索索引中移除
        searchSyncService.deleteArticle(id);

        // DB 级联删除 savings, saving_items, comments, download_logs
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
                .peek(item -> {
                    String coverKey = item.getCoverImage();
                    String cacheBuster = buildCacheBuster(item.getUpdatedAt());
                    item.setCoverImage(appendCacheBuster(resolveCoverUrl(coverKey), cacheBuster));
                    item.setCoverThumbnail(appendCacheBuster(resolveCoverThumbnailUrl(coverKey, 360), cacheBuster));
                })
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
                .peek(item -> {
                    String coverKey = item.getCoverImage();
                    String cacheBuster = buildCacheBuster(item.getUpdatedAt());
                    item.setCoverImage(appendCacheBuster(resolveCoverUrl(coverKey), cacheBuster));
                    item.setCoverThumbnail(appendCacheBuster(resolveCoverThumbnailUrl(coverKey, 360), cacheBuster));
                })
                .collect(Collectors.toList());

        return PageDTO.of(content, page, size, total);
    }

    // ── 封面图校验 ──

    /**
     * 通过魔术字节验证封面图格式，返回文件扩展名。
     * 接受 PNG、JPEG、GIF、WebP。其他格式抛出 BadRequestException。
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

    // ── 封面图 URL 解析 ──

    /**
     * 将存储 key 解析为对外 URL。
     *
     * <p>DB 中存储的是 storage key（如 {@code articles/1/2/42/cover.png}），
     * 读取时转为可公开访问的 URL。兼容存量数据中已存为 URL 的情况。
     */
    private String resolveCoverUrl(String coverKey) {
        if (coverKey == null || coverKey.isBlank()) return null;
        // 存量数据：已是完整 URL（http/https 或 /storage/ 路径），直接返回
        if (coverKey.startsWith("http://") || coverKey.startsWith("https://")
                || coverKey.startsWith("/storage/")) {
            return coverKey;
        }
        // 新数据：storage key → public URL
        try {
            return storageService.getPublicUrl(coverKey);
        } catch (Exception e) {
            log.warn("Failed to resolve cover URL for key {}: {}", coverKey, e.getMessage());
            return null;
        }
    }

    /**
     * 推导封面缩略图的对外 URL。
     * 从封面 key 推导缩略图 key（{@code cover_thumb_{size}.jpg}），若缩略图存在则返回其 public URL。
     *
     * @param coverKey 封面 storage key（如 {@code articles/1/2/42/cover.png}）
     * @param size     缩略图尺寸：270 / 360 / 720
     * @return 缩略图 URL，若缩略图不存在则返回 null（前端降级到原图）
     */
    private String resolveCoverThumbnailUrl(String coverKey, int size) {
        if (coverKey == null || coverKey.isBlank()) return null;
        // 存量 URL 无法推导缩略图 key
        if (coverKey.startsWith("http://") || coverKey.startsWith("https://")
                || coverKey.startsWith("/storage/")) {
            return null;
        }
        String thumbKey = coverKey.replaceAll("\\.[^.]+$", "_thumb_" + size + ".jpg");
        try {
            if (storageService.exists(thumbKey)) {
                return storageService.getPublicUrl(thumbKey);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail URL for key {}: {}", thumbKey, e.getMessage());
        }
        return null;
    }

    /**
     * 为 local 模式构建基于 {@code updatedAt} 的缓存破坏参数。
     * S3/COS 模式返回空字符串（预签名 URL 自带过期时间，不需额外破坏缓存）。
     */
    private String buildCacheBuster(LocalDateTime updatedAt) {
        if (updatedAt == null) return "";
        return "t=" + updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * 为 {@code /storage/} 路径附加缓存破坏查询参数。
     * S3/COS 预签名 URL 和 http/https URL 保持不变。
     */
    private String appendCacheBuster(String url, String cacheBuster) {
        if (url == null || cacheBuster == null || cacheBuster.isEmpty()) return url;
        if (url.startsWith("/storage/")) {
            return url + (url.contains("?") ? "&" : "?") + cacheBuster;
        }
        return url;
    }

    // ── 压缩包预检（本地 temp 文件，不访问网络）──

    /**
     * 压缩包本地预检 — 支持所有已知格式的结构扫描。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>文件大小检查（空文件 / 超过上限）</li>
     *   <li>格式检测（magic bytes 优先，扩展名回退）</li>
     *   <li>不支持格式友好拒绝（RAR）</li>
     *   <li>格式对应的结构完整性扫描（条目数 / 路径穿越 / 大小限制）</li>
     * </ol>
     *
     * <p>所有限制从配置文件 {@code app.extraction.*} 读取，确保与异步提取阶段完全一致。
     * 在 DB 写入和 COS 上传之前调用——预检失败时二者完全未动。
     */
    private void validateArchiveFile(Path tempZip, long zipSize, String originalFilename) {
        // ── 文件大小检查 ──
        if (zipSize == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (zipSize > maxFileSize) {
            throw new BadRequestException(
                    "File too large (" + zipSize / 1024 / 1024 + "MB, max "
                            + maxFileSize / 1024 / 1024 + "MB)");
        }

        // ── 格式检测（magic bytes 优先，扩展名回退）──
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

        // ── RAR 不支持提取，友好拒绝 ──
        if (!format.isExtractionSupported()) {
            throw new BadRequestException(
                    "RAR format is not yet supported. Please convert to ZIP, 7z, or tar.gz.");
        }

        // ── 格式对应的结构完整性扫描 ──
        switch (format) {
            case ZIP -> validateZipStructure(tempZip, zipSize);
            case SEVEN_Z -> validateSevenZStructure(tempZip);
            case TAR_GZ, TAR -> validateTarStructure(tempZip, format);
        }
    }

    /**
     * ZIP 结构完整性预检：Commons Compress 打开 + 条目扫描。
     * 检查条目数、路径穿越、单文件大小、解压总大小。
     */
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

                // ── 条目数上限检查 ──
                if (count > maxEntryCount) {
                    throw new BadRequestException(
                            "Archive contains too many files (" + count + " so far, max " + maxEntryCount + ")");
                }

                // ── 路径穿越检测 ──
                try {
                    PathTraversalValidator.validate(entry.getName());
                } catch (Exception e) {
                    throw new BadRequestException(
                            "Archive contains unsafe file path: " + entry.getName());
                }

                // ── 单文件大小检查 ──
                long size = entry.getSize();
                if (size > maxEntrySize) {
                    throw new BadRequestException(
                            "Archive contains a file that is too large: " + entry.getName()
                                    + " (" + size / 1024 / 1024 + "MB, max "
                                    + maxEntrySize / 1024 / 1024 + "MB per file)");
                }
                // ── 解压总大小检查 ──
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > maxTotalUncompressedSize) {
                        throw new BadRequestException(
                                "Archive total uncompressed size exceeds "
                                        + maxTotalUncompressedSize / 1024 / 1024 + "MB limit");
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

    /**
     * 7z 结构完整性预检：Commons Compress SevenZFile 打开 + 条目扫描。
     * 7z 不支持随机访问，此处只做元数据扫描（不读取条目内容）。
     * 检查条目数、路径穿越、条目大小限制。
     */
    private void validateSevenZStructure(Path tempZip) {
        try (SevenZFile sevenZFile = SevenZFile.builder()
                .setFile(tempZip.toFile())
                .get()) {

            int count = 0;
            long totalUncompressed = 0;
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                count++;

                // ── 条目数上限检查 ──
                if (count > maxEntryCount) {
                    throw new BadRequestException(
                            "7z contains too many entries (" + count + " so far, max " + maxEntryCount + ")");
                }

                String entryName = entry.getName() != null ? entry.getName().trim() : "";

                // ── 路径穿越检测 ──
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (Exception e) {
                    throw new BadRequestException(
                            "7z contains unsafe file path: " + entryName);
                }

                // ── 条目大小检查 ──
                long size = entry.getSize();
                if (size > maxEntrySize) {
                    throw new BadRequestException(
                            "7z contains a file that is too large: " + entryName
                                    + " (" + size / 1024 / 1024 + "MB, max "
                                    + maxEntrySize / 1024 / 1024 + "MB per file)");
                }
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > maxTotalUncompressedSize) {
                        throw new BadRequestException(
                                "7z total uncompressed size exceeds "
                                        + maxTotalUncompressedSize / 1024 / 1024 + "MB limit");
                    }
                }
            }

            if (count == 0) {
                throw new BadRequestException("7z archive is empty (no files inside)");
            }

            log.info("7z pre-validation passed: {} entries, {} bytes uncompressed",
                    count, totalUncompressed);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException(
                    "Cannot open 7z archive — file may be corrupted: " + e.getMessage());
        }
    }

    /**
     * TAR / TAR.GZ 结构完整性预检：流式顺序扫描元数据（不读取条目内容）。
     * 检查条目数、路径穿越、条目大小限制。
     */
    private void validateTarStructure(Path tempZip, ArchiveFormat format) {
        try (InputStream rawIn = Files.newInputStream(tempZip);
             InputStream bufIn = new BufferedInputStream(rawIn);
             InputStream decompIn = format == ArchiveFormat.TAR_GZ
                     ? new GzipCompressorInputStream(bufIn) : bufIn;
             TarArchiveInputStream tarIn = new TarArchiveInputStream(decompIn)) {

            int count = 0;
            long totalUncompressed = 0;
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                // 跳过符号链接和硬链接
                if (entry.isSymbolicLink() || entry.isLink()) continue;
                count++;

                // ── 条目数上限检查 ──
                if (count > maxEntryCount) {
                    throw new BadRequestException(
                            "Archive contains too many entries (" + count + " so far, max " + maxEntryCount + ")");
                }

                String entryName = entry.getName() != null ? entry.getName().trim() : "";

                // ── 路径穿越检测 ──
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (Exception e) {
                    throw new BadRequestException(
                            "Archive contains unsafe file path: " + entryName);
                }

                // ── 条目大小检查（TAR 中某些条目可能返回 -1 表示未知大小，跳过检查）──
                long size = entry.getSize();
                if (size > maxEntrySize) {
                    throw new BadRequestException(
                            "Archive contains a file that is too large: " + entryName
                                    + " (" + size / 1024 / 1024 + "MB, max "
                                    + maxEntrySize / 1024 / 1024 + "MB per file)");
                }
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > maxTotalUncompressedSize) {
                        throw new BadRequestException(
                                "Archive total uncompressed size exceeds "
                                        + maxTotalUncompressedSize / 1024 / 1024 + "MB limit");
                    }
                }
            }

            if (count == 0) {
                throw new BadRequestException("Archive is empty (no files inside)");
            }

            log.info("{} pre-validation passed: {} entries, {} bytes uncompressed",
                    format.name(), count, totalUncompressed);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException(
                    "Cannot open archive — file may be corrupted: " + e.getMessage());
        }
    }

    // ── Temp 文件清理 ──

    /**
     * 安全清理 temp 文件（不抛异常）。
     * 在所有异常路径和正常流程的 finally 块中调用，防止磁盘泄漏。
     */
    private void cleanupTempFile(Path tempZip) {
        if (tempZip != null) {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException e) {
                log.warn("Failed to clean temp file {}: {}", tempZip, e.getMessage());
            }
        }
    }

    // ── 递归目录删除 ──

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
