package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.TagResponse;
import com.gamesaves.gamesaves.entity.Tag;
import com.gamesaves.gamesaves.repository.TagRepository;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class TagSearchService {

    private static final Logger log = LoggerFactory.getLogger(TagSearchService.class);
    private static final String INDEX_UID = "tags";
    private static final int SEARCH_LIMIT = 10;

    private final Client client;
    private final TagRepository tagRepository;

    public TagSearchService(Client client, TagRepository tagRepository) {
        this.client = client;
        this.tagRepository = tagRepository;
    }

    public List<TagResponse> search(String keyword) {
        try {
            SearchResult result = (SearchResult) client.index(INDEX_UID).search(keyword);
            if (result.getHits() == null) {
                return Collections.emptyList();
            }
            return result.getHits().stream()
                    .limit(SEARCH_LIMIT)
                    .map(this::toResponse)
                    .toList();
        } catch (Exception exception) {
            log.warn("Tag Meilisearch query failed; using MySQL fallback: {}", exception.getMessage());
            return tagRepository.searchFallback(keyword, PageRequest.of(0, SEARCH_LIMIT)).stream()
                    .map(TagResponse::fromEntity)
                    .toList();
        }
    }

    public List<TagResponse> featured() {
        return tagRepository.findTop8BySourceOrderByCreatedAtDesc(Tag.Source.admin).stream()
                .map(TagResponse::fromEntity)
                .toList();
    }

    public void indexTag(Tag tag) {
        try {
            client.index(INDEX_UID).addDocuments("[" + toJson(tag) + "]");
        } catch (Exception exception) {
            log.warn("Failed to index tag {}: {}", tag.getId(), exception.getMessage());
        }
    }

    public void deleteTag(Long tagId) {
        try {
            client.index(INDEX_UID).deleteDocument(String.valueOf(tagId));
        } catch (Exception exception) {
            log.warn("Failed to delete tag index {}: {}", tagId, exception.getMessage());
        }
    }

    public int rebuildIndex() {
        try {
            var index = client.index(INDEX_UID);
            index.deleteAllDocuments();
            List<Tag> tags = tagRepository.findAll();
            if (!tags.isEmpty()) {
                index.addDocuments("[" + tags.stream().map(this::toJson).collect(java.util.stream.Collectors.joining(",")) + "]");
            }
            return tags.size();
        } catch (Exception exception) {
            log.warn("Failed to rebuild tag index: {}", exception.getMessage());
            return 0;
        }
    }

    private TagResponse toResponse(Map<String, Object> hit) {
        Object id = hit.get("id");
        Long tagId = id instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(id));
        return TagResponse.builder()
                .id(tagId)
                .name(String.valueOf(hit.getOrDefault("name", "")))
                .source(String.valueOf(hit.getOrDefault("source", "user")))
                .articleCount(0L)
                .build();
    }

    private String toJson(Tag tag) {
        return "{\"id\":" + tag.getId()
                + ",\"name\":\"" + escapeJson(tag.getName()) + "\""
                + ",\"source\":\"" + tag.getSource().name() + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
