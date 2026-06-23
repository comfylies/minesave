package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.response.AnnouncementResponse;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.service.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的公告接口（无需登录）
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/active")
    public ApiResponse<List<AnnouncementResponse>> getActiveAnnouncements() {
        List<AnnouncementResponse> announcements = announcementService.listActive();
        return ApiResponse.success(announcements);
    }
}
