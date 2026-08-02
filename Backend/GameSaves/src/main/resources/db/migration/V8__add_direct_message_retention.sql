ALTER TABLE direct_messages
    ADD COLUMN image_expired_at DATETIME NULL,
    ADD KEY idx_direct_messages_image_retention (image_expired_at, created_at);

ALTER TABLE direct_conversations
    ADD COLUMN history_purged_at DATETIME NULL;
