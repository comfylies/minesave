package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.GameCreateRequest;
import com.gamesaves.gamesaves.dto.request.GameUpdateRequest;
import com.gamesaves.gamesaves.dto.response.GameResponse;

import java.util.List;
import java.util.Map;

public interface GameService {

    GameResponse createGame(GameCreateRequest request);

    GameResponse updateGame(Long id, GameUpdateRequest request);

    GameResponse getGameById(Long id);

    List<GameResponse> getAllGames();

    List<GameResponse> searchGames(String query);

    void deleteGame(Long id);

    /**
     * 获取 Top-N 游戏（首页热门/最新区块）。
     * @param sort "hot"（按 articleCount 降序）或 "newest"（按 createdAt 降序）
     * @param limit 返回数量上限
     */
    List<GameResponse> getTopGames(String sort, int limit);

    Map<String, Object> mergeGames(Long sourceId, Long targetId);
}
