package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Savings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsRepository extends JpaRepository<Savings, Long> {

    Optional<Savings> findByArticleId(Long articleId);

    Optional<Savings> findByZipHash(String zipHash);

    List<Savings> findByUserIdAndGameId(Long userId, Long gameId);

    void deleteByArticleId(Long articleId);

    /** Admin merge: bulk-migrate savings from one game to another. */
    @Modifying
    @Query("UPDATE Savings s SET s.gameId = :targetId WHERE s.gameId = :sourceId")
    int updateGameId(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    /** Admin cascade delete: remove all savings for a game. */
    @Modifying
    @Query("DELETE FROM Savings s WHERE s.gameId = :gameId")
    int deleteByGameId(@Param("gameId") Long gameId);
}
