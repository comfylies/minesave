package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    Page<ContactMessage> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ContactMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);
}
