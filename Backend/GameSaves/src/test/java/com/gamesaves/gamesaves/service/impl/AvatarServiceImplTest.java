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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AvatarServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @InjectMocks private AvatarServiceImpl avatarService;

    @Test
    void uploadAvatar_storesCentered180pxAvatarAndTwoThumbnails() throws Exception {
        User user = User.builder().id(9L).username("avatar-user")
                .avatarKey("avatars/9/00000000-0000-0000-0000-000000000000-180.jpg").build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.getPublicUrl(any(String.class))).thenAnswer(invocation -> "/storage/" + invocation.getArgument(0));
        MockMultipartFile image = new MockMultipartFile("file", "avatar.png", "image/png", landscapePngBytes());

        avatarService.uploadAvatar(9L, image);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storageService, times(3)).store(key.capture(), bytes.capture());
        List<String> keys = key.getAllValues();
        assertTrue(keys.get(0).matches("avatars/9/[a-f0-9-]+-180\\.jpg"));
        assertEquals(keys.get(0).replace("-180.jpg", "-90.jpg"), keys.get(1));
        assertEquals(keys.get(0).replace("-180.jpg", "-45.jpg"), keys.get(2));
        assertImageSize(bytes.getAllValues().get(0), 180);
        assertImageSize(bytes.getAllValues().get(1), 90);
        assertImageSize(bytes.getAllValues().get(2), 45);
        assertCenterCropUsesMiddleOfLandscape(bytes.getAllValues().get(0));
        verify(storageService).delete("avatars/9/00000000-0000-0000-0000-000000000000-180.jpg");
        verify(storageService).delete("avatars/9/00000000-0000-0000-0000-000000000000-90.jpg");
        verify(storageService).delete("avatars/9/00000000-0000-0000-0000-000000000000-45.jpg");
        verify(userRepository).save(user);
    }

    private void assertImageSize(byte[] bytes, int side) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(side, image.getWidth());
        assertEquals(side, image.getHeight());
    }

    private void assertCenterCropUsesMiddleOfLandscape(byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        Color center = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
        assertTrue(center.getGreen() > center.getRed());
        assertTrue(center.getGreen() > center.getBlue());
    }

    private byte[] landscapePngBytes() throws Exception {
        BufferedImage image = new BufferedImage(12, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            Color color = x < 4 ? Color.RED : x < 8 ? Color.GREEN : Color.BLUE;
            for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, color.getRGB());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
