package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.request.ArticleCreateRequest;
import com.gamesaves.gamesaves.dto.request.ArticleFullUpdateRequest;
import com.gamesaves.gamesaves.dto.request.ArticleUpdateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.ArticleDetailResponse;
import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import com.gamesaves.gamesaves.dto.response.VoteResponse;
import com.gamesaves.gamesaves.dto.request.VoteRequest;
import com.gamesaves.gamesaves.service.ArticleService;
import com.gamesaves.gamesaves.service.ArticleVoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleVoteService articleVoteService;

    public ArticleController(ArticleService articleService,
                              ArticleVoteService articleVoteService) {
        this.articleService = articleService;
        this.articleVoteService = articleVoteService;
    }

    @PostMapping
    public ApiResponse<ArticleDetailResponse> createArticle(
            @Valid @RequestPart("metadata") ArticleCreateRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "readmeFile", required = false) MultipartFile readmeFile,
            @RequestPart(value = "coverFile", required = false) MultipartFile coverFile) {
        // Override userId from session to prevent impersonation
        request.setUserId(StpUtil.getLoginIdAsLong());
        ArticleDetailResponse article = articleService.createArticle(request, file, readmeFile, coverFile);
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

    /** 完整编辑存档（支持 README 文件替换、封面图替换） */
    @PostMapping("/{id}/edit")
    public ApiResponse<ArticleDetailResponse> updateArticleFull(
            @PathVariable Long id,
            @RequestPart("metadata") ArticleFullUpdateRequest request,
            @RequestPart(value = "readmeFile", required = false) MultipartFile readmeFile,
            @RequestPart(value = "coverFile", required = false) MultipartFile coverFile) {
        ArticleDetailResponse article = articleService.updateArticleFull(id, request, readmeFile, coverFile);
        return ApiResponse.success("Article updated", article);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return ApiResponse.success("Article deleted", null);
    }

    @GetMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> getArticleStatus(@PathVariable Long id) {
        Map<String, Object> status = articleService.getArticleStatus(id);
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

    // ── 好评/差评 ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/vote")
    public ApiResponse<VoteResponse> voteArticle(
            @PathVariable Long id,
            @Valid @RequestBody VoteRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        String ip = getClientIp(httpRequest);
        VoteResponse response = articleVoteService.vote(id, request.getVoteType(), currentUserId, ip);
        return ApiResponse.success(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = parts[i].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/{id}/my-vote")
    public ApiResponse<Map<String, Object>> getMyVote(@PathVariable Long id) {
        Map<String, Object> result = new java.util.HashMap<>();
        if (StpUtil.isLogin()) {
            String userVote = articleVoteService.getCurrentUserVote(id, StpUtil.getLoginIdAsLong());
            result.put("userVote", userVote);
        } else {
            result.put("userVote", null);
        }
        return ApiResponse.success(result);
    }
}
