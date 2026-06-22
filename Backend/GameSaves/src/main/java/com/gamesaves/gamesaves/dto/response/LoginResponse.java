package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 登录成功响应：包含 Sa-Token 和用户信息
 */
@Data
@Builder
@AllArgsConstructor
public class LoginResponse {

    private String tokenName;       // token名称（用于前端请求头）
    private String tokenValue;      // token值
    private UserResponse user;      // 用户信息
}
