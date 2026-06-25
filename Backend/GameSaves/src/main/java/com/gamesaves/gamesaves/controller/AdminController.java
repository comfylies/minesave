package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.request.AnnouncementCreateRequest;
import com.gamesaves.gamesaves.dto.request.TagCreateRequest;
import com.gamesaves.gamesaves.dto.response.*;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.service.AdminService;
import com.gamesaves.gamesaves.service.AnnouncementService;
import com.gamesaves.gamesaves.service.CleanupScheduler;
import com.gamesaves.gamesaves.service.SafePathService;
import com.gamesaves.gamesaves.service.SearchSyncService;
import com.gamesaves.gamesaves.service.StorageService;
import com.gamesaves.gamesaves.service.TagService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@SaCheckLogin
public class AdminController {

    private final AdminService adminService;
    private final AnnouncementService announcementService;
    private final TagService tagService;
    private final SearchSyncService searchSyncService;
    private final SafePathService safePathService;
    private final CleanupScheduler cleanupScheduler;
    private final StorageService storageService;

    public AdminController(AdminService adminService, AnnouncementService announcementService,
                           TagService tagService, SearchSyncService searchSyncService,
                           SafePathService safePathService,
                           CleanupScheduler cleanupScheduler,
                           StorageService storageService) {
        this.adminService = adminService;
        this.announcementService = announcementService;
        this.tagService = tagService;
        this.searchSyncService = searchSyncService;
        this.safePathService = safePathService;
        this.cleanupScheduler = cleanupScheduler;
        this.storageService = storageService;
    }

    @GetMapping("/dashboard")
    @SaCheckPermission("user:manage")
    public ApiResponse<DashboardStatsResponse> getDashboard() {
        DashboardStatsResponse stats = adminService.getDashboardStats();
        return ApiResponse.success(stats);
    }

