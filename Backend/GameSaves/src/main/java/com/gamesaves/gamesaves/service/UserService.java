package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.LoginRequest;
import com.gamesaves.gamesaves.dto.request.RegisterRequest;
import com.gamesaves.gamesaves.dto.request.UserUpdateRequest;
import com.gamesaves.gamesaves.dto.response.LoginResponse;
import com.gamesaves.gamesaves.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserResponse getUserById(Long id);

    UserResponse updateUser(Long id, UserUpdateRequest request);

    List<UserResponse> getAllUsers();
}
