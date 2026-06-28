package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.GameCreateRequest;
import com.gamesaves.gamesaves.dto.request.GameUpdateRequest;
import com.gamesaves.gamesaves.dto.response.GameResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.GameAlias;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.GameAliasRepository;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.gamesaves.gamesaves.repository.SafePathRepository;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.service.GameService;
import com.gamesaves.gamesaves.service.SearchSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class GameServiceImpl implements GameService {

    private static final Logger log = LoggerFactory.getLogger(GameServiceImpl.class);

    private final GameRepository gameRepository;
    private final GameAliasRepository aliasRepository;
    private final ArticleRepository articleRepository;
    private final SavingsRepository savingsRepository;
    private final SafePathRepository safePathRepository;
    private final SearchSyncService searchSyncService;

    public GameServiceImpl(GameRepository gameRepository,
                           GameAliasRepository aliasRepository,
                           ArticleRepository articleRepository,
                           SavingsRepository savingsRepository,
                           SafePathRepository safePathRepository,
                           SearchSyncService searchSyncService) {
        this.gameRepository = gameRepository;
        this.aliasRepository = aliasRepository;
        this.articleRepository = articleRepository;
        this.savingsRepository = savingsRepository;
        this.safePathRepository = safePathRepository;
        this.searchSyncService = searchSyncService;
    }

    // ──────────────────────────────────────────────
    //  规范化工具
    // ──────────────────────────────────────────────

    /** 规范化游戏名：全小写 + 去首尾空格 + 压缩连续空格 */
    static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** 拼接 search_text = name + 所有已确认别名 */
    private String buildSearchText(Game game) {
        List<GameAlias> aliases = aliasRepository.findByGameIdAndStatus(game.getId(), "confirmed");
        if (aliases.isEmpty()) return game.getName();
        StringBuilder sb = new StringBuilder(game.getName());
        for (GameAlias alias : aliases) {
            sb.append(' ').append(alias.getAliasName());
        }
        return sb.toString();
    }

    // ──────────────────────────────────────────────
    //  CRUD
    // ──────────────────────────────────────────────

    @Override
    public GameResponse createGame(GameCreateRequest request) {
        String normalized = normalizeName(request.getName());

        // 检查 games 表 normalized_name 是否冲突
        if (gameRepository.existsByNormalizedName(normalized)) {
            throw new BadRequestException("Game name already exists: " + request.getName());
        }

        // 检查别名表 — 用户输入的名字是否已是其他游戏的别名
        Optional<GameAlias> existingAlias = aliasRepository.findByAliasNormalized(normalized);
        if (existingAlias.isPresent()) {
            Game canonicalGame = existingAlias.get().getGame();
            throw new BadRequestException(
                    "\"" + request.getName() + "\" is already an alias for \"" +
                    canonicalGame.getName() + "\". Please use the existing game.");
        }

        // 创建游戏
        Game game = Game.builder()
                .name(request.getName())
                .normalizedName(normalized)
                .coverUrl(request.getCoverUrl())
                .description(request.getDescription())
                .searchText(request.getName())  // 初始只有自身名字
                .build();

        game = gameRepository.save(game);
        game.setSearchText(buildSearchText(game));
        game = gameRepository.save(game);

        log.info("Game created: {} (normalized: {})", game.getName(), normalized);
        searchSyncService.indexGame(game);
        return GameResponse.fromEntity(game);
    }

    @Override
    public GameResponse updateGame(Long id, GameUpdateRequest request) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Game", id));

        if (request.getName() != null) {
            String normalized = normalizeName(request.getName());
            if (!normalized.equals(game.getNormalizedName())) {
                if (gameRepository.existsByNormalizedName(normalized)) {
                    throw new BadRequestException("Game name already exists");
                }
                Optional<GameAlias> existingAlias = aliasRepository.findByAliasNormalized(normalized);
                if (existingAlias.isPresent() && !existingAlias.get().getGame().getId().equals(id)) {
                    throw new BadRequestException(
                            "\"" + request.getName() + "\" is already an alias for another game.");
                }
                game.setName(request.getName());
                game.setNormalizedName(normalized);
            }
        }
        if (request.getCoverUrl() != null) game.setCoverUrl(request.getCoverUrl());
        if (request.getDescription() != null) game.setDescription(request.getDescription());

        game.setSearchText(buildSearchText(game));
        game = gameRepository.save(game);
        searchSyncService.indexGame(game);
        return GameResponse.fromEntity(game);
    }

    @Override
    @Transactional(readOnly = true)
    public GameResponse getGameById(Long id) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Game", id));
        return GameResponse.fromEntity(game);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameResponse> getAllGames() {
        return gameRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(game -> {
                    long count = articleRepository.countByGameIdAndStatus(game.getId(), Article.ArticleStatus.READY);
                    return GameResponse.fromEntity(game, count);
                })
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────
    //  搜索（别名感知）
    // ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<GameResponse> searchGames(String query) {
        String normalized = normalizeName(query);
        List<Game> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        // ① 别名表精确匹配
        Optional<GameAlias> aliasHit = aliasRepository.findByAliasNormalized(normalized);
        if (aliasHit.isPresent()) {
            Game game = aliasHit.get().getGame();
            results.add(game);
            seenIds.add(game.getId());
        }

        // ② normalized_name 精确匹配
        Optional<Game> exactMatch = gameRepository.findByNormalizedName(normalized);
        if (exactMatch.isPresent() && seenIds.add(exactMatch.get().getId())) {
            results.add(exactMatch.get());
        }

        // ③ search_text 模糊匹配（用 LIKE，对于小规模游戏列表足够）
        List<Game> fuzzy = gameRepository.findBySearchTextContaining(normalized);
        for (Game g : fuzzy) {
            if (seenIds.add(g.getId())) {
                results.add(g);
            }
        }

        // ④ 名称前缀匹配（兜底）
        List<Game> prefix = gameRepository.findByNameContainingIgnoreCase(query.trim());
        for (Game g : prefix) {
            if (seenIds.add(g.getId())) {
                results.add(g);
            }
        }

        return results.stream()
                .map(game -> {
                    long count = articleRepository.countByGameIdAndStatus(game.getId(), Article.ArticleStatus.READY);
                    return GameResponse.fromEntity(game, count);
                })
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────
    //  删除
    // ──────────────────────────────────────────────

    @Override
    public void deleteGame(Long id) {
        if (!gameRepository.existsById(id)) {
            throw new ResourceNotFoundException("Game", id);
        }
        try {
            searchSyncService.deleteGame(id);
            aliasRepository.deleteByGameId(id);
            gameRepository.deleteById(id);
            log.info("Game deleted: id={}", id);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("Cannot delete game: it has associated articles");
        }
    }

    // ──────────────────────────────────────────────
    //  管理员合并（方案 A 后审）
    // ──────────────────────────────────────────────

    @Override
    public Map<String, Object> mergeGames(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw new BadRequestException("Cannot merge a game into itself");
        }

        Game source = gameRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Game", sourceId));
        Game target = gameRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Game", targetId));

        int movedArticles = articleRepository.updateGameId(sourceId, targetId);

        // 迁移 savings 表的 game_id（savings 也有 FK 到 games）
        int movedSavings = savingsRepository.updateGameId(sourceId, targetId);
        log.info("{} savings records moved from game {} to {}", movedSavings, sourceId, targetId);

        // 迁移或删除 safe_paths：目标已有结构则删源，否则迁移
        long targetPathCount = safePathRepository.countByGameId(targetId);
        if (targetPathCount > 0) {
            safePathRepository.deleteByGameId(sourceId);
            log.info("Safe paths of game {} deleted (target game {} already has {} paths)",
                    sourceId, targetId, targetPathCount);
        } else {
            safePathRepository.updateGameId(sourceId, targetId);
            log.info("Safe paths migrated from game {} to {}", sourceId, targetId);
        }

        // source 原名称 → target 别名
        if (!aliasRepository.existsByAliasNormalized(source.getNormalizedName())) {
            GameAlias sourceAsAlias = GameAlias.builder()
                    .game(target)
                    .aliasName(source.getName())
                    .aliasNormalized(source.getNormalizedName())
                    .source("auto")
                    .status("confirmed")
                    .build();
            aliasRepository.save(sourceAsAlias);
        }

        // source 的所有别名迁移到 target
        List<GameAlias> sourceAliases = aliasRepository.findByGameId(sourceId);
        for (GameAlias alias : sourceAliases) {
            if (!aliasRepository.existsByAliasNormalized(alias.getAliasNormalized())) {
                alias.setGame(target);
                alias.setSource("auto");
                aliasRepository.save(alias);
            }
        }

        // 删除 source（没有 article 关联后可以删除）
        searchSyncService.deleteGame(sourceId);
        gameRepository.delete(source);

        // 更新 target search_text 并重新索引
        target.setSearchText(buildSearchText(target));
        gameRepository.save(target);
        searchSyncService.indexGame(target);

        log.info("Games merged: {} (id={}) → {} (id={}), {} articles moved",
                source.getName(), sourceId, target.getName(), targetId, movedArticles);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceId", sourceId);
        result.put("sourceName", source.getName());
        result.put("targetId", targetId);
        result.put("targetName", target.getName());
        result.put("articlesMoved", movedArticles);
        return result;
    }
}
