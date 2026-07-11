package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.request.*;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.LoginResponse;
import com.gamesaves.gamesaves.dto.response.UserResponse;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 提供登录、注册、验证码、邮箱验证码等公开接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    // ==================== 图形验证码 ====================

    /**
     * 获取图形验证码
     * 返回 {captchaKey, captchaImage(base64)}
     */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> getCaptcha() {
        Map<String, String> captcha = authService.generateCaptcha();
        return ApiResponse.success(captcha);
    }

    // ==================== 字段可用性检查 ====================

    /**
     * 检查用户名/邮箱/手机号是否可用（注册时实时提示）
     * 限流：每 IP 每秒最多 1 次
     */
    @GetMapping("/check-field")
    public ApiResponse<AuthService.CheckFieldResult> checkField(
            @RequestParam String field,
            @RequestParam String value,
            HttpServletRequest request) {
        AuthService.CheckFieldResult result = authService.checkFieldAvailability(field, value, request);
        return ApiResponse.success(result);
    }

    // ==================== 账号密码登录（带验证码） ====================

    /**
     * 账号密码登录
     * 需要先获取图形验证码，提交时带上 captchaKey + captchaCode
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> loginWithPassword(
            @Valid @RequestBody LoginWithCaptchaRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse result = authService.loginWithPassword(request, httpRequest);
        return ApiResponse.success("Login successful", result);
    }

    // ==================== 邮箱验证码登录 ====================

    /**
     * 邮箱验证码登录
     */
    @PostMapping("/login/email")
    public ApiResponse<LoginResponse> loginWithEmailCode(
            @Valid @RequestBody EmailLoginRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse result = authService.loginWithEmailCode(request, httpRequest);
        return ApiResponse.success("Login successful", result);
    }

    // ==================== 发送邮箱验证码 ====================

    /**
     * 发送邮箱验证码（用于登录或注册验证）
     * 安全：需先通过图形验证码 + 每日每邮箱限5次 + 发送后2分钟冷却 + 有效期5分钟
     */
    @PostMapping("/email-code")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        authService.sendEmailCode(request);
        return ApiResponse.success("Verification code sent, valid for 10 minutes", null);
    }

    // ==================== 注册 ====================

    /**
     * 用户注册（邮箱验证码校验）
     */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        LoginResponse result = authService.register(request);
        return ApiResponse.success("Registration successful", result);
    }

    // ==================== 退出登录 ====================

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success("Logged out", null);
    }

    // ==================== 登录状态检查 ====================

    /**
     * 检查当前登录状态
     * 已登录返回用户信息，未登录返回null
     */
    @GetMapping("/check")
    public ApiResponse<UserResponse> checkLogin() {
        if (!StpUtil.isLogin()) {
            throw new BadRequestException("Not logged in");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        UserResponse user = userRepository.findById(userId)
                .map(UserResponse::fromEntity)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return ApiResponse.success(user);
    }
}
