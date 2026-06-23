package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class ArticleListItemResponse {

    private Long id;
    private String title;
    private String version;
    private String gameName;
    private String nickname;
    private String description;
    private Long fileSize;
    private Integer downloadCount;
    private String status;
    private List<String> tagNames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArticleListItemResponse fromEntity(Article article) {
        List<String> tagNameList = article.getTags() != null
                ? article.getTags().stream()
                    .map(tag -> tag.getName())
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return ArticleListItemResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .version(article.getVersion())
                .gameName(article.getGame() != null ? article.getGame().getName() : null)
                .nickname(article.getUser() != null ? article.getUser().getNickname() : null)
                .description(article.getDescription())
                .fileSize(article.getFileSize())
                .downloadCount(article.getDownloadCount())
                .status(article.getStatus().name())
                .tagNames(tagNameList)
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
