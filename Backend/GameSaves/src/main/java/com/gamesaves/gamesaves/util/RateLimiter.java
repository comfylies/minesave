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
    private final Map<String, GlobalCounter> globalCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> ipBans = new ConcurrentHashMap<>();  // key → bannedUntil epoch ms

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
     * General-purpose IP-based rate limiter for any action (e.g. game creation).
     * @param ip client IP address
     * @param action action name for key prefix (e.g. "create-game")
     * @param maxRequests max requests in the window
     * @param windowSeconds time window in seconds
     * @return true if allowed
     */
    public boolean tryAcquireGlobal(String ip, String action, int maxRequests, int windowSeconds) {
        String key = "global:" + action + ":" + ip;
        GlobalCounter counter = globalCounters.compute(key, (k, v) -> {
            if (v == null || v.isExpired(windowSeconds)) {
                return new GlobalCounter();
            }
            v.increment();
            return v;
        });
        boolean allowed = counter.getCount() <= maxRequests;
        if (!allowed) {
            log.warn("Global rate limit hit: ip={}, action={}, count={}", ip, action, counter.getCount());
        }
        return allowed;
    }

    /**
     * Ban an IP for a specific action for {@code banSeconds} seconds.
     */
    public void banIp(String ip, String action, long banSeconds) {
        String key = "ban:" + action + ":" + ip;
        long bannedUntil = System.currentTimeMillis() + banSeconds * 1000L;
        ipBans.put(key, bannedUntil);
        // Also clear any existing counters for this IP+action so they start fresh after ban
        globalCounters.remove("global:" + action + ":" + ip);
        log.warn("IP banned: ip={}, action={}, duration={}s", ip, action, banSeconds);
    }

    /**
     * Check if an IP is currently banned for the given action.
     * @return true if banned (and not expired)
     */
    public boolean isIpBanned(String ip, String action) {
        String key = "ban:" + action + ":" + ip;
        Long bannedUntil = ipBans.get(key);
        if (bannedUntil == null) return false;
        if (System.currentTimeMillis() > bannedUntil) {
            ipBans.remove(key);  // lazy expire
            return false;
        }
        return true;
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

        // Evict expired global counters
        int gBefore = globalCounters.size();
        globalCounters.entrySet().removeIf(entry -> {
            GlobalCounter counter = entry.getValue();
            return counter != null && counter.isExpired(3600);
        });
        int gAfter = globalCounters.size();
        if (gBefore != gAfter) {
            log.debug("Global rate limiter eviction: {} -> {} entries", gBefore, gAfter);
        }

        // Evict expired IP bans
        int bBefore = ipBans.size();
        ipBans.entrySet().removeIf(entry ->
                System.currentTimeMillis() > entry.getValue());
        int bAfter = ipBans.size();
        if (bBefore != bAfter) {
            log.debug("IP ban eviction: {} -> {} entries", bBefore, bAfter);
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

    private static class GlobalCounter {
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
