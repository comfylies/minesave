package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.ConversationReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationReadStateRepository extends JpaRepository<ConversationReadState, Long> {

    Optional<ConversationReadState> findByConversationIdAndUserId(Long conversationId, Long userId);

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
