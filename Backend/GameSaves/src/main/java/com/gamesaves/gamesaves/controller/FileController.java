package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.DirectoryBrowseResponse;
import com.gamesaves.gamesaves.dto.response.FileEntryResponse;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.service.DownloadService;
import com.gamesaves.gamesaves.service.DownloadVerificationService;
import com.gamesaves.gamesaves.service.FileExplorerService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;


@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileExplorerService fileExplorerService;
    private final DownloadService downloadService;
    private final DownloadVerificationService downloadVerificationService;

    public FileController(FileExplorerService fileExplorerService,
                           DownloadService downloadService,
                           DownloadVerificationService downloadVerificationService) {
        this.fileExplorerService = fileExplorerService;
        this.downloadService = downloadService;
        this.downloadVerificationService = downloadVerificationService;
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
     * 查询下载信息 — 是否需要验证码、文件大小、今日剩余次数。
     * GET /api/files/{articleId}/download/info
     */
    @GetMapping("/{articleId}/download/info")
    public ApiResponse<DownloadVerificationService.DownloadInfo> getDownloadInfo(
            @PathVariable Long articleId,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        DownloadVerificationService.DownloadInfo info = downloadVerificationService.getDownloadInfo(articleId, ip);
        return ApiResponse.success(info);
    }

    /**
     * 验证图形验证码并获取下载 token（大文件下载）。
     * POST /api/files/{articleId}/download/verify-captcha
     */
    @PostMapping("/{articleId}/download/verify-captcha")
    public ApiResponse<DownloadVerificationService.VerifyResult> verifyDownloadCaptcha(
            @PathVariable Long articleId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String captchaKey = body.get("captchaKey");
        String captchaCode = body.get("captchaCode");
        if (captchaKey == null || captchaKey.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BadRequestException("验证码不能为空");
        }
        String ip = getClientIp(request);
        DownloadVerificationService.VerifyResult result =
                downloadVerificationService.verifyCaptchaAndGenerateToken(ip, articleId, captchaKey, captchaCode);
        return ApiResponse.success(result);
    }

    /**
     * Download the original ZIP archive (with rate limiting + captcha for large files).
     * Uses 302 redirect — local mode redirects to /storage/..., COS mode to pre-signed URL.
     *
     * <p>大文件（超过 captcha-threshold）需要先通过 /verify-captcha 获取 token，
     * 然后携带 token 参数访问。
     */
    @GetMapping("/{articleId}/download")
    public ResponseEntity<Void> downloadZip(
            @PathVariable Long articleId,
            @RequestParam(required = false) String token,
            HttpServletRequest request) {

        String ip = getClientIp(request);

        // ── 大文件下载验证码检查 ──
        if (downloadVerificationService.requiresCaptcha(articleId)) {
            boolean tokenValid = downloadVerificationService.validateAndConsumeToken(token, ip, articleId);
            if (!tokenValid) {
                throw new BadRequestException(
                        "本存档超过 500MB，需要验证码验证后才能下载。请先获取验证码并通过验证。");
            }
        }

        // Check rate limit
        downloadService.checkRateLimit(ip, articleId);

        // Record download
        downloadService.recordDownload(articleId, request);

        // Get download URL (pre-signed URL for COS, /storage/ path for local)
        String downloadUrl = fileExplorerService.getZipDownloadUrl(articleId)
                .orElseThrow(() -> new com.gamesaves.gamesaves.exception.ResourceNotFoundException(
                        "ZIP not available for download"));

        // URL-encode 非 ASCII 字符，HTTP Location 头只允许 ASCII
        String safeUrl = encodePathForHeader(downloadUrl);

        log.info("Download redirect: article={}, ip={}, url={}", articleId, ip,
                safeUrl.substring(0, Math.min(80, safeUrl.length())) + "...");

        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, safeUrl)
                .build();
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
            // 取最右侧非空IP（最近的代理地址，无法被客户端伪造）
            String[] parts = xForwardedFor.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = parts[i].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * URL-encode the filename segment so the URL is safe for HTTP Location header
     * (which requires ASCII-only).
     *
     * <p>For external HTTP URLs (COS pre-signed URLs), the SDK has already
     * URL-encoded non-ASCII characters — skip re-encoding to avoid double-encoding
     * (which breaks the signature). For local /storage/ paths, encode the filename
     * segment so Chinese characters are safe in the Location header.
     */
    private String encodePathForHeader(String url) {
        // COS pre-signed URLs are already properly encoded by the AWS SDK.
        // Re-encoding would turn %E8 → %25E8, breaking the signature.
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0) return url;

        String path = url.substring(0, lastSlash + 1);
        String filename = url.substring(lastSlash + 1);

        // Split off query string if present
        String query = "";
        int queryIdx = filename.indexOf('?');
        if (queryIdx >= 0) {
            query = filename.substring(queryIdx);
            filename = filename.substring(0, queryIdx);
        }

        String encoded = UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
        return path + encoded + query;
    }
}
