package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.ArticleFavorite;
import com.gamesaves.gamesaves.repository.ArticleFavoriteRepository;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleFavoriteServiceImplTest {

    @Mock
    private ArticleFavoriteRepository favoriteRepository;

    @Mock
    private ArticleRepository articleRepository;

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
}
