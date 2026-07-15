package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class VoteResponse {

    private int upvoteCount;
    private int downvoteCount;
    private String userVote;   // "UP", "DOWN", or null
}
