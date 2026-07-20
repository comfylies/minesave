package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.service.StorageService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String role;
    private String avatarUrl;
    private String avatarThumbnailUrl;
    private String avatarSmallUrl;
    private String bio;
    private LocalDateTime lastLogin;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserResponse fromEntity(User user) {
        return fromEntity(user, false);
    }

    /**
     * 从实体创建响应，可选择脱敏模式。
     * publicView=true 时对 phone/email 脱敏，防止 PII 泄露。
     */
    public static UserResponse fromEntity(User user, boolean publicView) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .phone(publicView ? maskPhone(user.getPhone()) : user.getPhone())
                .email(publicView ? maskEmail(user.getEmail()) : user.getEmail())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .lastLogin(publicView ? null : user.getLastLogin())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static UserResponse fromEntity(User user, boolean publicView, StorageService storageService) {
        UserResponse response = fromEntity(user, publicView);
        String avatarKey = user.getAvatarKey();
        if (avatarKey == null || avatarKey.isBlank()) return response;

        response.setAvatarUrl(storageService.getPublicUrl(avatarKey));
        if (avatarKey.matches("avatars/\\d+/[a-f0-9-]+-180\\.jpg")) {
            response.setAvatarThumbnailUrl(storageService.getPublicUrl(avatarKey.replace("-180.jpg", "-90.jpg")));
            response.setAvatarSmallUrl(storageService.getPublicUrl(avatarKey.replace("-180.jpg", "-45.jpg")));
        }
        return response;
    }

    /**
     * 判断当前请求者是否有权查看目标用户的完整信息。
     */
    public static boolean canViewFullInfo(Long targetUserId) {
        try {
            Long currentUserId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
            if (currentUserId.equals(targetUserId)) return true;
            // 检查是否为管理员
            return cn.dev33.satoken.stp.StpUtil.hasPermission("user:manage");
        } catch (Exception e) {
            return false; // 未登录
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) return name.charAt(0) + "***@" + domain;
        return name.substring(0, 2) + "***@" + domain;
    }
}
