package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.FollowedUserResponse;
import com.gamesaves.gamesaves.service.UserFollowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
public class MyFollowController {

    private final UserFollowService userFollowService;

    public MyFollowController(UserFollowService userFollowService) {
        this.userFollowService = userFollowService;
    }

    @GetMapping
    public ApiResponse<PageDTO<FollowedUserResponse>> getFollowing(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userFollowService.getFollowing(StpUtil.getLoginIdAsLong(), page, size));
    }
}
