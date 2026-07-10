package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.service.SiteSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站点设置公开接口（无需登录）。
 *
 * <p>返回前端渲染所需的所有公开配置，如首页背景图 URL。
 * 管理员通过 {@code AdminController} 中的管理端点写入。
 */
@RestController
@RequestMapping("/api/site-settings")
public class SiteSettingController {

    private final SiteSettingService siteSettingService;

    public SiteSettingController(SiteSettingService siteSettingService) {
        this.siteSettingService = siteSettingService;
    }

    /**
     * 获取所有公开站点设置。
     * @return key-value 映射（如 background_image_url）
     */
    @GetMapping
    public ApiResponse<Map<String, String>> getPublicSettings() {
        Map<String, String> settings = siteSettingService.getPublicSettings();
        return ApiResponse.success(settings);
    }
}
