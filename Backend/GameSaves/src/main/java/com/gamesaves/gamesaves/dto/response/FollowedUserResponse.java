package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.service.StorageService;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FollowedUserResponse {

    Long id;
    String username;
    String nickname;
    String avatarUrl;
    String avatarSmallUrl;
    String bio;

    public static FollowedUserResponse fromEntity(User user, StorageService storageService) {
        String avatarUrl = user.getAvatarUrl();
        String avatarSmallUrl = avatarUrl;
        String avatarKey = user.getAvatarKey();
        if (avatarKey != null && !avatarKey.isBlank()) {
            avatarUrl = storageService.getPublicUrl(avatarKey);
            avatarSmallUrl = avatarKey.matches("avatars/\\d+/[a-f0-9-]+-180\\.jpg")
                    ? storageService.getPublicUrl(avatarKey.replace("-180.jpg", "-45.jpg"))
                    : avatarUrl;
        }
        return FollowedUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatarUrl(avatarUrl)
                .avatarSmallUrl(avatarSmallUrl)
                .bio(user.getBio())
                .build();
    }
}
