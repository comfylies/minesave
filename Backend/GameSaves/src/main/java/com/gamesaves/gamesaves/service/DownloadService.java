package com.gamesaves.gamesaves.service;

import jakarta.servlet.http.HttpServletRequest;

public interface DownloadService {

    /**
     * Check if this IP is allowed to download this article (rate limiting).
     * Throws RateLimitException if limit exceeded.
     */
    void checkRateLimit(String ip, Long articleId);

    /**
     * Record a download event (log to DB + memory).
     */
    String recordDownload(Long articleId, HttpServletRequest request);
}
