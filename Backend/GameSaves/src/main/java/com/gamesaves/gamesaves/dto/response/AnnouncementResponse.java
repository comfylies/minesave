package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.Announcement;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnnouncementResponse {

    private Long id;
    private String title;
    private String contentRaw;
    private String contentHtml;
    private Long authorId;
    private String authorName;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AnnouncementResponse fromEntity(Announcement a) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .contentRaw(a.getContentRaw())
                .contentHtml(a.getContentHtml())
                .authorId(a.getAuthor() != null ? a.getAuthor().getId() : null)
                .authorName(a.getAuthor() != null ? a.getAuthor().getUsername() : null)
                .isActive(a.getIsActive())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
