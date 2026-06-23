package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.GameCreateRequest;
import com.gamesaves.gamesaves.dto.request.GameUpdateRequest;
import com.gamesaves.gamesaves.dto.response.GameResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.gamesaves.gamesaves.service.GameService;
import com.gamesaves.gamesaves.service.SearchSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class GameServiceImpl implements GameService {

    private static final Logger log = LoggerFactory.getLogger(GameServiceImpl.class);

    private final GameRepository gameRepository;
    private final ArticleRepository articleRepository;
    private final SearchSyncService searchSyncService;

    public GameServiceImpl(GameRepository gameRepository, ArticleRepository articleRepository,
                           SearchSyncService searchSyncService) {
        this.gameRepository = gameRepository;
        this.articleRepository = articleRepository;
        this.searchSyncService = searchSyncService;
    }

    @Override
    public GameResponse createGame(GameCreateRequest request) {
        if (gameRepository.existsByName(request.getName())) {
            throw new BadRequestException("Game name already exists: " + request.getName());
        }

        Game game = Game.builder()
                .name(request.getName())
                .coverUrl(request.getCoverUrl())
                .description(request.getDescription())
                .build();

        game = gameRepository.save(game);
        log.info("Game created: {}", game.getName());
        searchSyncService.indexGame(game);
        return GameResponse.fromEntity(game);
    }

    @Override
    public GameResponse updateGame(Long id, GameUpdateRequest request) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Game", id));

        if (request.getName() != null) {
            if (!request.getName().equals(game.getName())
                    && gameRepository.existsByName(request.getName())) {
                throw new BadRequestException("Game name already exists");
            }
            game.setName(request.getName());
        }
        if (request.getCoverUrl() != null) game.setCoverUrl(request.getCoverUrl());
        if (request.getDescription() != null) game.setDescription(request.getDescription());

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

    @Override
    public void deleteGame(Long id) {
        if (!gameRepository.existsById(id)) {
            throw new ResourceNotFoundException("Game", id);
        }
        try {
            searchSyncService.deleteGame(id);
            gameRepository.deleteById(id);
            log.info("Game deleted: id={}", id);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("Cannot delete game: it has associated articles");
        }
    }
}
