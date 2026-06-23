package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCreateRequest {

    @NotNull(message = "Game ID is required")
    private Long gameId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Version is required")
    private String version;

    private String description;

    private String readmeRaw;           // Markdown README (optional)

    private List<Long> tagIds;          // 可选标签 ID 列表
}
