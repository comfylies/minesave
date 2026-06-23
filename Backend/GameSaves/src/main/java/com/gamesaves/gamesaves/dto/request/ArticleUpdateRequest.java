package com.gamesaves.gamesaves.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleUpdateRequest {

    private String title;

    private String version;

    private String description;

    private List<Long> tagIds;          // 更新标签关联
}
