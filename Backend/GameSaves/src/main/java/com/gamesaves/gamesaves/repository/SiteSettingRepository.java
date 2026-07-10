package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 站点设置数据访问 — 基于 JPA，按 key 精确查询。
 */
@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {

    /** 按 settingKey 精确查找 */
    Optional<SiteSetting> findBySettingKey(String settingKey);
}
