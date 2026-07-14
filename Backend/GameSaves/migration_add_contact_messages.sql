-- ============================================================
-- 联系我们留言表
-- 用于用户提交建议、Bug反馈、商务合作等
-- ============================================================
CREATE TABLE IF NOT EXISTS contact_messages (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL COMMENT '提交用户ID（必须登录，禁止匿名）',
    name          VARCHAR(100) NOT NULL COMMENT '联系人姓名',
    email         VARCHAR(200) NOT NULL COMMENT '联系邮箱',
    category      VARCHAR(50) NOT NULL COMMENT '类别：suggestion/bug/business/other',
    subject       VARCHAR(200) NOT NULL COMMENT '主题',
    message       MEDIUMTEXT NOT NULL COMMENT 'MD格式留言内容（可含图片引用）',
    status        VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/resolved/closed',
    admin_reply   TEXT NULL COMMENT '管理员回复（预留）',
    ip_address    VARCHAR(45) NULL COMMENT '提交者IP地址',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cm_status (status),
    INDEX idx_cm_category (category),
    INDEX idx_cm_user_id (user_id),
    INDEX idx_cm_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