    @GetMapping("/users")
    @SaCheckPermission("user:manage")
    public ApiResponse<Page<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<AdminUserResponse> users = adminService.listUsers(
                PageRequest.of(page, size, Sort.by("createdAt").descending()), keyword);
        return ApiResponse.success(users);
    }

    @PutMapping("/users/{id}/ban")
    @SaCheckPermission("user:manage")
    public ApiResponse<AdminUserResponse> toggleUserBan(@PathVariable Long id) {
        AdminUserResponse user = adminService.toggleUserBan(id);
        return ApiResponse.success(user);
    }

    @GetMapping("/articles")
    @SaCheckPermission("article:manage")
    public ApiResponse<Page<AdminArticleResponse>> listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Page<AdminArticleResponse> articles = adminService.listArticles(
                PageRequest.of(page, size, Sort.by("createdAt").descending()), keyword, status);
        return ApiResponse.success(articles);
    }

    @DeleteMapping("/articles/{id}")
    @SaCheckPermission("article:manage")
    public ApiResponse<String> deleteArticle(@PathVariable Long id) {
        adminService.deleteArticle(id);
        return ApiResponse.success("Article deleted", "ok");
    }

    // ==================== 搜索索引管理 ====================

    /** 全量重建搜索索引 */
    @PostMapping("/search/reindex")
    @SaCheckPermission("user:manage")
    public ApiResponse<Map<String, Object>> reindex() {
        long start = System.currentTimeMillis();
        int count = searchSyncService.rebuildAll();
        long elapsed = System.currentTimeMillis() - start;
        return ApiResponse.success(Map.of(
                "documents", count,
                "elapsedMs", elapsed
        ));
    }

    // ==================== 公告管理 ====================

    /** 获取所有公告（含禁用） */
    @GetMapping("/announcements")
    public ApiResponse<List<AnnouncementResponse>> listAnnouncements() {
        return ApiResponse.success(announcementService.listAll());
    }

    /** 创建公告 */
    @PostMapping("/announcements")
    public ApiResponse<AnnouncementResponse> createAnnouncement(
            @Valid @RequestBody AnnouncementCreateRequest request) {
        Long authorId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(announcementService.create(request, authorId));
    }

    /** 更新公告 */
    @PutMapping("/announcements/{id}")
    public ApiResponse<AnnouncementResponse> updateAnnouncement(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementCreateRequest request) {
        return ApiResponse.success(announcementService.update(id, request));
    }

    /** 删除公告 */
    @DeleteMapping("/announcements/{id}")
    public ApiResponse<String> deleteAnnouncement(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResponse.success("Announcement deleted", "ok");
    }

    /** 切换公告启用/禁用 */
    @PutMapping("/announcements/{id}/toggle")
    public ApiResponse<AnnouncementResponse> toggleAnnouncement(@PathVariable Long id) {
        return ApiResponse.success(announcementService.toggleActive(id));
    }

    /** 上传公告图片 */
    @PostMapping("/announcements/upload-image")
    public ApiResponse<Map<String, String>> uploadAnnouncementImage(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("文件为空");
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("只允许上传图片文件");
        }

        // 校验大小（最大 10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("图片大小不能超过 10MB");
        }

        try {
            // 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String ext = ".png";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString().substring(0, 8) + ext;

            // 保存到临时文件，上传到 storage
            Path tempFile = Files.createTempFile("ann-img-", ext);
            file.transferTo(tempFile);
            String key = "announcements/images/" + filename;
            storageService.storeFromPath(key, tempFile);
            Files.deleteIfExists(tempFile);

            // 返回访问 URL
            String url = storageService.getPublicUrl(key);
            return ApiResponse.success(Map.of(
                    "url", url,
                    "markdown", "![](" + url + ")",
                    "filename", filename
            ));
        } catch (IOException e) {
            throw new BadRequestException("图片上传失败: " + e.getMessage());
        }
    }

    // ==================== 标签管理 ====================

    /** 创建预设标签（admin 专属） */
    @PostMapping("/tags")
    @SaCheckPermission("tag:manage")
    public ApiResponse<TagResponse> createTag(@Valid @RequestBody TagCreateRequest request) {
        if (request.getSource() == null) {
            request.setSource("admin");
        }
        return ApiResponse.success("Tag created", tagService.createTag(request));
    }

    /** 更新标签 */
    @PutMapping("/tags/{id}")
    @SaCheckPermission("tag:manage")
    public ApiResponse<TagResponse> updateTag(@PathVariable Long id,
                                               @Valid @RequestBody TagCreateRequest request) {
        return ApiResponse.success(tagService.updateTag(id, request));
    }

    /** 删除标签 */
    @DeleteMapping("/tags/{id}")
    @SaCheckPermission("tag:manage")
    public ApiResponse<String> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ApiResponse.success("Tag deleted", "ok");
    }

    // ==================== 标准结构管理 ====================

    /** 上传游戏标准文件夹结构 ZIP，建立路径白名单 */
    @PostMapping("/games/{gameId}/safe-structure")
    @SaCheckPermission("game:manage")
    public ApiResponse<Map<String, Object>> uploadSafeStructure(
            @PathVariable Long gameId,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("文件为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new BadRequestException("只允许上传 .zip 文件");
        }

        try {
            int count = safePathService.uploadSafeStructure(gameId, file.getInputStream());
            return ApiResponse.success(Map.of(
                    "gameId", gameId,
                    "pathCount", count,
                    "message", "标准结构已更新，共 " + count + " 条路径"
            ));
        } catch (java.io.IOException e) {
            throw new BadRequestException("文件读取失败: " + e.getMessage());
        }
    }

    /** 查询游戏的标准结构路径数量 */
    @GetMapping("/games/{gameId}/safe-structure")
    @SaCheckPermission("game:manage")
    public ApiResponse<Map<String, Object>> getSafeStructure(@PathVariable Long gameId) {
        long count = safePathService.getPathCount(gameId);
        return ApiResponse.success(Map.of(
                "gameId", gameId,
                "pathCount", count,
                "hasStructure", count > 0
        ));
    }

    /** 上传公告 Markdown 文件，返回文件内容 */
    @PostMapping("/announcements/upload-md")
    public ApiResponse<Map<String, String>> uploadAnnouncementMd(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("文件为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".md")) {
            throw new BadRequestException("只允许上传 .md 文件");
        }

        try {
            // 保留原始文件名，加时间戳防重名
            String baseName = originalName.replaceAll("\\.md$", "");
            String timestamp = String.valueOf(System.currentTimeMillis()).substring(0, 10);
            String savedName = baseName + "_" + timestamp + ".md";

            // 读取内容，上传到 storage
            byte[] mdBytes = file.getBytes();
            String content = new String(mdBytes, java.nio.charset.StandardCharsets.UTF_8);
            String key = "announcements/md/" + savedName;
            storageService.store(key, mdBytes);

            return ApiResponse.success(Map.of(
                    "content", content,
                    "filename", savedName,
                    "path", storageService.getPublicUrl(key)
            ));
        } catch (IOException e) {
            throw new BadRequestException("文件上传失败: " + e.getMessage());
        }
    }

    // ==================== 失败存档清理 ====================

    private volatile long lastManualTriggerTime = 0;

    /** 手动触发清理 */
    @PostMapping("/cleanup/trigger")
    @SaCheckPermission("user:manage")
    public ApiResponse<Map<String, Object>> triggerCleanup(
            @RequestParam(defaultValue = "all") String mode) {

        // Rate limit: 60s between manual triggers
        long now = System.currentTimeMillis();
        long elapsed = now - lastManualTriggerTime;
        if (elapsed < 60_000) {
            throw new BadRequestException("清理间隔需大于 60 秒，请 " + (60 - elapsed / 1000) + " 秒后再试");
        }
        lastManualTriggerTime = now;

        if (!mode.equals("all") && !mode.equals("failed-only")) {
            throw new BadRequestException("无效的清理模式，仅支持 all 或 failed-only");
        }

        CleanupScheduler.CleanupProgress progress = cleanupScheduler.cleanup(mode);
        return ApiResponse.success("清理完成", Map.of(
                "mode", mode,
                "batchesCompleted", progress.batchesCompleted(),
                "totalDeleted", progress.totalDeleted(),
                "durationMs", progress.durationMs()
        ));
    }

    /** 查询清理进度 */
    @GetMapping("/cleanup/status")
    @SaCheckPermission("user:manage")
    public ApiResponse<Map<String, Object>> cleanupStatus() {
        CleanupScheduler.CleanupProgress progress = cleanupScheduler.getStatus();
        return ApiResponse.success(Map.of(
                "completed", progress.completed(),
                "batchesCompleted", progress.batchesCompleted(),
                "totalDeleted", progress.totalDeleted(),
                "estimatedRemaining", progress.estimatedRemaining(),
                "durationMs", progress.durationMs()
        ));
    }
}
