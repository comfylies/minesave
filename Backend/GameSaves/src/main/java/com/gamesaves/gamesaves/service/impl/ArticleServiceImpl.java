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
import com.gamesaves.gamesaves.util.ImageThumbnailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // Validate
        Game game = gameRepository.findById(request.getGameId())
                .orElseThrow(() -> new ResourceNotFoundException("Game", request.getGameId()));
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("ZIP file is required");
        }

        // Resolve README content:
        //   Priority: 1) manual markdown text in request  2) uploaded .md file  3) null (extract from ZIP)
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

        // Build storage prefix (without article ID, which we get after save)
        String storagePrefix = user.getId() + "/" + game.getId() + "/";

        final String finalReadmeRaw = resolvedReadmeRaw;
        // Create article with placeholder storageRoot (ID not yet generated)
        Article article = Article.builder()
                .title(request.getTitle())
                .version(request.getVersion())
                .game(game)
                .user(user)
                .description(request.getDescription())
                .readmeRaw(finalReadmeRaw)
                .zipFilename(file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "archive.zip")
                .storageRoot(storagePrefix)  // placeholder, updated below
                .status(Article.ArticleStatus.UPLOADING)
                .build();

        article = articleRepository.save(article);

        // Now we have the ID — set the real storage root
        String storageRoot = storagePrefix + article.getId() + "/";
        article.setStorageRoot(storageRoot);

        // Associate tags if provided
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            article.setTags(new HashSet<>(tags));
        }

        article = articleRepository.save(article);

        // Save files using StorageService
        try {
            String cosPrefix = storageService.articleKey(user.getId(), game.getId(), article.getId(), "");

            // Save ZIP to temp then upload to storage
            Path tempZip = Files.createTempFile("upload-", ".zip");
            try {
                file.transferTo(tempZip.toFile());
                long zipSize = Files.size(tempZip);
                article.setFileSize(zipSize);

                String zipKey = cosPrefix + article.getZipFilename();
                storageService.storeFromPath(zipKey, tempZip);

                log.info("Article {} created, ZIP uploaded to {}", article.getId(), zipKey);
            } finally {
                Files.deleteIfExists(tempZip);
            }

            // Save cover image if provided
            if (coverFile != null && !coverFile.isEmpty()) {
                try {
                    String coverExt = validateAndGetImageExtension(coverFile);
                    Path tempCover = Files.createTempFile("cover-", "." + coverExt);
                    try {
                        coverFile.transferTo(tempCover.toFile());

                        // Upload original cover
                        String coverFilename = "cover." + coverExt;
                        String coverKey = cosPrefix + coverFilename;
                        storageService.storeFromPath(coverKey, tempCover);
                        article.setCoverImage(storageService.getPublicUrl(coverKey));

                        // Generate thumbnails locally, upload each
                        Path thumbDir = Files.createTempDirectory("thumbs-");
                        try {
                            ImageThumbnailService.generateCoverThumbnails(tempCover, thumbDir);
                            for (int size : new int[]{270, 360, 720}) {
                                Path thumbPath = thumbDir.resolve("cover_thumb_" + size + ".jpg");
                                if (Files.exists(thumbPath)) {
                                    String thumbKey = cosPrefix + "cover_thumb_" + size + ".jpg";
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

            // Submit async extraction AFTER current transaction commits
            final Long savedArticleId = article.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            zipExtractionService.extractAsync(savedArticleId);
                        }
                    });

        } catch (IOException e) {
            log.error("Failed to process files for article {}: {}", article.getId(), e.toString());
            throw new FileProcessingException(
                    "Failed to save uploaded file: " + e.getMessage(), e);
        }

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

        if (request.getTitle() != null) article.setTitle(request.getTitle());
        if (request.getVersion() != null) article.setVersion(request.getVersion());
        if (request.getDescription() != null) article.setDescription(request.getDescription());

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

        // Delete physical files via storage service
        String prefix = storageService.articleKey(
                article.getUser().getId(), article.getGame().getId(), article.getId(), "");
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
