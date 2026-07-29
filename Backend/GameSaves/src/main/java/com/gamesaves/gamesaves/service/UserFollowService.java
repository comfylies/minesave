package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.FollowResponse;
import com.gamesaves.gamesaves.dto.response.FollowedUserResponse;

public interface UserFollowService {

    FollowResponse toggle(Long followingId, Long followerId);

    boolean isFollowing(Long followingId, Long followerId);

    PageDTO<FollowedUserResponse> getFollowing(Long followerId, int page, int size);
}
