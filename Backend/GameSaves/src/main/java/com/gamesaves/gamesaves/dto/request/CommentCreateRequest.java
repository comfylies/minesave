package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateRequest {

    @NotNull(message = "Article ID is required")
    private Long articleId;

    // userId 不再从请求参数获取，由服务端通过 Sa-Token 获取当前登录用户
    private Long userId;

    @NotBlank(message = "Content cannot be empty")
    private String content;

    @NotBlank(message = "Anchor is required")
    private String anchor;              // JSON TextQuoteSelector

    @NotBlank(message = "Selected text is required")
    private String selectedText;

    private Integer quoteStart;

    private Integer quoteEnd;

    private Long parentId;                 // 父批注ID（回复时使用）
}
