package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import com.gamesaves.gamesaves.dto.response.FavoriteResponse;
import com.gamesaves.gamesaves.entity.ArticleFavorite;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleFavoriteRepository;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.service.ArticleFavoriteService;
import com.gamesaves.gamesaves.service.StorageService;
import com.gamesaves.gamesaves.util.CoverUrlResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ArticleFavoriteServiceImpl implements ArticleFavoriteService {

    private final ArticleFavoriteRepository favoriteRepository;
    private final ArticleRepository articleRepository;
    private final StorageService storageService;

    public ArticleFavoriteServiceImpl(ArticleFavoriteRepository favoriteRepository,
                                      ArticleRepository articleRepository,
                                      StorageService storageService) {
        this.favoriteRepository = favoriteRepository;
        this.articleRepository = articleRepository;
        this.storageService = storageService;
    }

    @Override
    public FavoriteResponse toggle(Long articleId, Long userId) {
        if (!articleRepository.existsById(articleId)) {
            throw new ResourceNotFoundException("Article", articleId);
        }

        return favoriteRepository.findByArticleIdAndUserId(articleId, userId)
                .map(favorite -> {
                    favoriteRepository.delete(favorite);
                    return new FavoriteResponse(false);
                })
                .orElseGet(() -> {
                    favoriteRepository.save(ArticleFavorite.builder()
                            .articleId(articleId)
                            .userId(userId)
                            .build());
                    return new FavoriteResponse(true);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorited(Long articleId, Long userId) {
        return favoriteRepository.findByArticleIdAndUserId(articleId, userId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ArticleListItemResponse> getFavorites(Long userId, int page, int size) {
        long total = favoriteRepository.countByUserId(userId);
        List<ArticleListItemResponse> content = favoriteRepository
                .findFavoriteArticlesByUserId(userId, PageRequest.of(page, size))
                .stream()
                .map(ArticleListItemResponse::fromEntity)
                .peek(item -> CoverUrlResolver.resolveListItem(item, storageService))
                .toList();
        return PageDTO.of(content, page, size, total);
    }
}
