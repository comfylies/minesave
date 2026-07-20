package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.UserResponse;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.AvatarService;
import com.gamesaves.gamesaves.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AvatarServiceImpl implements AvatarService {

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final StorageService storageService;

    public AvatarServiceImpl(UserRepository userRepository, StorageService storageService) {
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Override
    public UserResponse uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Avatar file is required");
        if (file.getSize() > MAX_AVATAR_BYTES) throw new BadRequestException("Avatar must be 2 MiB or smaller");
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Avatar must be a JPEG, PNG, or WebP image");
        }

        User user = findUser(userId);
        String newKey = "avatars/" + userId + "/" + UUID.randomUUID() + ".jpg";
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (source == null) throw new BadRequestException("Invalid image file");
            byte[] normalizedImage = toSquareJpeg(source);
            storageService.store(newKey, normalizedImage);
            String oldKey = user.getAvatarKey();
            user.setAvatarKey(newKey);
            user.setAvatarUrl(storageService.getPublicUrl(newKey));
            userRepository.save(user);
            if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) storageService.delete(oldKey);
            return UserResponse.fromEntity(user);
        } catch (IOException e) {
            throw new BadRequestException("Failed to process avatar image");
        } catch (RuntimeException e) {
            if (storageService.exists(newKey)) storageService.delete(newKey);
            throw e;
        }
    }

    @Override
    public UserResponse removeAvatar(Long userId) {
        User user = findUser(userId);
        String oldKey = user.getAvatarKey();
        user.setAvatarKey(null);
        user.setAvatarUrl(null);
        userRepository.save(user);
        if (oldKey != null && !oldKey.isBlank()) storageService.delete(oldKey);
        return UserResponse.fromEntity(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private byte[] toSquareJpeg(BufferedImage source) throws IOException {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        int targetSide = Math.min(512, side);
        BufferedImage target = new BufferedImage(targetSide, targetSide, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, targetSide, targetSide, x, y, x + side, y + side, null);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(target, "jpeg", output);
        return output.toByteArray();
    }
}
