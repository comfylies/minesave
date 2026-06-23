package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.SearchResultResponse;
import com.gamesaves.gamesaves.service.SearchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 统一搜索：同时搜索游戏和存档
     */
    @GetMapping("/search")
    public ApiResponse<SearchResultResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (q == null || q.isBlank()) {
            return ApiResponse.error(400, "Query parameter 'q' is required");
        }

        SearchResultResponse result = searchService.search(q.trim(), page, size);
        return ApiResponse.success(result);
    }
}
