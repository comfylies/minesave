package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 搜索结果项 — 统一搜索端点返回的单条结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitResponse {

    private String type;             // "game" | "article"
    private Long id;
    private String title;            // game: name, article: title
    private String description;
    private String gameName;         // article 所属游戏名
    private Long gameId;             // article 所属游戏 ID
    private List<String> tags;
    private Long articleCount;       // game: 存档数
    private Integer downloadCount;
    private String createdAt;
    private Map<String, String> formatted;  // Meilisearch 高亮结果
}
