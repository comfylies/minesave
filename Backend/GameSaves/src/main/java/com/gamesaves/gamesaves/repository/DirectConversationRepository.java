package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.DirectConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectConversationRepository extends JpaRepository<DirectConversation, Long> {

    Optional<DirectConversation> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

    Page<DirectConversation> findByUserOneIdOrUserTwoIdOrderByLastMessageAtDesc(
            Long userOneId, Long userTwoId, Pageable pageable);
}
