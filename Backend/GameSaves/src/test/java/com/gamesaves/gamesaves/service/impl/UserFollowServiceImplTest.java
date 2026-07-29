package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.UserFollowRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceImplTest {

    @Mock
    private UserFollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserFollowServiceImpl service;

    @Test
    void toggle_rejectsFollowingYourself() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.toggle(2L, 2L));

        assertTrue(error.getMessage().contains("yourself"));
    }

    @Test
    void toggle_createsFollowForActiveTarget() {
        User target = User.builder().id(9L).username("publisher").isActive(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(followRepository.findByFollowerIdAndFollowingId(2L, 9L)).thenReturn(Optional.empty());

        assertTrue(service.toggle(9L, 2L).isFollowing());

        verify(followRepository).save(argThat(follow ->
                follow.getFollowerId().equals(2L) && follow.getFollowingId().equals(9L)));
    }
}
