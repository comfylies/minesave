package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.request.UserUpdateRequest;
import com.gamesaves.gamesaves.dto.request.ChangePasswordRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.UserResponse;
import com.gamesaves.gamesaves.service.AvatarService;
import com.gamesaves.gamesaves.service.UserService;
import com.gamesaves.gamesaves.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints that operate on the authenticated account only. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final AuthService authService;

    public AccountController(UserService userService, AvatarService avatarService, AuthService authService) {
        this.userService = userService;
        this.avatarService = avatarService;
        this.authService = authService;
    }

    @PatchMapping("/profile")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(userService.updateUser(currentUserId, request));
    }

    @PostMapping("/avatar")
    public ApiResponse<UserResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(avatarService.uploadAvatar(StpUtil.getLoginIdAsLong(), file));
    }

    @DeleteMapping("/avatar")
    public ApiResponse<UserResponse> removeAvatar() {
        return ApiResponse.success(avatarService.removeAvatar(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(StpUtil.getLoginIdAsLong(), request);
        return ApiResponse.success("Password changed. Please sign in again.", null);
    }
}
