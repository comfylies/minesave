package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.DirectoryBrowseResponse;
import com.gamesaves.gamesaves.dto.response.FileEntryResponse;
import com.gamesaves.gamesaves.service.DownloadService;
import com.gamesaves.gamesaves.service.FileExplorerService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileExplorerService fileExplorerService;
    private final DownloadService downloadService;

    public FileController(FileExplorerService fileExplorerService,
                           DownloadService downloadService) {
        this.fileExplorerService = fileExplorerService;
        this.downloadService = downloadService;
    }

    /**
     * GitHub-style directory browsing.
     * GET /api/files/{articleId}/browse?path=
     * GET /api/files/{articleId}/browse?path=region/
     */
    @GetMapping("/{articleId}/browse")
    public ApiResponse<DirectoryBrowseResponse> browseDirectory(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "") String path) {
        DirectoryBrowseResponse result = fileExplorerService.browseDirectory(articleId, path);
        return ApiResponse.success(result);
    }

    /**
     * Get file metadata.
     */
    @GetMapping("/{articleId}/detail")
    public ApiResponse<FileEntryResponse> getFileDetail(
            @PathVariable Long articleId,
            @RequestParam String path) {
        FileEntryResponse file = fileExplorerService.getFileDetail(articleId, path)
                .orElse(null);
        if (file == null) {
            return ApiResponse.error(404, "File not found: " + path);
        }
        return ApiResponse.success(file);
    }

    /**
     * Preview file content (text or binary).
     */
    @GetMapping("/{articleId}/preview")
    public ResponseEntity<Resource> previewFile(
            @PathVariable Long articleId,
            @RequestParam String path) {
        FileEntryResponse fileDetail = fileExplorerService.getFileDetail(articleId, path)
                .orElse(null);
        if (fileDetail == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileExplorerService.getFileContent(articleId, path);

        // 根据文件扩展名返回正确的 MIME 类型（图片需要正确的 Content-Type 才能在 <img> 中显示）
        MediaType mediaType = getMediaType(fileDetail);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileDetail.getName() + "\"")
                .body(resource);
    }

    /**
     * Serve README images from readme/images/ directory.
     * GET /api/files/{articleId}/readme-image?path=screenshot.png
     */
    @GetMapping("/{articleId}/readme-image")
    public ResponseEntity<Resource> getReadmeImage(
            @PathVariable Long articleId,
            @RequestParam String path) {
        Resource resource = fileExplorerService.getReadmeImage(articleId, path);

        MediaType mediaType = getMediaTypeByExtension(path);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + path.substring(path.lastIndexOf('/') + 1) + "\"")
                .body(resource);
    }

    /**
     * Download the original ZIP archive (with rate limiting).
     */
    @GetMapping("/{articleId}/download")
    public ResponseEntity<Resource> downloadZip(
            @PathVariable Long articleId,
            HttpServletRequest request) {

        String ip = getClientIp(request);

        // Check rate limit
        downloadService.checkRateLimit(ip, articleId);

        // Record download
        String zipFilename = downloadService.recordDownload(articleId, request);

        Resource resource = fileExplorerService.getZipForDownload(articleId);

        String encodedFilename = URLEncoder.encode(zipFilename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        log.info("Download: article={}, ip={}, file={}", articleId, ip, zipFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFilename + "\"")
                .body(resource);
    }

    /**
     * 根据文件扩展名返回正确的 MIME 类型
     */
    private MediaType getMediaType(FileEntryResponse file) {
        if (file.getIsText() != null && file.getIsText()) {
            return MediaType.TEXT_PLAIN;
        }
        String name = file.getName();
        if (name == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (lower.endsWith(".bmp")) return MediaType.valueOf("image/bmp");
        if (lower.endsWith(".ico")) return MediaType.valueOf("image/x-icon");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".xml")) return MediaType.APPLICATION_XML;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".css")) return MediaType.valueOf("text/css");
        if (lower.endsWith(".js")) return MediaType.valueOf("application/javascript");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private MediaType getMediaTypeByExtension(String filename) {
        if (filename == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (lower.endsWith(".bmp")) return MediaType.valueOf("image/bmp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
