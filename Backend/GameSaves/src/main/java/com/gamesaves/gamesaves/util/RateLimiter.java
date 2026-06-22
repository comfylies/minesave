package com.gamesaves.gamesaves.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter using ConcurrentHashMap with time-windowed counters.
 * Works alongside download_logs for dual-layer IP-based download rate limiting.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final Map<String, DownloadCounter> counters = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.max-downloads-per-ip:3}")
    private int maxDownloads;

    @Value("${app.rate-limit.window-seconds:60}")
    private int windowSeconds;

    /**
     * Check if an IP is allowed to download. Thread-safe.
     * @param ip client IP address
     * @param articleId article being downloaded
     * @return true if allowed
     */
    public boolean tryAcquire(String ip, Long articleId) {
        String key = ip + ":" + articleId;
        DownloadCounter counter = counters.compute(key, (k, v) -> {
            if (v == null || v.isExpired(windowSeconds)) {
                return new DownloadCounter();
            }
            v.increment();
            return v;
        });
        boolean allowed = counter.getCount() <= maxDownloads;
        if (!allowed) {
            log.warn("Rate limit hit: ip={}, articleId={}, count={}", ip, articleId, counter.getCount());
        }
        return allowed;
    }

    /**
     * Periodically evict expired entries to prevent memory leak.
     */
    @Scheduled(fixedDelay = 60000)
    public void evictExpired() {
        int before = counters.size();
        counters.entrySet().removeIf(entry -> {
            DownloadCounter counter = entry.getValue();
            return counter != null && counter.isExpired(windowSeconds);
        });
        int after = counters.size();
        if (before != after) {
            log.debug("Rate limiter eviction: {} -> {} entries", before, after);
        }
    }

    private static class DownloadCounter {
        private final AtomicInteger count = new AtomicInteger(1);
        private final long timestamp = System.currentTimeMillis();

        void increment() {
            count.incrementAndGet();
        }

        int getCount() {
            return count.get();
        }

        boolean isExpired(int windowSeconds) {
            return System.currentTimeMillis() - timestamp > windowSeconds * 1000L;
        }
    }
}
