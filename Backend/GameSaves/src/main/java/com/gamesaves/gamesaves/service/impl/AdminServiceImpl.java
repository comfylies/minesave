package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.AdminArticleResponse;
import com.gamesaves.gamesaves.dto.response.AdminGameResponse;
import com.gamesaves.gamesaves.dto.response.AdminUserResponse;
import com.gamesaves.gamesaves.dto.response.DashboardStatsResponse;
import com.gamesaves.gamesaves.dto.response.GhostArticleResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.GameAlias;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.CommentRepository;
import com.gamesaves.gamesaves.repository.DownloadLogRepository;
import com.gamesaves.gamesaves.repository.GameAliasRepository;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.AdminService;
import com.gamesaves.gamesaves.service.SearchSyncService;
import com.gamesaves.gamesaves.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final GameRepository gameRepository;
    private final GameAliasRepository gameAliasRepository;
    private final CommentRepository commentRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final StorageService storageService;
    private final SearchSyncService searchSyncService;

    public AdminServiceImpl(UserRepository userRepository,
                            ArticleRepository articleRepository,
                            GameRepository gameRepository,
                            GameAliasRepository gameAliasRepository,
                            CommentRepository commentRepository,
                            DownloadLogRepository downloadLogRepository,
                            StorageService storageService,
                            SearchSyncService searchSyncService) {
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.gameRepository = gameRepository;
        this.gameAliasRepository = gameAliasRepository;
        this.commentRepository = commentRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.storageService = storageService;
        this.searchSyncService = searchSyncService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        long imageBytes = 0;
        try {
            imageBytes = storageService.totalImageSize("articles/");
        } catch (Exception e) {
            log.warn("Failed to calculate image storage size: {}", e.getMessage());
        }

        return DashboardStatsResponse.builder()
                .userCount(userRepository.count())
                .articleCount(articleRepository.count())
                .gameCount(gameRepository.count())
                .commentCount(commentRepository.count())
                .downloadCount(downloadLogRepository.count())
                .imageStorageBytes(imageBytes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Pageable pageable, String keyword) {
        Page<User> userPage;
        if (keyword != null && !keyword.isBlank()) {
            userPage = userRepository.searchByKeyword(keyword, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }
        return userPage.map(AdminUserResponse::fromEntity);
    }

    @Override
    public AdminUserResponse toggleUserBan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if ("admin".equals(user.getRole())) {
            throw new BadRequestException("Cannot ban admin users");
        }

        user.setIsActive(!user.getIsActive());
        user = userRepository.save(user);
        log.info("User {} {} by admin", user.getUsername(),
                user.getIsActive() ? "unbanned" : "banned");
        return AdminUserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminArticleResponse> listArticles(Pageable pageable, String keyword, String status) {
        Page<Article> articlePage;

        if (status != null && !status.isBlank()) {
            Article.ArticleStatus articleStatus = Article.ArticleStatus.valueOf(status.toUpperCase());
            articlePage = articleRepository.findByStatusWithDetails(articleStatus, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            articlePage = articleRepository.searchByKeyword(keyword, pageable);
        } else {
            articlePage = articleRepository.findAllWithDetails(pageable);
        }

        return articlePage.map(AdminArticleResponse::fromEntity);
    }

    @Override
    public void deleteArticle(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        // 删除物理文件（ZIP、解压文件、封面缩略图、README 图片等）
        // 使用 storageRoot 而非 article.game.id — 合并游戏后 gameId 会变但文件在原路径
        String prefix = storageService.articleKeyFromRoot(article.getStorageRoot(), "");
        storageService.deleteDirectory(prefix);

        // 从搜索索引中移除
        searchSyncService.deleteArticle(articleId);

        // 清理数据库（关联的评论、下载记录先手动删，再删主记录）
        commentRepository.deleteByArticleId(articleId);
        downloadLogRepository.deleteByArticleId(articleId);
        articleRepository.delete(article);
        log.info("Article {} deleted by admin", article.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GhostArticleResponse> scanGhostArticles() {
        List<Article> allArticles = articleRepository.findAll();
        log.info("Scanning {} articles for ghost records...", allArticles.size());

        List<GhostArticleResponse> results = new java.util.ArrayList<>();
        for (Article article : allArticles) {
            String prefix = storageService.articleKeyFromRoot(article.getStorageRoot(), "");
            boolean hasFiles;
            try {
                List<String> files = storageService.listFiles(prefix);
                hasFiles = !files.isEmpty();
            } catch (Exception e) {
                // Storage unreachable → treat as ghost
                log.warn("Storage check failed for article {} (prefix={}): {}",
                        article.getId(), prefix, e.getMessage());
                hasFiles = false;
            }
            results.add(GhostArticleResponse.fromEntity(article, prefix, hasFiles));
        }

        long ghostCount = results.stream().filter(r -> !r.isHasAnyFile()).count();
        log.info("Ghost scan complete: {} total, {} ghosts, {} healthy",
                results.size(), ghostCount, results.size() - ghostCount);
        return results;
    }

    @Override
    public int deleteGhostArticles(List<Long> ids) {
        int deleted = 0;
        for (Long articleId : ids) {
            try {
                Article article = articleRepository.findById(articleId)
                        .orElse(null);
                if (article == null) {
                    log.warn("Ghost delete skipped — article {} not found in DB", articleId);
                    continue;
                }

                // 尝试删除可能残留的文件
                try {
                    String prefix = storageService.articleKeyFromRoot(
                            article.getStorageRoot(), "");
                    storageService.deleteDirectory(prefix);
                } catch (Exception e) {
                    log.warn("Failed to delete files for ghost article {}: {}", articleId, e.getMessage());
                }

                // 从搜索索引中移除
                try {
                    searchSyncService.deleteArticle(articleId);
                } catch (Exception e) {
                    log.warn("Failed to remove ghost article {} from search index: {}", articleId, e.getMessage());
                }

                // 清理关联数据 + 主记录
                commentRepository.deleteByArticleId(articleId);
                downloadLogRepository.deleteByArticleId(articleId);
                articleRepository.delete(article);
                deleted++;
                log.info("Ghost article {} deleted", articleId);
            } catch (Exception e) {
                log.error("Failed to delete ghost article {}: {}", articleId, e.getMessage());
            }
        }
        log.info("Ghost batch delete: {} requested, {} deleted", ids.size(), deleted);
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminGameResponse> listGamesForAdmin() {
        List<Game> games = gameRepository.findAllByOrderByCreatedAtDesc();

        // ── 冲突检测：找出被多个 game 共用的 alias_normalized ──
        List<GameAlias> allAliases = gameAliasRepository.findAll();
        Set<String> conflictingAliases = allAliases.stream()
                .collect(Collectors.groupingBy(GameAlias::getAliasNormalized))
                .entrySet().stream()
                .filter(e -> e.getValue().stream()
                        .map(a -> a.getGame().getId())
                        .distinct()
                        .count() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // 按 gameId 收集冲突的 game 名称
        Map<Long, Set<String>> conflictByGameId = new HashMap<>();
        for (GameAlias alias : allAliases) {
            if (conflictingAliases.contains(alias.getAliasNormalized())) {
                conflictByGameId
                        .computeIfAbsent(alias.getGame().getId(), k -> new LinkedHashSet<>())
                        .add(alias.getAliasNormalized());
            }
        }

        return games.stream().map(game -> {
            // 封面缩略图：取该游戏下最早的有封面的 READY 存档
            String thumbnailUrl = null;
            List<Article> earliestArticles = articleRepository.findEarliestReadyWithCover(
                    game.getId(), Article.ArticleStatus.READY, PageRequest.of(0, 1));
            if (!earliestArticles.isEmpty()) {
                Article earliest = earliestArticles.get(0);
                String coverImage = earliest.getCoverImage();
                if (coverImage != null && !coverImage.isBlank()) {
                    // 构造 360h 缩略图 URL：将 cover.{ext} 替换为 cover_thumb_360.jpg
                    thumbnailUrl = deriveThumbnailUrl(coverImage, earliest);
                }
            }

            // 统计 READY 存档数
            long articleCount = articleRepository.countByGameIdAndStatus(
                    game.getId(), Article.ArticleStatus.READY);

            // 统计别名数
            int aliasCount = (int) gameAliasRepository.countByGameId(game.getId());

            // 冲突信息
            Set<String> conflictNames = conflictByGameId.getOrDefault(game.getId(), Collections.emptySet());
            boolean hasConflict = !conflictNames.isEmpty();

            return AdminGameResponse.builder()
                    .id(game.getId())
                    .name(game.getName())
                    .thumbnailUrl(thumbnailUrl)
                    .articleCount(articleCount)
                    .aliasCount(aliasCount)
                    .hasConflict(hasConflict)
                    .conflictGameNames(hasConflict ? new ArrayList<>(conflictNames) : null)
                    .createdAt(game.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 从封面 URL 推导 360px 高缩略图 URL。
     * 原始 URL 格式：/storage/articles/{uid}/{gid}/{aid}/cover.{ext}
     * 缩略图 URL：同路径下 cover_thumb_360.jpg
     */
    private String deriveThumbnailUrl(String coverUrl, Article article) {
        try {
            String storageRoot = article.getStorageRoot();
            if (storageRoot == null) return coverUrl; // 降级

            // 使用 articleKeyFromRoot 规范化 storageRoot（兼容 Database/ 前缀的存量数据）
            String thumbKey = storageService.articleKeyFromRoot(
                    storageRoot, "cover_thumb_360.jpg");
            return storageService.generatePresignedUrl(thumbKey, 1440);
        } catch (Exception e) {
            log.warn("Failed to derive thumbnail URL for article {}: {}", article.getId(), e.getMessage());
            return coverUrl; // 降级：返回原封面
        }
    }
}
