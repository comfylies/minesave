package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameCreateRequest {

    @NotBlank(message = "Game name cannot be empty")
    @Size(max = 100, message = "Game name too long")
    private String name;

    private String coverUrl;

    private String description;
}
