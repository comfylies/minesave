package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.SearchHitResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.entity.Tag;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncService.class);
    private static final String INDEX_UID = "saves";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Client client;
    private final ArticleRepository articleRepository;
    private final GameRepository gameRepository;

    public SearchSyncService(Client client, ArticleRepository articleRepository,
                              GameRepository gameRepository) {
        this.client = client;
        this.articleRepository = articleRepository;
        this.gameRepository = gameRepository;
    }

    /** 索引单个游戏 */
    public void indexGame(Game game) {
        try {
            Index index = client.index(INDEX_UID);
            long articleCount = articleRepository.countByGameIdAndStatus(
                    game.getId(), Article.ArticleStatus.READY);
            Map<String, Object> doc = gameToDoc(game, articleCount);
            index.addDocuments("[" + toJson(doc) + "]");
            log.debug("Indexed game: {}", game.getName());
        } catch (Exception e) {
            log.warn("Failed to index game {}: {}", game.getId(), e.getMessage());
        }
    }

    /** 索引单个存档 */
    public void indexArticle(Article article) {
        try {
            Index index = client.index(INDEX_UID);
            Map<String, Object> doc = articleToDoc(article);
            index.addDocuments("[" + toJson(doc) + "]");
            log.debug("Indexed article: {}", article.getTitle());
        } catch (Exception e) {
            log.warn("Failed to index article {}: {}", article.getId(), e.getMessage());
        }
    }

    /** 删除游戏索引 */
    public void deleteGame(Long gameId) {
        try {
            client.index(INDEX_UID).deleteDocument("game-" + gameId);
            log.debug("Deleted game index: {}", gameId);
        } catch (Exception e) {
            log.warn("Failed to delete game index {}: {}", gameId, e.getMessage());
        }
    }

    /** 删除存档索引 */
    public void deleteArticle(Long articleId) {
        try {
            client.index(INDEX_UID).deleteDocument("article-" + articleId);
            log.debug("Deleted article index: {}", articleId);
        } catch (Exception e) {
            log.warn("Failed to delete article index {}: {}", articleId, e.getMessage());
        }
    }

    /** 全量重建索引 */
    @Transactional(readOnly = true)
    public int rebuildAll() {
        try {
            Index index = client.index(INDEX_UID);
            index.deleteAllDocuments();
            int count = 0;

            // 索引所有游戏（包括存档数为 0 的，用户搜到空游戏也知道它存在）
            List<Game> games = gameRepository.findAll();
            for (Game game : games) {
                long articleCount = articleRepository.countByGameIdAndStatus(
                        game.getId(), Article.ArticleStatus.READY);
                Map<String, Object> doc = gameToDoc(game, articleCount);
                index.addDocuments("[" + toJson(doc) + "]");
                count++;
            }

            // 索引所有 READY 存档
            int page = 0;
            while (true) {
                Page<Article> articlePage = articleRepository.findByStatusWithDetails(
                        Article.ArticleStatus.READY, PageRequest.of(page, 100));
                if (articlePage.isEmpty()) break;
                String docs = articlePage.getContent().stream()
                        .map(this::articleToDoc)
                        .map(this::toJson)
                        .collect(Collectors.joining(","));
                index.addDocuments("[" + docs + "]");
                count += articlePage.getNumberOfElements();
                if (articlePage.isLast()) break;
                page++;
            }

            log.info("Search index rebuilt: {} documents", count);
            return count;
        } catch (Exception e) {
            log.error("Failed to rebuild search index: {}", e.getMessage(), e);
            throw new RuntimeException("Search index rebuild failed: " + e.getMessage());
        }
    }

    private Map<String, Object> gameToDoc(Game game, long articleCount) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", "game-" + game.getId());
        doc.put("type", "game");
        doc.put("name", game.getName() != null ? game.getName() : "");
        doc.put("description", game.getDescription() != null ? game.getDescription() : "");
        doc.put("articleCount", articleCount);
        doc.put("downloadCount", 0);
        doc.put("createdAt", game.getCreatedAt() != null ? DTF.format(game.getCreatedAt()) : "");
        return doc;
    }

    private Map<String, Object> articleToDoc(Article article) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", "article-" + article.getId());
        doc.put("type", "article");
        doc.put("title", article.getTitle() != null ? article.getTitle() : "");
        doc.put("description", article.getDescription() != null ? article.getDescription() : "");
        doc.put("gameName", article.getGame() != null ? article.getGame().getName() : "");
        doc.put("gameId", article.getGame() != null ? article.getGame().getId() : 0);
        List<String> tagNames = article.getTags() != null
                ? article.getTags().stream().map(Tag::getName).collect(Collectors.toList())
                : Collections.emptyList();
        doc.put("tags", tagNames);
        doc.put("downloadCount", article.getDownloadCount() != null ? article.getDownloadCount() : 0);
        doc.put("createdAt", article.getCreatedAt() != null ? DTF.format(article.getCreatedAt()) : "");
        return doc;
    }

    private String toJson(Map<String, Object> doc) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String) {
                sb.append("\"").append(escapeJson((String) v)).append("\"");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof List) {
                sb.append("[");
                List<?> list = (List<?>) v;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(String.valueOf(list.get(i)))).append("\"");
                }
                sb.append("]");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
