package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 联系我们图片上传控制器。
 * 借鉴 AdminController.uploadAnnouncementImage() 的模式。
 */
@RestController
@RequestMapping("/api/contact")
@SaCheckLogin
public class ContactImageController {

    private static final Logger log = LoggerFactory.getLogger(ContactImageController.class);

    private final StorageService storageService;

    public ContactImageController(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传联系留言中的图片。
     * 选中图片后即时上传到服务器，返回 URL + MD 格式引用。
     */
    @PostMapping("/upload-image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
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
                ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
                // 仅允许常见图片扩展名
                if (!ext.matches("\\.(png|jpg|jpeg|gif|webp|bmp)$")) {
                    ext = ".png";
                }
            }
            String filename = UUID.randomUUID().toString().substring(0, 8) + ext;

            // 保存到临时文件，上传到 storage
            Path tempFile = Files.createTempFile("contact-img-", ext);
            file.transferTo(tempFile);
            String key = "contact/images/" + filename;
            storageService.storeFromPath(key, tempFile);
            Files.deleteIfExists(tempFile);

            // 返回访问 URL
            String url = storageService.getPublicUrl(key);
            log.info("Contact image uploaded: key={}", key);
            return ApiResponse.success(Map.of(
                    "url", url,
                    "markdown", "![](" + url + ")",
                    "filename", filename
            ));
        } catch (IOException e) {
            throw new BadRequestException("图片上传失败: " + e.getMessage());
        }
    }
}
