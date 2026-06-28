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

    Map<String, Object> mergeGames(Long sourceId, Long targetId);
}
