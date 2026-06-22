package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.AdminArticleResponse;
import com.gamesaves.gamesaves.dto.response.AdminUserResponse;
import com.gamesaves.gamesaves.dto.response.DashboardStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    DashboardStatsResponse getDashboardStats();

    Page<AdminUserResponse> listUsers(Pageable pageable, String keyword);

    AdminUserResponse toggleUserBan(Long userId);

    Page<AdminArticleResponse> listArticles(Pageable pageable, String keyword, String status);

    void deleteArticle(Long articleId);
}
