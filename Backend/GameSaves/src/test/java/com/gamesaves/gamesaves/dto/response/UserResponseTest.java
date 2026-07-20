package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserResponseTest {

    @Mock private StorageService storageService;

    @Test
    void fromEntity_signsEachAvatarVariantIndependently() throws Exception {
        User user = User.builder()
                .id(9L)
                .avatarKey("avatars/9/00000000-0000-0000-0000-000000000000-180.jpg")
                .avatarUrl("https://old-url.example/avatar.jpg")
                .build();

        when(storageService.getPublicUrl("avatars/9/00000000-0000-0000-0000-000000000000-180.jpg"))
                .thenReturn("https://cos.example/avatar-180.jpg?signature=primary");
        when(storageService.getPublicUrl("avatars/9/00000000-0000-0000-0000-000000000000-90.jpg"))
                .thenReturn("https://cos.example/avatar-90.jpg?signature=thumbnail");
        when(storageService.getPublicUrl("avatars/9/00000000-0000-0000-0000-000000000000-45.jpg"))
                .thenReturn("https://cos.example/avatar-45.jpg?signature=small");

        UserResponse response = UserResponse.fromEntity(user, false, storageService);

        assertEquals("https://cos.example/avatar-180.jpg?signature=primary", response.getAvatarUrl());
        assertEquals("https://cos.example/avatar-90.jpg?signature=thumbnail", response.getAvatarThumbnailUrl());
        assertEquals("https://cos.example/avatar-45.jpg?signature=small", response.getAvatarSmallUrl());
    }
}
