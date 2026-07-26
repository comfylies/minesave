package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.TagResponse;
import com.gamesaves.gamesaves.entity.Tag;
import com.gamesaves.gamesaves.repository.TagRepository;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagSearchServiceTest {

    @Mock
    private Client client;

    @Mock
    private Index index;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagSearchService tagSearchService;

    @Test
    void searchFallsBackToAtMostTenMysqlMatchesWhenMeilisearchIsUnavailable() {
        Tag tag = Tag.builder().id(2L).name("Speedrun").source(Tag.Source.admin).build();
        when(client.index("tags")).thenThrow(new RuntimeException("Meilisearch unavailable"));
        when(tagRepository.searchFallback(eq("speed"), any(Pageable.class))).thenReturn(List.of(tag));

        List<TagResponse> results = tagSearchService.search("speed");

        assertEquals(List.of("Speedrun"), results.stream().map(TagResponse::getName).toList());
    }

    @Test
    void searchReturnsTagDocumentsFromMeilisearchWithoutUsingMysqlFallback() throws Exception {
        HashMap<String, Object> document = new HashMap<>();
        document.put("id", 7L);
        document.put("name", "Speedrun");
        document.put("source", "admin");
        when(client.index("tags")).thenReturn(index);
        when(index.search("speed")).thenReturn(searchResultWith(document));

        List<TagResponse> results = tagSearchService.search("speed");

        assertEquals(List.of("Speedrun"), results.stream().map(TagResponse::getName).toList());
    }

    private SearchResult searchResultWith(HashMap<String, Object> document) throws Exception {
        SearchResult result = new SearchResult();
        var hitsField = SearchResult.class.getDeclaredField("hits");
        hitsField.setAccessible(true);
        hitsField.set(result, new ArrayList<>(List.of(document)));
        return result;
    }
}
