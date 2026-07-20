package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @InjectMocks private AvatarServiceImpl avatarService;

    @Test
    void uploadAvatar_storesNormalizedImageAndReplacesManagedKey() throws Exception {
        User user = User.builder().id(9L).username("avatar-user").avatarKey("avatars/9/old.jpg").build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getPublicUrl(any(String.class))).thenAnswer(invocation -> "/storage/" + invocation.getArgument(0));
        MockMultipartFile image = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes());

        avatarService.uploadAvatar(9L, image);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(key.capture(), any(byte[].class));
        assertTrue(key.getValue().matches("avatars/9/[a-f0-9-]+\\.jpg"));
        verify(storageService).delete("avatars/9/old.jpg");
        verify(userRepository).save(user);
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
