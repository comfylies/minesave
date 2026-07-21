package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Savings;
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
public class ArticleDetailResponse {

    private Long id;
    private String title;
    private String version;
    private Long gameId;
    private String gameName;
    private Long userId;
    private String nickname;
    private String description;
    private String readmeRaw;
    private String readmeContent;
    private String storageRoot;
    private String zipFilename;
    private Long fileSize;
    private Integer downloadCount;
    private Integer upvoteCount;
    private Integer downvoteCount;
    private String currentUserVote;
    private String status;
    private String errorMessage;
    private String securityLevel;
    private String coverImage;
    private String coverThumbnail;    // 360p 缩略图 URL（用于卡片/列表展示）
    private String coverThumbnail720;  // 720p 缩略图 URL（用于详情页封面图）
    private List<TagResponse> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Savings summary (if available)
    private Integer fileCount;
    private Long totalExtractSize;
    private Long savingsId;

    public static ArticleDetailResponse fromEntity(Article article) {
        List<TagResponse> tagList = article.getTags() != null
                ? article.getTags().stream()
                    .map(TagResponse::fromEntity)
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return ArticleDetailResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .version(article.getVersion())
                .gameId(article.getGame() != null ? article.getGame().getId() : null)
                .gameName(article.getGame() != null ? article.getGame().getName() : null)
                .userId(article.getUser() != null ? article.getUser().getId() : null)
                .nickname(article.getUser() != null ? article.getUser().getNickname() : null)
                .description(article.getDescription())
                .readmeRaw(article.getReadmeRaw())
                .readmeContent(article.getReadmeContent())
                .storageRoot(article.getStorageRoot())
                .zipFilename(article.getZipFilename())
                .fileSize(article.getFileSize())
                .downloadCount(article.getDownloadCount())
                .upvoteCount(article.getUpvoteCount())
                .downvoteCount(article.getDownvoteCount())
                .status(article.getStatus().name())
                .errorMessage(article.getErrorMessage())
                .securityLevel(article.getSecurityLevel().name())
                .coverImage(article.getCoverImage())
                .tags(tagList)
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    public static ArticleDetailResponse fromEntity(Article article, Savings savings) {
        ArticleDetailResponse response = fromEntity(article);
        if (savings != null) {
            response.setFileCount(savings.getFileCount());
            response.setTotalExtractSize(savings.getTotalSize());
            response.setSavingsId(savings.getId());
        }
        return response;
    }
}
