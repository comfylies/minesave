package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.ArticleFavorite;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.ArticleFavoriteRepository;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.service.StorageService;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleFavoriteServiceImplTest {

    @Mock
    private ArticleFavoriteRepository favoriteRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ArticleFavoriteServiceImpl service;

    @Test
    void toggle_createsFavoriteWhenNoneExists() {
        when(articleRepository.existsById(8L)).thenReturn(true);
        when(favoriteRepository.findByArticleIdAndUserId(8L, 2L)).thenReturn(Optional.empty());

        assertTrue(service.toggle(8L, 2L).isFavorited());

        verify(favoriteRepository).save(argThat(favorite ->
                favorite.getArticleId().equals(8L) && favorite.getUserId().equals(2L)));
    }

    @Test
    void toggle_removesFavoriteWhenItAlreadyExists() {
        ArticleFavorite favorite = ArticleFavorite.builder()
                .id(4L)
                .articleId(8L)
                .userId(2L)
                .build();
        when(articleRepository.existsById(8L)).thenReturn(true);
        when(favoriteRepository.findByArticleIdAndUserId(8L, 2L)).thenReturn(Optional.of(favorite));

        assertFalse(service.toggle(8L, 2L).isFavorited());

        verify(favoriteRepository).delete(favorite);
    }

    @Test
    void getFavorites_resolvesCoverAndThumbnailUrls() {
        String coverKey = "articles/1/2/3/cover.png";
        String thumbnailKey = "articles/1/2/3/cover_thumb_360.jpg";
        when(favoriteRepository.countByUserId(7L)).thenReturn(1L);
        when(favoriteRepository.findFavoriteArticlesByUserId(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(articleWithCover(coverKey)));
        when(storageService.getPublicUrl(coverKey))
                .thenReturn("/storage/articles/1/2/3/cover.png");
        when(storageService.exists(thumbnailKey)).thenReturn(true);
        when(storageService.getPublicUrl(thumbnailKey))
                .thenReturn("/storage/articles/1/2/3/cover_thumb_360.jpg");

        var response = service.getFavorites(7L, 0, 20).getContent().get(0);

        assertEquals("/storage/articles/1/2/3/cover.png?t=1710000000000", response.getCoverImage());
        assertEquals("/storage/articles/1/2/3/cover_thumb_360.jpg?t=1710000000000",
                response.getCoverThumbnail());
    }

    private Article articleWithCover(String coverKey) {
        return Article.builder()
                .id(3L)
                .title("Favorite save")
                .version("1.0")
                .game(Game.builder().id(2L).name("Game").build())
                .user(User.builder().id(1L).nickname("Author").build())
                .storageRoot("1/2/3/")
                .zipFilename("save.zip")
                .status(Article.ArticleStatus.READY)
                .coverImage(coverKey)
                .updatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(1710000000000L), ZoneOffset.UTC))
                .build();
    }
}
