package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {

    private String query;
    private long totalHits;
    private long processingTimeMs;
    private List<SearchHitResponse> hits;
}
