package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.UserUpdateRequest;
import com.gamesaves.gamesaves.dto.response.UserResponse;

public interface UserService {

    UserResponse getUserById(Long id);

    UserResponse updateUser(Long id, UserUpdateRequest request);
}
