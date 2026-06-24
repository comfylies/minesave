package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.SafePath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface SafePathRepository extends JpaRepository<SafePath, Long> {

    /** 获取某个游戏的所有标准路径 */
    List<SafePath> findByGameId(Long gameId);

    /** 获取某个游戏的所有标准路径字符串（用于 Set 快速查找） */
    @Query("SELECT sp.path FROM SafePath sp WHERE sp.gameId = :gameId")
    Set<String> findPathSetByGameId(@Param("gameId") Long gameId);

    /** 根据游戏和路径查找 */
    boolean existsByGameIdAndPath(Long gameId, String path);

    /** 删除某个游戏的所有标准路径（重新上传时覆盖） */
    void deleteByGameId(Long gameId);

    /** 统计某个游戏的标准路径数量 */
    long countByGameId(Long gameId);
}
