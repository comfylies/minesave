package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 2, max = 50, message = "Username must be 2-50 characters")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, max = 100, message = "Password must be 6-100 characters")
    private String password;

    @Size(min = 1, max = 24, message = "Nickname must be 1-24 characters")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s·]+$", message = "Nickname can only contain Chinese characters, letters, digits, spaces, underscores, hyphens, and middle dots")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Invalid phone number format")
    private String phone;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    private String email;
}
