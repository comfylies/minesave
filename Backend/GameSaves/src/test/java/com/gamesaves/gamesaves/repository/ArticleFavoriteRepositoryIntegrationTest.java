package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.ArticleFavorite;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ArticleFavoriteRepositoryIntegrationTest {

    @Autowired
    private ArticleFavoriteRepository favoriteRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findFavoriteArticles_ordersFavoritesWhenArticlesFetchTags() {
        User owner = userRepository.save(User.builder()
                .username("favorite-query-owner")
                .password("password")
                .build());
        User collector = userRepository.save(User.builder()
                .username("favorite-query-collector")
                .password("password")
                .build());
        Game game = gameRepository.save(Game.builder()
                .name("Favorite Query Game")
                .normalizedName("favorite query game")
                .searchText("Favorite Query Game")
                .build());
        Article article = articleRepository.save(Article.builder()
                .title("Favorite Query Save")
                .version("1.0")
                .game(game)
                .user(owner)
                .storageRoot("Database/test")
                .zipFilename("save.zip")
                .build());
        favoriteRepository.save(ArticleFavorite.builder()
                .articleId(article.getId())
                .userId(collector.getId())
                .build());

        assertThat(favoriteRepository.findFavoriteArticlesByUserId(
                collector.getId(), PageRequest.of(0, 10)))
                .extracting(Article::getId)
                .containsExactly(article.getId());
    }
}
