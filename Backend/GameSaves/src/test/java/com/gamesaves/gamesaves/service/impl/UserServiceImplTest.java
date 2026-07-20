package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.UserUpdateRequest;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void updateUser_sanitizesNicknameAndBioBeforeSaving() {
        User user = User.builder().id(7L).username("tester").email("tester@example.com").build();
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("<script>alert(1)</script>Alice");
        request.setBio("<img src=x onerror=alert(1)>safe bio");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateUser(7L, request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertFalse(savedUser.getValue().getNickname().contains("<script>"));
        assertFalse(savedUser.getValue().getBio().contains("onerror"));
    }
}
