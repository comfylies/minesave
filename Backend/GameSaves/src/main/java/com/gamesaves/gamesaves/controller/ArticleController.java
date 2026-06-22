package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.request.ArticleCreateRequest;
import com.gamesaves.gamesaves.dto.request.ArticleUpdateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.ArticleDetailResponse;
import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import com.gamesaves.gamesaves.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @PostMapping
    public ApiResponse<ArticleDetailResponse> createArticle(
            @Valid @RequestPart("metadata") ArticleCreateRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "readmeFile", required = false) MultipartFile readmeFile) {
        ArticleDetailResponse article = articleService.createArticle(request, file, readmeFile);
        return ApiResponse.success("Article created, extraction started", article);
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> getArticle(@PathVariable Long id) {
        ArticleDetailResponse article = articleService.getArticleDetail(id);
        return ApiResponse.success(article);
    }

    @PutMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> updateArticle(
            @PathVariable Long id,
            @RequestBody ArticleUpdateRequest request) {
        ArticleDetailResponse article = articleService.updateArticle(id, request);
        return ApiResponse.success(article);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return ApiResponse.success("Article deleted", null);
    }

    @GetMapping("/{id}/status")
    public ApiResponse<Map<String, String>> getArticleStatus(@PathVariable Long id) {
        Map<String, String> status = articleService.getArticleStatus(id);
        return ApiResponse.success(status);
    }

    @GetMapping("/{id}/readme")
    public ApiResponse<ArticleDetailResponse> getArticleReadme(@PathVariable Long id) {
        // Returns the full detail which includes readmeContent
        ArticleDetailResponse article = articleService.getArticleDetail(id);
        return ApiResponse.success(article);
    }

    @GetMapping("/game/{gameId}")
    public ApiResponse<PageDTO<ArticleListItemResponse>> getArticlesByGame(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageDTO<ArticleListItemResponse> articles = articleService.getArticlesByGame(gameId, page, size);
        return ApiResponse.success(articles);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<PageDTO<ArticleListItemResponse>> getUserArticles(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageDTO<ArticleListItemResponse> articles = articleService.getUserArticles(userId, page, size);
        return ApiResponse.success(articles);
    }
}
