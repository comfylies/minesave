package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.SiteSetting;
import com.gamesaves.gamesaves.repository.SiteSettingRepository;
import com.gamesaves.gamesaves.service.SiteSettingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点设置服务实现 — 内存缓存 + 数据库持久化。
 *
 * <p>启动时加载全部设置到 {@link #cache}，读取全部走缓存（零 DB 查询）。
 * 管理员写入时先落库、再刷新缓存，保证一致性。
 */
@Service
public class SiteSettingServiceImpl implements SiteSettingService {

    private static final Logger log = LoggerFactory.getLogger(SiteSettingServiceImpl.class);

    private final SiteSettingRepository repository;

    /** 内存缓存：key → value（线程安全） */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public SiteSettingServiceImpl(SiteSettingRepository repository) {
        this.repository = repository;
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
