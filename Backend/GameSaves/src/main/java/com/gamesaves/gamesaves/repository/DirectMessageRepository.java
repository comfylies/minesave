package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    Page<DirectMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    Page<DirectMessage> findByConversationIdAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    long countByConversationId(Long conversationId);

    long countByConversationIdAndIdLessThan(Long conversationId, Long id);

    java.util.Optional<DirectMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    @Query("SELECT m FROM DirectMessage m WHERE m.imageExpiredAt IS NULL AND m.messageType IN ('IMAGE', 'IMAGE_WITH_TEXT') AND m.createdAt <= :cutoff ORDER BY m.id ASC")
    java.util.List<DirectMessage> findImageExpiryCandidates(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);

    @Query("SELECT m FROM DirectMessage m WHERE m.createdAt <= :cutoff ORDER BY m.conversationId ASC, m.id ASC")
    java.util.List<DirectMessage> findPurgeCandidates(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);
}
