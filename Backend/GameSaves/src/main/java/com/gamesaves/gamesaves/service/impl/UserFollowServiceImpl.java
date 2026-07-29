package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.FollowResponse;
import com.gamesaves.gamesaves.dto.response.FollowedUserResponse;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.entity.UserFollow;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.UserFollowRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.StorageService;
import com.gamesaves.gamesaves.service.UserFollowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserFollowServiceImpl implements UserFollowService {

    private final UserFollowRepository followRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public UserFollowServiceImpl(UserFollowRepository followRepository,
                                 UserRepository userRepository,
                                 StorageService storageService) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Override
    public FollowResponse toggle(Long followingId, Long followerId) {
        if (followingId.equals(followerId)) {
            throw new BadRequestException("You cannot follow yourself");
        }
        User target = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("User", followingId));
        if (!Boolean.TRUE.equals(target.getIsActive())) {
            throw new BadRequestException("Cannot follow an inactive user");
        }

        return followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .map(follow -> {
                    followRepository.delete(follow);
                    return new FollowResponse(false);
                })
                .orElseGet(() -> {
                    followRepository.save(UserFollow.builder()
                            .followerId(followerId)
                            .followingId(followingId)
                            .build());
                    return new FollowResponse(true);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(Long followingId, Long followerId) {
        return followRepository.findByFollowerIdAndFollowingId(followerId, followingId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<FollowedUserResponse> getFollowing(Long followerId, int page, int size) {
        long total = followRepository.countActiveFollowingByFollowerId(followerId);
        List<FollowedUserResponse> content = followRepository
                .findFollowedUsersByFollowerId(followerId, PageRequest.of(page, size))
                .stream()
                .map(user -> FollowedUserResponse.fromEntity(user, storageService))
                .toList();
        return PageDTO.of(content, page, size, total);
    }
}
