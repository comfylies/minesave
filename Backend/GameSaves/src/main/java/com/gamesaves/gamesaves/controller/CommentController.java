package com.gamesaves.gamesaves.controller;

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
        CommentResponse comment = commentService.createComment(request);
        return ApiResponse.success("Comment created", comment);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id,
                                            @RequestParam Long userId) {
        commentService.deleteComment(id, userId);
        return ApiResponse.success("Comment deleted", null);
    }
}
