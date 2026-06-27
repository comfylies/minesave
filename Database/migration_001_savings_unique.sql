-- ============================================================
-- 迁移 001: savings.article_id 添加 UNIQUE 约束
-- 日期: 2026-06-27
-- 说明: ER 图标注 savings ↔ article 为 1-1 关系，
--       但 schema 仅有 INDEX，缺少 UNIQUE 约束。
--       此迁移修复数据完整性。
-- 执行: mysql -u root -p3256 gamesaving < migration_001_savings_unique.sql
-- ============================================================

-- 检查是否存在重复数据（如有则需手动处理）
-- SELECT article_id, COUNT(*) FROM savings GROUP BY article_id HAVING COUNT(*) > 1;

-- 添加 UNIQUE 约束
ALTER TABLE savings ADD UNIQUE INDEX uq_savings_article_id (article_id);
