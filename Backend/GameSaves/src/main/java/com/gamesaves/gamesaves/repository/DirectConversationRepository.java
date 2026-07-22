package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.DirectConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DirectConversationRepository extends JpaRepository<DirectConversation, Long> {

    Optional<DirectConversation> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT conversation FROM DirectConversation conversation
            WHERE conversation.userOneId = :userOneId AND conversation.userTwoId = :userTwoId
            """)
    Optional<DirectConversation> findPairForUpdate(@Param("userOneId") long userOneId,
                                                    @Param("userTwoId") long userTwoId);

    @Modifying
    @Query(value = """
            INSERT INTO direct_conversations (user_one_id, user_two_id, created_at, updated_at)
            VALUES (:userOneId, :userTwoId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """, nativeQuery = true)
    int upsertPair(@Param("userOneId") long userOneId, @Param("userTwoId") long userTwoId);

    @Modifying
    @Query(value = """
            UPDATE direct_conversations
            SET last_message_id = :messageId,
                last_message_at = :messageAt,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :conversationId
              AND (last_message_id IS NULL OR last_message_id < :messageId)
            """, nativeQuery = true)
    int advanceLastMessage(@Param("conversationId") long conversationId,
                           @Param("messageId") long messageId,
                           @Param("messageAt") LocalDateTime messageAt);

    Page<DirectConversation> findByUserOneIdOrUserTwoIdOrderByLastMessageAtDesc(
            Long userOneId, Long userTwoId, Pageable pageable);
}
