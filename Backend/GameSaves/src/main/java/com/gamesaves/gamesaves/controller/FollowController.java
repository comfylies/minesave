package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.FollowResponse;
import com.gamesaves.gamesaves.service.UserFollowService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class FollowController {

    private final UserFollowService userFollowService;

    public FollowController(UserFollowService userFollowService) {
        this.userFollowService = userFollowService;
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<FollowResponse> toggleFollow(@PathVariable Long id) {
        return ApiResponse.success(userFollowService.toggle(id, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/{id}/my-following")
    public ApiResponse<Map<String, Object>> getMyFollowing(@PathVariable Long id) {
        boolean following = StpUtil.isLogin()
                && userFollowService.isFollowing(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.success(Map.of("following", following));
    }
}
