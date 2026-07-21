package com.gamesaves.gamesaves.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
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

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度须为 2-20 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*[a-zA-Z0-9]$|^[a-zA-Z0-9]$",
            message = "用户名只允许字母、数字、下划线和连字符，不能以连字符或下划线开头/结尾，不允许使用中文、表情符号或特殊字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度须为 6-100 个字符")
    private String password;

    @Size(min = 1, max = 24, message = "昵称长度须为 1-24 个字符")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s·]+$",
            message = "昵称只允许中文、字母、数字、空格、下划线、连字符和中间点，不允许使用表情符号或特殊字符")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确，须为11位中国大陆手机号")
    private String phone;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱地址最长 100 个字符")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码须为6位数字")
    private String emailCode;

    @AssertTrue(message = "请阅读并同意服务协议与隐私政策")
    private Boolean acceptedTerms;
}
