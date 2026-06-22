package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Login identifier cannot be empty")
    private String login;           // username or phone

    @NotBlank(message = "Password cannot be empty")
    private String password;
}
