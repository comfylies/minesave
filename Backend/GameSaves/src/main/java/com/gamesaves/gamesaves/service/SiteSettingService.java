package com.gamesaves.gamesaves.service;

import java.util.Map;
import java.util.Optional;

/**
 * 站点设置服务。
 *
 * <p>公开方法供 Controller 调用；管理员通过 AdminController 写入。
 * 实现层使用内存缓存 {@code ConcurrentHashMap} 加速读取。
 */
public interface SiteSettingService {

    /**
     * 获取单个设置值。
     * @param key 设置键名
     * @return 设置值，不存在时返回 {@code Optional.empty()}
     */
    Optional<String> getValue(String key);

    /**
     * 获取所有公开设置（用于公开 API）。
     * @return key → value 映射
     */
    Map<String, String> getPublicSettings();

    /**
     * 获取所有公开设置，并将存储 key 解析为可公开访问的 URL。
     *
     * <p>缓存中存的是原始存储 key（如 {@code site/background_xxx.jpg}），
     * 本方法通过 {@code StorageService} 将其解析为预签名 URL 或本地路径。
     * 已经是完整 URL 的值（http/https 或 /storage/ 开头）原样返回。
     *
     * @return 解析后的 key → value 映射
     */
    Map<String, String> getResolvedPublicSettings();

    /**
     * 批量更新设置（仅管理员）。
     * <p>每个 key 执行 upsert：存在则更新 value，不存在则插入新记录。
     * 写入后自动刷新缓存。
     * @param settings key → value 映射
     */
    void updateSettings(Map<String, String> settings);
}
