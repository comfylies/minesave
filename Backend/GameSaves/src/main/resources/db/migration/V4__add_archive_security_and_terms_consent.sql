ALTER TABLE article
    ADD COLUMN security_level VARCHAR(20) NOT NULL DEFAULT 'SAFE' AFTER error_message;

ALTER TABLE users
    ADD COLUMN terms_version VARCHAR(30) NULL AFTER bio,
    ADD COLUMN terms_accepted_at DATETIME NULL AFTER terms_version;
