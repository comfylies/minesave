package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.request.GameCreateRequest;
import com.gamesaves.gamesaves.dto.request.GameUpdateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.GameResponse;
import com.gamesaves.gamesaves.service.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public ApiResponse<List<GameResponse>> getAllGames() {
        List<GameResponse> games = gameService.getAllGames();
        return ApiResponse.success(games);
    }

    @GetMapping("/{id}")
    public ApiResponse<GameResponse> getGame(@PathVariable Long id) {
        GameResponse game = gameService.getGameById(id);
        return ApiResponse.success(game);
    }

    @PostMapping
    public ApiResponse<GameResponse> createGame(@Valid @RequestBody GameCreateRequest request) {
        GameResponse game = gameService.createGame(request);
        return ApiResponse.success("Game created", game);
    }

    @PutMapping("/{id}")
    public ApiResponse<GameResponse> updateGame(@PathVariable Long id,
                                                 @RequestBody GameUpdateRequest request) {
        GameResponse game = gameService.updateGame(id, request);
        return ApiResponse.success(game);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGame(@PathVariable Long id) {
        gameService.deleteGame(id);
        return ApiResponse.success("Game deleted", null);
    }
}
