package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagCreateRequest {

    @NotBlank(message = "Tag name cannot be empty")
    @Size(max = 50, message = "Tag name too long (max 50)")
    private String name;

    private String source;
}
