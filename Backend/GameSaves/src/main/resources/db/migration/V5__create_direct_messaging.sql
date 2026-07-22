CREATE TABLE direct_conversations (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_one_id     BIGINT NOT NULL,
    user_two_id     BIGINT NOT NULL,
    last_message_id BIGINT NULL,
    last_message_at DATETIME NULL,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_direct_conversations_pair (user_one_id, user_two_id),
    KEY idx_direct_conversations_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE direct_messages (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id     BIGINT NOT NULL,
    sender_id           BIGINT NOT NULL,
    message_type        VARCHAR(20) NOT NULL,
    content             VARCHAR(4000) NULL,
    image_original_key  VARCHAR(500) NULL,
    image_thumbnail_key VARCHAR(500) NULL,
    image_width         INT NULL,
    image_height        INT NULL,
    created_at          DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_direct_messages_conversation_id_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversation_read_states (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id      BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    last_read_message_id BIGINT NULL,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_read_states_conversation_user (conversation_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
