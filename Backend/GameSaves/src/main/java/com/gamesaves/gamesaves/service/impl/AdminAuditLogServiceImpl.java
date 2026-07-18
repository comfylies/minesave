package com.gamesaves.gamesaves.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.entity.AdminAuditLog;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.AdminAuditLogRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.AdminAuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditLogServiceImpl.class);
    private final AdminAuditLogRepository repository;
    private final UserRepository userRepository;

    public AdminAuditLogServiceImpl(AdminAuditLogRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public void log(String action, String targetType, Long targetId, String detail) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userRepository.findById(userId).orElse(null);
            String username = user != null ? user.getUsername() : String.valueOf(userId);
            logWithUser(userId, username, action, targetType, targetId, detail);
        } catch (Exception e) {
            log.warn("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Override
    public void logWithUser(Long adminUserId, String adminUsername, String action,
                            String targetType, Long targetId, String detail) {
        try {
            String username = adminUsername;
            if (username == null) {
                username = String.valueOf(adminUserId);
            }
            String ip = getClientIp();
            AdminAuditLog auditLog = new AdminAuditLog(
                    adminUserId, username, action, targetType, targetId, detail, ip);
            repository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String xForwardedFor = attrs.getRequest().getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
