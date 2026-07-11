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
    private Long gameId;
    private String gameName;
    private Long userId;
    private String nickname;
    private String description;
    private Long fileSize;
    private Integer downloadCount;
    private String status;
    private String coverImage;
    private String coverThumbnail;  // 360p 缩略图 URL（列表/卡片），可能为 null（缩略图不存在时降级到原图）
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
                .gameId(article.getGame() != null ? article.getGame().getId() : null)
                .gameName(article.getGame() != null ? article.getGame().getName() : null)
                .userId(article.getUser() != null ? article.getUser().getId() : null)
                .nickname(article.getUser() != null ? article.getUser().getNickname() : null)
                .description(article.getDescription())
                .fileSize(article.getFileSize())
                .downloadCount(article.getDownloadCount())
                .coverImage(article.getCoverImage())
                .status(article.getStatus().name())
                .tagNames(tagNameList)
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
