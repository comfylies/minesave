package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class CommentResponse {

    private Long id;
    private Long articleId;
    private Long userId;
    private String nickname;
    private String role;               // 批注者角色，前端用颜色区分
    private String content;
    private String anchor;
    private String selectedText;
    private Integer quoteStart;
    private Integer quoteEnd;
    private Long parentId;
    private List<CommentResponse> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommentResponse fromEntity(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .articleId(comment.getArticleId())
                .userId(comment.getUserId())
                .nickname(comment.getUser() != null ? comment.getUser().getNickname() : null)
                .role(comment.getUser() != null ? comment.getUser().getRole() : "user")
                .content(comment.getContent())
                .anchor(comment.getAnchor())
                .selectedText(comment.getSelectedText())
                .quoteStart(comment.getQuoteStart())
                .quoteEnd(comment.getQuoteEnd())
                .parentId(comment.getParentId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
