-- ============================================================
-- 文章编辑次数追踪（每日编辑限制）
-- ============================================================
ALTER TABLE article
    ADD COLUMN last_edit_date DATE DEFAULT NULL AFTER error_message,
    ADD COLUMN daily_edit_count INT NOT NULL DEFAULT 0 AFTER last_edit_date;
