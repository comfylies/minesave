package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.config.S3Config;
import com.gamesaves.gamesaves.entity.SiteSetting;
import com.gamesaves.gamesaves.repository.SiteSettingRepository;
import com.gamesaves.gamesaves.service.SiteSettingService;
import com.gamesaves.gamesaves.service.StorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 站点设置服务实现 — 内存缓存 + 数据库持久化。
 *
 * <p>启动时加载全部设置到 {@link #cache}，读取全部走缓存（零 DB 查询）。
 * 管理员写入时先落库、再刷新缓存，保证一致性。
 *
 * <p>缓存中存储原始值（如 storage key），对外暴露时通过 {@link #getResolvedPublicSettings()}
 * 将 storage key 解析为可公开访问的 URL（预签名 URL 或本地 /storage/ 路径）。
 */
@Service
public class SiteSettingServiceImpl implements SiteSettingService {

    private static final Logger log = LoggerFactory.getLogger(SiteSettingServiceImpl.class);

    private final SiteSettingRepository repository;
    private final StorageService storageService;

    /** S3 配置（local 模式时为 null）——仅用于获取预签名 URL 有效期 */
    private final S3Config s3Config;

    /** 内存缓存：key → value（线程安全），存储原始 DB 值 */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 已解析 URL 缓存：key → (URL, 创建时间)。
     *
     * <p>预签名 URL 每次调用 AWS SDK 都会生成不同签名字符串，导致浏览器缓存失效。
     * 因此缓存已解析的 URL，只在接近过期时才重新生成，使同一 URL 能被浏览器复用。
     */
    private final ConcurrentHashMap<String, ResolvedUrlEntry> resolvedUrlCache = new ConcurrentHashMap<>();

    /** 预签名 URL 有效期的 90% 作为缓存刷新阈值（留 10% 余量防止边界过期） */
    private static final double RESOLVED_URL_REFRESH_RATIO = 0.9;

    /** 预签名 URL 默认有效期（毫秒），S3Config 不可用时使用（local 模式不走此逻辑） */
    private static final long DEFAULT_PUBLIC_URL_TTL_MS = TimeUnit.DAYS.toMillis(7);

    private static class ResolvedUrlEntry {
        final String url;
        final long createdAtMs;

        ResolvedUrlEntry(String url, long createdAtMs) {
            this.url = url;
            this.createdAtMs = createdAtMs;
        }
    }

    public SiteSettingServiceImpl(SiteSettingRepository repository, StorageService storageService,
                                  @Autowired(required = false) S3Config s3Config) {
        this.repository = repository;
        this.storageService = storageService;
        this.s3Config = s3Config;
    }

    /** 启动时加载所有设置到内存缓存 */
    @PostConstruct
    public void init() {
        refreshCache();
        log.info("Site settings cache loaded: {} entries", cache.size());
    }

    @Override
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public Map<String, String> getPublicSettings() {
        return new HashMap<>(cache);
    }

    @Override
    public Map<String, String> getResolvedPublicSettings() {
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getKey(), entry.getValue()));
        }
        return resolved;
    }

    /**
     * 将缓存中的原始值解析为可公开访问的 URL，带内存缓存以避免每次生成不同的预签名 URL。
     *
     * <p>如果值已经是完整 URL（http/https 或 /storage/ 开头），直接返回（兼容存量数据）。
     * 否则视为 storage key：
     * <ol>
     *   <li>检查 {@link #resolvedUrlCache} 中是否有尚未过期的缓存 URL，有则复用</li>
     *   <li>无缓存或缓存已接近过期 → 通过 StorageService 重新生成并缓存</li>
     * </ol>
     *
     * <p>这样浏览器每次拿到相同的 URL，可以命中本地缓存，避免重复下载。
     */
    private String resolveValue(String key, String value) {
        if (value == null || value.isBlank()) return value;
        // 已是完整 URL → 直接返回
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
            return value;
        }

        // 检查已解析 URL 缓存
        long now = System.currentTimeMillis();
        ResolvedUrlEntry cached = resolvedUrlCache.get(key);
        if (cached != null) {
            long maxAgeMs = s3Config != null
                    ? TimeUnit.MINUTES.toMillis(s3Config.getPublicUrlExpirationMinutes())
                    : DEFAULT_PUBLIC_URL_TTL_MS;
            long refreshThresholdMs = (long) (maxAgeMs * RESOLVED_URL_REFRESH_RATIO);
            long ageMs = now - cached.createdAtMs;
            if (ageMs < refreshThresholdMs) {
                return cached.url; // 缓存命中，复用同一 URL → 浏览器可缓存
            }
        }

        // storage key → 解析为可访问 URL 并缓存
        try {
            String url = storageService.getPublicUrl(value);
            resolvedUrlCache.put(key, new ResolvedUrlEntry(url, now));
            if (cached != null) {
                log.debug("Refreshed resolved URL cache for '{}' (previous URL was {} old)", key,
                        java.time.Duration.ofMillis(now - cached.createdAtMs));
            }
            return url;
        } catch (Exception e) {
            log.warn("Failed to resolve storage key '{}' to public URL: {}", value, e.getMessage());
            // 降级：返回缓存中的旧 URL（即使可能已过期）
            if (cached != null) return cached.url;
            return value;
        }
    }

    @Override
    @Transactional
    public void updateSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            Optional<SiteSetting> existing = repository.findBySettingKey(key);
            if (existing.isPresent()) {
                SiteSetting record = existing.get();
                record.setSettingValue(value);
                repository.save(record);
            } else {
                SiteSetting record = SiteSetting.builder()
                        .settingKey(key)
                        .settingValue(value)
                        .build();
                repository.save(record);
            }
        }
        refreshCache();
        resolvedUrlCache.clear(); // 设置变更后清除已解析 URL 缓存，下次请求重新生成
        log.info("Site settings updated: {} keys", settings.size());
    }

    /** 从数据库全量刷新缓存 */
    private void refreshCache() {
        cache.clear();
        for (SiteSetting record : repository.findAll()) {
            cache.put(record.getSettingKey(), record.getSettingValue());
        }
    }
}
