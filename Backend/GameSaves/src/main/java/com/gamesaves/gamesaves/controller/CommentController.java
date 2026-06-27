package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.request.CommentCreateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.CommentResponse;
import com.gamesaves.gamesaves.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/article/{articleId}")
    public ApiResponse<List<CommentResponse>> getComments(@PathVariable Long articleId) {
        List<CommentResponse> comments = commentService.getCommentsByArticle(articleId);
        return ApiResponse.success(comments);
    }

    @PostMapping
    public ApiResponse<CommentResponse> createComment(@Valid @RequestBody CommentCreateRequest request) {
        // 使用当前登录用户ID，不信任请求参数
        Long currentUserId = StpUtil.getLoginIdAsLong();
        CommentResponse comment = commentService.createComment(request, currentUserId);
        return ApiResponse.success("Comment created", comment);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        // 使用当前登录用户ID，不信任请求参数
        Long currentUserId = StpUtil.getLoginIdAsLong();
        commentService.deleteComment(id, currentUserId);
        return ApiResponse.success("Comment deleted", null);
    }
}
