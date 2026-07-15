package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.VoteResponse;

public interface ArticleVoteService {

    VoteResponse vote(Long articleId, String voteType, Long currentUserId, String ip);

    String getCurrentUserVote(Long articleId, Long currentUserId);
}
