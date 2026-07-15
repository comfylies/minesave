-- 文章好评/差评功能
CREATE TABLE IF NOT EXISTS article_votes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    vote_type   VARCHAR(4) NOT NULL COMMENT 'UP=好评, DOWN=差评',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_user (article_id, user_id),
    INDEX idx_article_id (article_id),
    INDEX idx_user_id (user_id),
    CONSTRAINT fk_vote_article FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE,
    CONSTRAINT fk_vote_user    FOREIGN KEY (user_id)    REFERENCES users(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 文章表新增计数列
ALTER TABLE article
    ADD COLUMN upvote_count   INT NOT NULL DEFAULT 0 AFTER download_count,
    ADD COLUMN downvote_count INT NOT NULL DEFAULT 0 AFTER upvote_count;
