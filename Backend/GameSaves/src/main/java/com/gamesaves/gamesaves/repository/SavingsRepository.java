package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Savings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsRepository extends JpaRepository<Savings, Long> {

    Optional<Savings> findByArticleId(Long articleId);

    Optional<Savings> findByZipHash(String zipHash);

    List<Savings> findByUserIdAndGameId(Long userId, Long gameId);

    void deleteByArticleId(Long articleId);
}
