package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.SearchHitResponse;
import com.gamesaves.gamesaves.dto.response.SearchResultResponse;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String INDEX_UID = "saves";

    private final Client client;

    public SearchService(Client client) {
        this.client = client;
    }

    public SearchResultResponse search(String query, int page, int size) {
        try {
            Index index = client.index(INDEX_UID);
            SearchResult result = (SearchResult) index.search(query);

            List<SearchHitResponse> hits = new ArrayList<>();
            if (result.getHits() != null) {
                for (Object raw : result.getHits()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> hit = (Map<String, Object>) raw;
                    hits.add(mapHit(hit));
                }
            }

            hits.sort(Comparator
                    .comparing(SearchHitResponse::getType, (a, b) -> {
                        if ("game".equals(a) && "article".equals(b)) return -1;
                        if ("article".equals(a) && "game".equals(b)) return 1;
                        return 0;
                    })
                    .thenComparing(h -> h.getDownloadCount() != null ? h.getDownloadCount() : 0,
                            Comparator.reverseOrder()));

            return SearchResultResponse.builder()
                    .query(query)
                    .totalHits(result.getEstimatedTotalHits())
                    .processingTimeMs(result.getProcessingTimeMs())
                    .hits(hits)
                    .build();
        } catch (Exception e) {
            log.error("Search error: {}", e.getMessage(), e);
            return SearchResultResponse.builder()
                    .query(query).totalHits(0).processingTimeMs(0)
                    .hits(Collections.emptyList()).build();
        }
    }

    @SuppressWarnings("unchecked")
    private SearchHitResponse mapHit(Map<String, Object> hit) {
        String type = (String) hit.getOrDefault("type", "article");

        List<String> tags = Collections.emptyList();
        Object tagsObj = hit.get("tags");
        if (tagsObj instanceof List) {
            tags = ((List<?>) tagsObj).stream().map(Object::toString).collect(Collectors.toList());
        }

        return SearchHitResponse.builder()
                .type(type)
                .id(parseId((String) hit.get("id")))
                .title("game".equals(type) ? (String) hit.get("name")
                        : (String) hit.get("title"))
                .description((String) hit.get("description"))
                .gameName((String) hit.get("gameName"))
                .gameId(hit.get("gameId") instanceof Number
                        ? ((Number) hit.get("gameId")).longValue() : null)
                .tags(tags)
                .articleCount(hit.get("articleCount") instanceof Number
                        ? ((Number) hit.get("articleCount")).longValue() : null)
                .downloadCount(hit.get("downloadCount") instanceof Number
                        ? ((Number) hit.get("downloadCount")).intValue() : 0)
                .createdAt((String) hit.get("createdAt"))
                .build();
    }

    private Long parseId(String docId) {
        if (docId == null || docId.isEmpty()) return null;
        int dash = docId.indexOf('-');
        if (dash >= 0) {
            try { return Long.parseLong(docId.substring(dash + 1)); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
