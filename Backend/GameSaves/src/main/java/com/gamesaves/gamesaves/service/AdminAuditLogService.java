package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAuditLogService {

    /**
     * 记录审计日志。自动从当前登录上下文获取操作者信息。
     * @param action 操作类型（delete_article, toggle_ban, merge_games, etc.）
     * @param targetType 目标类型（article, user, game, etc.）
     * @param targetId 目标ID
     * @param detail 操作详情
     */
    void log(String action, String targetType, Long targetId, String detail);

    /** 直接日志（指定操作者，用于内部调用） */
    void logWithUser(Long adminUserId, String adminUsername, String action,
                     String targetType, Long targetId, String detail);

    Page<AdminAuditLog> list(Pageable pageable);
}
