package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.AdminArticleResponse;
import com.gamesaves.gamesaves.dto.response.AdminGameResponse;
import com.gamesaves.gamesaves.dto.response.AdminUserResponse;
import com.gamesaves.gamesaves.dto.response.DashboardStatsResponse;
import com.gamesaves.gamesaves.dto.response.GhostArticleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminService {

    DashboardStatsResponse getDashboardStats();

    Page<AdminUserResponse> listUsers(Pageable pageable, String keyword);

    AdminUserResponse toggleUserBan(Long userId);

    Page<AdminArticleResponse> listArticles(Pageable pageable, String keyword, String status);

    void deleteArticle(Long articleId);

    /** 管理员游戏列表（含封面缩略图、存档数、别名数、冲突检测） */
    List<AdminGameResponse> listGamesForAdmin();

    /**
     * 扫描"幽灵文章"：数据库有记录但存储中找不到对应文件的存档。
     * 常见于跨机器迁移（COS 文件在另一台电脑）、手动删除磁盘文件、存储后端切换。
     *
     * @return 所有文章的扫描结果，包含文件是否存在标记
     */
    List<GhostArticleResponse> scanGhostArticles();

    /**
     * 批量删除指定的幽灵文章（同时清理 DB 记录和残留文件）。
     *
     * @param ids 要删除的文章 ID 列表
     * @return 实际删除数量
     */
    int deleteGhostArticles(List<Long> ids);
}
