package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.CommentCreateRequest;
import com.gamesaves.gamesaves.dto.response.CommentResponse;

import java.util.List;

public interface CommentService {

    CommentResponse createComment(CommentCreateRequest request, Long currentUserId);

    List<CommentResponse> getCommentsByArticle(Long articleId);

    void deleteComment(Long commentId, Long currentUserId);
}
