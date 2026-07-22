package com.gamesaves.gamesaves.service;

import org.springframework.web.multipart.MultipartFile;

public interface ChatImageService {

    void validate(MultipartFile image);

    ChatImageData store(MultipartFile image, Long conversationId, Long messageId);

    record ChatImageData(String originalKey, String thumbnailKey, int width, int height) {
    }
}
