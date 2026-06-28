package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminGameResponse {

    private Long id;
    private String name;
    private String thumbnailUrl;       // 最早存档的 360px 高封面缩略图 URL
    private Long articleCount;         // READY 状态存档数
    private int aliasCount;            // 别名数
    private boolean hasConflict;       // 别名是否与其他 game 冲突
    private List<String> conflictGameNames; // 与哪些游戏存在别名冲突
    private LocalDateTime createdAt;
}
