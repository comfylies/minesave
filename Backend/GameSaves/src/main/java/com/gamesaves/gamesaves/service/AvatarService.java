package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {

    UserResponse uploadAvatar(Long userId, MultipartFile file);

    UserResponse removeAvatar(Long userId);
}
