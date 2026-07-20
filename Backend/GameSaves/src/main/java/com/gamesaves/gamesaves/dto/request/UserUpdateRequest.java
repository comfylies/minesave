package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(min = 1, max = 24, message = "Nickname must be 1-24 characters")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number")
    private String phone;

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;
}
