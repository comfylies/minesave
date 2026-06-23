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
import com.gamesaves.gamesaves.service.ZipExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

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

    @Value("${app.storage.database-path:../../Database}")
    private String databasePathConfig;

    private Path storageBasePath;

    @PostConstruct
    public void init() {
        storageBasePath = Paths.get(databasePathConfig).toAbsolutePath().normalize();
        log.info("Storage base path: {} (from config: {})", storageBasePath, databasePathConfig);
    }

    public ArticleServiceImpl(ArticleRepository articleRepository,
                               GameRepository gameRepository,
                               UserRepository userRepository,
                               SavingsRepository savingsRepository,
                               TagRepository tagRepository,
                               ZipExtractionService zipExtractionService,
                               SearchSyncService searchSyncService) {
        this.articleRepository = articleRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.savingsRepository = savingsRepository;
        this.tagRepository = tagRepository;
        this.zipExtractionService = zipExtractionService;
        this.searchSyncService = searchSyncService;
    }

    @Override
    public ArticleDetailResponse createArticle(ArticleCreateRequest request, MultipartFile file,
                                                MultipartFile readmeFile) {
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

        // Build storage path prefix (without article ID, which we get after save)
        String storagePrefix = storageBasePath.resolve(String.valueOf(user.getId()))
                .resolve(String.valueOf(game.getId())).toString().replace("\\", "/") + "/";

        final String finalReadmeRaw = resolvedReadmeRaw;
        // Create article with placeholder storageRoot (ID not yet generated)
        // IDENTITY generation forces immediate INSERT, so storage_root must be NOT NULL
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

        // Save ZIP to disk
        try {
            Path storageDir = storageBasePath
                    .resolve(String.valueOf(user.getId()))
                    .resolve(String.valueOf(game.getId()))
                    .resolve(String.valueOf(article.getId()));
            Files.createDirectories(storageDir);

            Path zipPath = storageDir.resolve(article.getZipFilename());
            file.transferTo(zipPath.toFile());

            article.setFileSize(Files.size(zipPath));
            article = articleRepository.save(article);

            log.info("Article {} created, ZIP saved to {}", article.getId(), zipPath);

            // Submit async extraction AFTER current transaction commits.
            // The async thread needs the article to be visible in the database.
            final Long savedArticleId = article.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            zipExtractionService.extractAsync(savedArticleId);
                        }
                    });

        } catch (IOException e) {
            log.error("Failed to save ZIP for article {}: {}", article.getId(), e.toString());
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

        // Delete physical files
        try {
            Path storageRoot = storageBasePath
                    .resolve(String.valueOf(article.getUser().getId()))
                    .resolve(String.valueOf(article.getGame().getId()))
                    .resolve(String.valueOf(article.getId()));
            if (Files.exists(storageRoot)) {
                deleteRecursively(storageRoot);
            }
        } catch (IOException e) {
            log.warn("Failed to delete physical files for article {}", id, e);
        }

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
