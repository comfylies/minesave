package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.GameAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameAliasRepository extends JpaRepository<GameAlias, Long> {

    Optional<GameAlias> findByAliasNormalized(String aliasNormalized);

    boolean existsByAliasNormalized(String aliasNormalized);

    List<GameAlias> findByGameIdAndStatus(Long gameId, String status);

    List<GameAlias> findByGameId(Long gameId);

    void deleteByGameId(Long gameId);

    long countByGameId(Long gameId);
}
