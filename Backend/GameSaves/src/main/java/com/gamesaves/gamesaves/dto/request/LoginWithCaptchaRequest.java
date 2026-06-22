package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带验证码的登录请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginWithCaptchaRequest {

    @NotBlank(message = "Login identifier cannot be empty")
    private String login;           // username or phone

    @NotBlank(message = "Password cannot be empty")
    private String password;

    private String captchaKey;      // 验证码key（获取验证码时返回）

    private String captchaCode;     // 用户输入的验证码
}
