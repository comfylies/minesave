-- ============================================================
-- 迁移 002: users.role + article.status 添加 CHECK 约束
-- 日期: 2026-06-27
-- 说明: 数据库层面约束确保枚举值合法，防止应用层 bug 写入脏数据。
-- 执行: mysql -u root -p3256 gamesaving < migration_002_check_constraints.sql
-- ============================================================

-- 1. users.role CHECK 约束 (MySQL 8.0.16+ 支持)
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('admin', 'user'));

-- 2. article.status CHECK 约束
ALTER TABLE article ADD CONSTRAINT chk_article_status
    CHECK (status IN ('UPLOADING', 'EXTRACTING', 'READY', 'FAILED'));
