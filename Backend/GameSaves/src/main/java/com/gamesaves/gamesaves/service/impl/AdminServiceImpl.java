package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.AdminArticleResponse;
import com.gamesaves.gamesaves.dto.response.AdminUserResponse;
import com.gamesaves.gamesaves.dto.response.DashboardStatsResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.CommentRepository;
import com.gamesaves.gamesaves.repository.DownloadLogRepository;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final GameRepository gameRepository;
    private final CommentRepository commentRepository;
    private final DownloadLogRepository downloadLogRepository;

    public AdminServiceImpl(UserRepository userRepository,
                            ArticleRepository articleRepository,
                            GameRepository gameRepository,
                            CommentRepository commentRepository,
                            DownloadLogRepository downloadLogRepository) {
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.gameRepository = gameRepository;
        this.commentRepository = commentRepository;
        this.downloadLogRepository = downloadLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        return DashboardStatsResponse.builder()
                .userCount(userRepository.count())
                .articleCount(articleRepository.count())
                .gameCount(gameRepository.count())
                .commentCount(commentRepository.count())
                .downloadCount(downloadLogRepository.count())
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

        commentRepository.deleteByArticleId(articleId);
        downloadLogRepository.deleteByArticleId(articleId);
        articleRepository.delete(article);
        log.info("Article {} deleted by admin", article.getTitle());
    }
}
