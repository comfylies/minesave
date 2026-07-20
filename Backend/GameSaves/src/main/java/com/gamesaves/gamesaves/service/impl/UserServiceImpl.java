package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.UserUpdateRequest;
import com.gamesaves.gamesaves.dto.response.UserResponse;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.UserService;
import com.gamesaves.gamesaves.util.XssFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        boolean publicView = !UserResponse.canViewFullInfo(id);
        return UserResponse.fromEntity(user, publicView);
    }

    @Override
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (request.getNickname() != null) {
            user.setNickname(XssFilter.sanitize(request.getNickname()));
        }
        if (request.getPhone() != null) {
            if (!request.getPhone().equals(user.getPhone())
                    && userRepository.existsByPhone(request.getPhone())) {
                throw new BadRequestException("Phone already in use");
            }
            user.setPhone(request.getPhone());
        }
        if (request.getBio() != null) {
            user.setBio(XssFilter.sanitize(request.getBio()));
        }

        return UserResponse.fromEntity(userRepository.save(user));
    }
}
