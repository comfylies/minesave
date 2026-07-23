package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.gamesaves.gamesaves.dto.request.GameCreateRequest;
import com.gamesaves.gamesaves.dto.request.GameUpdateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.GameResponse;
import com.gamesaves.gamesaves.exception.RateLimitException;
import com.gamesaves.gamesaves.service.GameService;
import com.gamesaves.gamesaves.util.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final RateLimiter rateLimiter;

    public GameController(GameService gameService, RateLimiter rateLimiter) {
        this.gameService = gameService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ApiResponse<List<GameResponse>> getAllGames() {
        List<GameResponse> games = gameService.getAllGames();
        return ApiResponse.success(games);
    }

    /** 游戏名搜索（含别名）— 上传页 autocomplete */
    @GetMapping("/search")
    public ApiResponse<List<GameResponse>> searchGames(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ApiResponse.success(List.of());
        }
        List<GameResponse> games = gameService.searchGames(q.trim());
        return ApiResponse.success(games);
    }

    /** 首页 Top-N：热门游戏（按 articleCount 降序）或最新上传（按 createdAt 降序） */
    @GetMapping("/top")
    public ApiResponse<List<GameResponse>> getTopGames(
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "4") int limit) {
        List<GameResponse> games = gameService.getTopGames(sort, limit);
        return ApiResponse.success(games);
    }

    @GetMapping("/{id}")
    public ApiResponse<GameResponse> getGame(@PathVariable Long id) {
        GameResponse game = gameService.getGameById(id);
        return ApiResponse.success(game);
    }

    @PostMapping
    @SaCheckLogin
    public ApiResponse<GameResponse> createGame(@Valid @RequestBody GameCreateRequest request,
                                                 HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);

        // 已封禁的 IP 直接拒绝
        if (rateLimiter.isIpBanned(ip, "create-game")) {
            throw new RateLimitException("Too many game creation requests. IP temporarily banned.");
        }

        // IP 级别限流：每分钟最多 3 次，超限封禁 30 分钟
        if (!rateLimiter.tryAcquireGlobal(ip, "create-game", 3, 60)) {
            rateLimiter.banIp(ip, "create-game", 30 * 60);
            throw new RateLimitException("Too many game creation requests. IP banned for 30 minutes.");
        }

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

    /** 获取客户端真实IP（与 AuthService 一致） */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String ipAddr = parts[i].trim();
                if (!ipAddr.isEmpty()) {
                    return ipAddr;
                }
            }
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
