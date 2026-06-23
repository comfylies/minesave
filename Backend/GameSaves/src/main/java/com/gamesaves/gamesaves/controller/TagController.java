package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.gamesaves.gamesaves.dto.request.TagCreateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.TagResponse;
import com.gamesaves.gamesaves.service.TagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** 获取所有标签（公开，供下拉选择器使用） */
    @GetMapping
    public ApiResponse<List<TagResponse>> getAllTags(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String q) {

        if (q != null && !q.isBlank()) {
            return ApiResponse.success(tagService.searchTags(q));
        }
        if (source != null && !source.isBlank()) {
            return ApiResponse.success(tagService.getTagsBySource(source));
        }
        return ApiResponse.success(tagService.getAllTags());
    }

    /** 创建用户标签（需登录，普通用户创建的标签 source=user） */
    @PostMapping
    @SaCheckLogin
    public ApiResponse<TagResponse> createTag(@Valid @RequestBody TagCreateRequest request) {
        // 普通用户只能创建 user 标签
        if (request.getSource() == null) {
            request.setSource("user");
        }
        TagResponse tag = tagService.createTag(request);
        return ApiResponse.success("Tag created", tag);
    }
}
