package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送邮箱验证码请求
 * 需要先通过图形验证码校验，每日每邮箱限5次
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailCodeRequest {

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Captcha key is required")
    private String captchaKey;      // 图形验证码key

    @NotBlank(message = "Captcha code is required")
    private String captchaCode;     // 用户输入的图形验证码
}
