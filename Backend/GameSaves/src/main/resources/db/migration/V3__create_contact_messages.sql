-- Create the contact-message table for databases that were already baselined
-- before the "联系我们" feature was introduced. New installations already
-- receive the table from V1, so this migration is deliberately idempotent.
CREATE TABLE IF NOT EXISTS contact_messages (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    user_id       BIGINT          NOT NULL,
    name          VARCHAR(100)    NOT NULL,
    email         VARCHAR(200)    NOT NULL,
    category      VARCHAR(50)     NOT NULL,
    subject       VARCHAR(200)    NOT NULL,
    message       MEDIUMTEXT      NOT NULL,
    status        VARCHAR(20)     NOT NULL DEFAULT 'pending',
    admin_reply   TEXT            DEFAULT NULL,
    ip_address    VARCHAR(45)     DEFAULT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cm_status (status),
    INDEX idx_cm_category (category),
    INDEX idx_cm_user_id (user_id),
    INDEX idx_cm_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='联系我们留言表 - 用户建议/Bug反馈/商务合作';
