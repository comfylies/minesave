package com.gamesaves.gamesaves.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleUpdateRequest {

    private String title;

    private String version;

    private String description;
}
