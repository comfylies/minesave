package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.ConversationReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationReadStateRepository extends JpaRepository<ConversationReadState, Long> {

    Optional<ConversationReadState> findByConversationIdAndUserId(Long conversationId, Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO conversation_read_states
                (conversation_id, user_id, last_read_message_id, created_at, updated_at)
            VALUES (:conversationId, :userId, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int ensureReadState(@Param("conversationId") long conversationId, @Param("userId") long userId);

    @Modifying
    @Query(value = """
            UPDATE conversation_read_states
            SET last_read_message_id = CASE
                    WHEN last_read_message_id IS NULL OR last_read_message_id < :messageId THEN :messageId
                    ELSE last_read_message_id
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE conversation_id = :conversationId AND user_id = :userId
            """, nativeQuery = true)
    int advanceReadState(@Param("conversationId") long conversationId,
                         @Param("userId") long userId,
                         @Param("messageId") long messageId);

    @Query(value = """
            SELECT COUNT(*)
            FROM direct_messages message
            JOIN conversation_read_states read_state ON read_state.conversation_id = message.conversation_id
            WHERE read_state.user_id = :userId
              AND message.sender_id <> :userId
              AND (read_state.last_read_message_id IS NULL OR message.id > read_state.last_read_message_id)
            """, nativeQuery = true)
    long countUnreadMessages(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*)
            FROM direct_messages message
            JOIN conversation_read_states read_state ON read_state.conversation_id = message.conversation_id
            WHERE read_state.conversation_id = :conversationId
              AND read_state.user_id = :userId
              AND message.sender_id <> :userId
              AND (read_state.last_read_message_id IS NULL OR message.id > read_state.last_read_message_id)
            """, nativeQuery = true)
    long countUnreadMessagesForConversation(
            @Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
