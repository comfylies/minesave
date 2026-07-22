package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.service.ChatImageService;
import com.gamesaves.gamesaves.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatImageServiceImpl implements ChatImageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_PIXELS = 40_000_000;
    private static final int THUMBNAIL_MAX_EDGE = 720;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final StorageService storageService;

    public ChatImageServiceImpl(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) throw new BadRequestException("Chat image is required");
        if (image.getSize() > MAX_IMAGE_BYTES) throw new BadRequestException("Chat image must be 10 MiB or smaller");

        String extension = extensionOf(image.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Chat image must be a JPEG, PNG, or WebP file");
        }
        try {
            byte[] bytes = image.getBytes();
            if (!matchesMagic(bytes, extension)) throw new BadRequestException("Chat image content does not match its extension");
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null || decoded.getWidth() < 1 || decoded.getHeight() < 1
                    || (long) decoded.getWidth() * decoded.getHeight() > MAX_PIXELS) {
                throw new BadRequestException("Invalid or excessively large chat image");
            }
        } catch (IOException exception) {
            throw new BadRequestException("Failed to process chat image");
        }
    }

    @Override
    public ChatImageData store(MultipartFile image, Long conversationId, Long messageId) {
        validate(image);
        String extension = extensionOf(image.getOriginalFilename());

        String originalKey = "messages/" + conversationId + "/" + messageId + "/original." + extension;
        String thumbnailKey = "messages/" + conversationId + "/" + messageId + "/thumbnail.jpg";
        try {
            byte[] bytes = image.getBytes();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            storageService.store(originalKey, bytes);
            storageService.store(thumbnailKey, toThumbnailJpeg(decoded));
            return new ChatImageData(originalKey, thumbnailKey, decoded.getWidth(), decoded.getHeight());
        } catch (IOException exception) {
            deleteBoth(originalKey, thumbnailKey);
            throw new BadRequestException("Failed to process chat image");
        } catch (RuntimeException exception) {
            deleteBoth(originalKey, thumbnailKey);
            throw exception;
        }
    }

    private void deleteBoth(String originalKey, String thumbnailKey) {
        try { storageService.delete(originalKey); } catch (RuntimeException ignored) { }
        try { storageService.delete(thumbnailKey); } catch (RuntimeException ignored) { }
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean matchesMagic(byte[] data, String extension) {
        if (data.length < 12) return false;
        return switch (extension) {
            case "jpg", "jpeg" -> (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
            case "png" -> data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47;
            case "webp" -> data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
            default -> false;
        };
    }

    private byte[] toThumbnailJpeg(BufferedImage source) throws IOException {
        int largestEdge = Math.max(source.getWidth(), source.getHeight());
        double scale = Math.min(1D, (double) THUMBNAIL_MAX_EDGE / largestEdge);
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, "jpeg", output);
        return output.toByteArray();
    }
}
