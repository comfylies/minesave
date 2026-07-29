package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import com.gamesaves.gamesaves.dto.response.FavoriteResponse;

public interface ArticleFavoriteService {

    FavoriteResponse toggle(Long articleId, Long userId);

    boolean isFavorited(Long articleId, Long userId);

    PageDTO<ArticleListItemResponse> getFavorites(Long userId, int page, int size);
}
