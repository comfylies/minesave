-- ============================================================
-- 站点设置表 — key-value 模式，可扩展任意设置项
-- ============================================================
CREATE TABLE IF NOT EXISTS site_settings (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始种子数据：首页背景图（默认为空）
INSERT INTO site_settings (setting_key, setting_value)
VALUES ('background_image_url', '');
