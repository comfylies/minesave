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

    @NotNull(message = "User ID is required")
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
