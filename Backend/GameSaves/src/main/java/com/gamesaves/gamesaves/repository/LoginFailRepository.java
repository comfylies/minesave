package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.LoginFail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginFailRepository extends JpaRepository<LoginFail, Long> {

    Optional<LoginFail> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
