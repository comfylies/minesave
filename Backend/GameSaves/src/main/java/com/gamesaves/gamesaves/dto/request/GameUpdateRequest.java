package com.gamesaves.gamesaves.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameUpdateRequest {

    private String name;

    private String coverUrl;

    private String description;
}
