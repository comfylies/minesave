package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminArticleResponse {

    private Long id;
    private String title;
    private String version;
    private String status;
    private String errorMessage;
    private String gameName;
    private Long gameId;
    private String username;
    private Long userId;
    private Long fileSize;
    private Integer downloadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminArticleResponse fromEntity(Article article) {
        return AdminArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .version(article.getVersion())
                .status(article.getStatus().name())
                .errorMessage(article.getErrorMessage())
                .gameName(article.getGame() != null ? article.getGame().getName() : null)
                .gameId(article.getGame() != null ? article.getGame().getId() : null)
                .username(article.getUser() != null ? article.getUser().getUsername() : null)
                .userId(article.getUser() != null ? article.getUser().getId() : null)
                .fileSize(article.getFileSize())
                .downloadCount(article.getDownloadCount())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
