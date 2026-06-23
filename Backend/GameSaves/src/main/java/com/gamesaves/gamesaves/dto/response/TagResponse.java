package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class TagResponse {

    private Long id;
    private String name;
    private String source;
    private Long articleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TagResponse fromEntity(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .source(tag.getSource().name())
                .articleCount(0L)
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }

    public static TagResponse fromEntity(Tag tag, Long articleCount) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .source(tag.getSource().name())
                .articleCount(articleCount != null ? articleCount : 0L)
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }
}
