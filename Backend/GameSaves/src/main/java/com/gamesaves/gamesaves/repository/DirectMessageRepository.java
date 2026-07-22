package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    Page<DirectMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    Page<DirectMessage> findByConversationIdAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    long countByConversationId(Long conversationId);
}
