package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.gamesaves.gamesaves.dto.response.AdminArticleResponse;
import com.gamesaves.gamesaves.dto.response.AdminUserResponse;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.DashboardStatsResponse;
import com.gamesaves.gamesaves.service.AdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@SaCheckLogin
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    @SaCheckPermission("user:manage")
    public ApiResponse<DashboardStatsResponse> getDashboard() {
        DashboardStatsResponse stats = adminService.getDashboardStats();
        return ApiResponse.success(stats);
    }

    @GetMapping("/users")
    @SaCheckPermission("user:manage")
    public ApiResponse<Page<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<AdminUserResponse> users = adminService.listUsers(
                PageRequest.of(page, size, Sort.by("createdAt").descending()), keyword);
        return ApiResponse.success(users);
    }

    @PutMapping("/users/{id}/ban")
    @SaCheckPermission("user:manage")
    public ApiResponse<AdminUserResponse> toggleUserBan(@PathVariable Long id) {
        AdminUserResponse user = adminService.toggleUserBan(id);
        return ApiResponse.success(user);
    }

    @GetMapping("/articles")
    @SaCheckPermission("article:manage")
    public ApiResponse<Page<AdminArticleResponse>> listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Page<AdminArticleResponse> articles = adminService.listArticles(
                PageRequest.of(page, size, Sort.by("createdAt").descending()), keyword, status);
        return ApiResponse.success(articles);
    }

    @DeleteMapping("/articles/{id}")
    @SaCheckPermission("article:manage")
    public ApiResponse<String> deleteArticle(@PathVariable Long id) {
        adminService.deleteArticle(id);
        return ApiResponse.success("Article deleted", "ok");
    }
}
