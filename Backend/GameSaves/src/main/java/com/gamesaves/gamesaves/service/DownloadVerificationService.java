package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.DownloadLog;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.CaptchaValidationException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.DownloadLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大文件下载验证码服务 — 超过阈值的存档下载需要图形验证码验证。
 *
 * <p>设计要点：
 * <ul>
 *   <li>无需登录 — 以 IP 标识用户</li>
 *   <li>验证码通过后发放一次性下载 token（5 分钟有效）</li>
 *   <li>每日每 IP 最多 N 次大文件下载（默认 3 次）</li>
 *   <li>小文件（≤ 阈值）不受影响，直接下载</li>
 * </ul>
 */
@Service
public class DownloadVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DownloadVerificationService.class);

    private final ArticleRepository articleRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final AuthService authService;

    /** 下载 token 缓存：token → TokenEntry */
    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    @Value("${app.download.captcha-threshold:524288000}")
    private long captchaThreshold;

    @Value("${app.download.max-large-downloads-per-day:3}")
    private int maxLargeDownloadsPerDay;

    /** Token 有效期（毫秒）：5 分钟 */
    private static final long TOKEN_TTL_MS = 5 * 60 * 1000;

    public DownloadVerificationService(ArticleRepository articleRepository,
                                        DownloadLogRepository downloadLogRepository,
                                        AuthService authService) {
        this.articleRepository = articleRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.authService = authService;
    }

    /**
     * 查询下载信息 — 是否需要验证码、文件大小、今日剩余次数。
     */
    public DownloadInfo getDownloadInfo(Long articleId, String ip) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        long fileSize = article.getFileSize() != null ? article.getFileSize() : 0;
        boolean requiresCaptcha = fileSize > captchaThreshold;
        int dailyRemaining = requiresCaptcha ? getDailyRemaining(ip) : -1;

        return new DownloadInfo(requiresCaptcha, fileSize, dailyRemaining);
    }

    /**
     * 验证图形验证码并生成下载 token。
     *
     * @return {downloadToken, dailyRemaining}
     * @throws CaptchaValidationException 验证码错误或过期
     * @throws BadRequestException        今日下载次数已用完
     */
    public VerifyResult verifyCaptchaAndGenerateToken(String ip, Long articleId,
                                                       String captchaKey, String captchaCode) {
        // 1. 验证图形验证码（委托给 AuthService）
        authService.validateCaptcha(captchaKey, captchaCode);

        // 2. 检查该 IP 是否还在限流之外（防止并发绕过）
        int remaining = getDailyRemaining(ip);
        if (remaining <= 0) {
            throw new BadRequestException(
                    "今日大文件下载次数已用完（" + maxLargeDownloadsPerDay + " 次/天），请明天再试");
        }

        // 3. 生成一次性下载 token
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenCache.put(token, new TokenEntry(token, ip, articleId,
                System.currentTimeMillis() + TOKEN_TTL_MS));
        log.info("Download token generated: ip={}, articleId={}, remaining={}", ip, articleId, remaining - 1);

        // 定期清理过期 token
        if (tokenCache.size() > 500) {
            tokenCache.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().expireTime);
        }

        return new VerifyResult(token, remaining - 1);
    }

    /**
     * 验证并消费下载 token。成功返回 true，失败返回 false。
     * Token 一次性使用（验证后删除）。
     */
    public boolean validateAndConsumeToken(String token, String ip, Long articleId) {
        if (token == null || token.isBlank()) {
            return false;
        }

        TokenEntry entry = tokenCache.remove(token);
        if (entry == null) {
            log.debug("Download token not found: ip={}, articleId={}", ip, articleId);
            return false;
        }

        if (System.currentTimeMillis() > entry.expireTime) {
            log.info("Download token expired: ip={}, articleId={}", ip, articleId);
            return false;
        }

        if (!entry.ip.equals(ip) || !entry.articleId.equals(articleId)) {
            log.warn("Download token mismatch: expected ip={} articleId={}, got ip={} articleId={}",
                    entry.ip, entry.articleId, ip, articleId);
            return false;
        }

        log.info("Download token consumed: ip={}, articleId={}", ip, articleId);
        return true;
    }

    /**
     * 查询该 IP 今日剩余大文件下载次数。
     */
    public int getDailyRemaining(String ip) {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long todayCount = downloadLogRepository.countByIpAddressAndDownloadedAtAfter(ip, todayStart);
        return Math.max(0, maxLargeDownloadsPerDay - (int) todayCount);
    }

    /**
     * 该文件是否需要下载验证码。
     */
    public boolean requiresCaptcha(Long articleId) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null) return false;
        long fileSize = article.getFileSize() != null ? article.getFileSize() : 0;
        return fileSize > captchaThreshold;
    }

    // ── 内部类型 ──

    /** 下载信息 */
    public record DownloadInfo(boolean requiresCaptcha, long fileSize, int dailyRemaining) {}

    /** 验证码验证结果 */
    public record VerifyResult(String downloadToken, int dailyRemaining) {}

    /** Token 缓存条目 */
    private record TokenEntry(String token, String ip, Long articleId, long expireTime) {}
}
