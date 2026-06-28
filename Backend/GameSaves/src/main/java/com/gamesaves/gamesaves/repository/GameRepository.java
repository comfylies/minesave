package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByName(String name);

    boolean existsByName(String name);

    List<Game> findAllByOrderByCreatedAtDesc();

    // ── 规范化名称查询 ──

    boolean existsByNormalizedName(String normalizedName);

    Optional<Game> findByNormalizedName(String normalizedName);

    // ── search_text 模糊匹配 ──

    @Query("SELECT g FROM Game g WHERE g.searchText LIKE %:query%")
    List<Game> findBySearchTextContaining(@Param("query") String query);

    // ── 名称前缀/包含匹配（兜底） ──

    @Query("SELECT g FROM Game g WHERE LOWER(g.name) LIKE %:name%")
    List<Game> findByNameContainingIgnoreCase(@Param("name") String name);
}
