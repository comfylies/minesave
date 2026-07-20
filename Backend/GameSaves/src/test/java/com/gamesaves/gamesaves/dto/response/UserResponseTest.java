package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserResponseTest {

    @Test
    void fromEntity_exposesDerivedAvatarThumbnailUrls() throws Exception {
        User user = User.builder()
                .id(9L)
                .avatarKey("avatars/9/00000000-0000-0000-0000-000000000000-180.jpg")
                .avatarUrl("/storage/avatars/9/00000000-0000-0000-0000-000000000000-180.jpg")
                .build();

        UserResponse response = UserResponse.fromEntity(user);

        assertEquals("/storage/avatars/9/00000000-0000-0000-0000-000000000000-90.jpg", response.getAvatarThumbnailUrl());
        assertEquals("/storage/avatars/9/00000000-0000-0000-0000-000000000000-45.jpg", response.getAvatarSmallUrl());
    }
}
