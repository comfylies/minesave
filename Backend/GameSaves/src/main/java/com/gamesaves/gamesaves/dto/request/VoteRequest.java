package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {

    @NotBlank(message = "voteType 不能为空")
    @Pattern(regexp = "UP|DOWN|NONE", message = "voteType 必须为 UP、DOWN 或 NONE")
    private String voteType;
}
