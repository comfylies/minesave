package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.GameCreateRequest;
import com.gamesaves.gamesaves.dto.request.GameUpdateRequest;
import com.gamesaves.gamesaves.dto.response.GameResponse;

import java.util.List;

public interface GameService {

    GameResponse createGame(GameCreateRequest request);

    GameResponse updateGame(Long id, GameUpdateRequest request);

    GameResponse getGameById(Long id);

    List<GameResponse> getAllGames();

    void deleteGame(Long id);
}
