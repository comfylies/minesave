package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.request.ArticleCreateRequest;
import com.gamesaves.gamesaves.dto.request.ArticleUpdateRequest;
import com.gamesaves.gamesaves.dto.response.ArticleDetailResponse;
import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ArticleService {

    ArticleDetailResponse createArticle(ArticleCreateRequest request, MultipartFile file, MultipartFile readmeFile);

    ArticleDetailResponse getArticleDetail(Long id);

    ArticleDetailResponse updateArticle(Long id, ArticleUpdateRequest request);

    void deleteArticle(Long id);

    Map<String, String> getArticleStatus(Long id);

    PageDTO<ArticleListItemResponse> getArticlesByGame(Long gameId, int page, int size);

    PageDTO<ArticleListItemResponse> getUserArticles(Long userId, int page, int size);
}
