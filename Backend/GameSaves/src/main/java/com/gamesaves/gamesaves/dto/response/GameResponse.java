package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Game;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class GameResponse {

    private Long id;
    private String name;
    private String coverUrl;
    private String description;
    private Long articleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GameResponse fromEntity(Game game) {
        return GameResponse.builder()
                .id(game.getId())
                .name(game.getName())
                .coverUrl(game.getCoverUrl())
                .description(game.getDescription())
                .articleCount(0L)
                .createdAt(game.getCreatedAt())
                .updatedAt(game.getUpdatedAt())
                .build();
    }

    public static GameResponse fromEntity(Game game, Long articleCount) {
        return GameResponse.builder()
                .id(game.getId())
                .name(game.getName())
                .coverUrl(game.getCoverUrl())
                .description(game.getDescription())
                .articleCount(articleCount != null ? articleCount : 0L)
                .createdAt(game.getCreatedAt())
                .updatedAt(game.getUpdatedAt())
                .build();
    }
}
