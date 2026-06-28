package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "幽灵文章" — 数据库有记录但存储中找不到对应文件的存档。
 * 常见原因：跨机器迁移（COS 文件在另一台电脑）、手动删除磁盘文件、存储后端切换。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhostArticleResponse {

    private Long id;
    private String title;
    private String version;
    private String status;
    private String gameName;
    private Long gameId;
    private String username;
    private Long userId;
    private String storageRoot;
    private String storagePrefix;        // 实际检查的 storage key
    private boolean hasAnyFile;          // 存储中是否有任何文件
    private Long fileSize;               // DB 记录的 ZIP 大小
    private LocalDateTime createdAt;

    public static GhostArticleResponse fromEntity(Article article, String storagePrefix, boolean hasAnyFile) {
        return GhostArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .version(article.getVersion())
                .status(article.getStatus().name())
                .gameName(article.getGame() != null ? article.getGame().getName() : null)
                .gameId(article.getGame() != null ? article.getGame().getId() : null)
                .username(article.getUser() != null ? article.getUser().getUsername() : null)
                .userId(article.getUser() != null ? article.getUser().getId() : null)
                .storageRoot(article.getStorageRoot())
                .storagePrefix(storagePrefix)
                .hasAnyFile(hasAnyFile)
                .fileSize(article.getFileSize())
                .createdAt(article.getCreatedAt())
                .build();
    }
}
