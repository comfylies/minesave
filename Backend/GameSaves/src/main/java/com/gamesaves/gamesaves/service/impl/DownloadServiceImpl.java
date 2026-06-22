package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.DownloadLog;
import com.gamesaves.gamesaves.exception.RateLimitException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.DownloadLogRepository;
import com.gamesaves.gamesaves.service.DownloadService;
import com.gamesaves.gamesaves.util.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class DownloadServiceImpl implements DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadServiceImpl.class);

    private final RateLimiter rateLimiter;
    private final DownloadLogRepository downloadLogRepository;
    private final ArticleRepository articleRepository;

    public DownloadServiceImpl(RateLimiter rateLimiter,
                                DownloadLogRepository downloadLogRepository,
                                ArticleRepository articleRepository) {
        this.rateLimiter = rateLimiter;
        this.downloadLogRepository = downloadLogRepository;
        this.articleRepository = articleRepository;
    }

    @Override
    public void checkRateLimit(String ip, Long articleId) {
        // Layer 1: Memory rate limiter
        if (!rateLimiter.tryAcquire(ip, articleId)) {
            throw new RateLimitException("Download limit reached. Please try again later.");
        }

        // Layer 2: Database audit check (last 60 seconds)
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long recentCount = downloadLogRepository.countByIpAddressAndDownloadedAtAfter(ip, oneMinuteAgo);
        if (recentCount >= 5) { // Hard cap from DB
            throw new RateLimitException("Download limit reached (DB check). Please try again later.");
        }
    }

    @Override
    public String recordDownload(Long articleId, HttpServletRequest request) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Log download
        DownloadLog log = DownloadLog.builder()
                .articleId(articleId)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();
        downloadLogRepository.save(log);

        // Increment counter
        article.setDownloadCount(article.getDownloadCount() + 1);
        articleRepository.save(article);

        // Get the zip filename for download
        return article.getZipFilename();
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
