ALTER TABLE direct_conversations
    ADD CONSTRAINT chk_direct_conversations_normalized_pair
        CHECK (user_one_id < user_two_id);
