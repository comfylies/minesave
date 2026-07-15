package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.VoteResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.ArticleVote;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.ArticleVoteRepository;
import com.gamesaves.gamesaves.service.ArticleVoteService;
import com.gamesaves.gamesaves.util.RateLimiter;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Transactional
public class ArticleVoteServiceImpl implements ArticleVoteService {

    private static final Logger log = LoggerFactory.getLogger(ArticleVoteServiceImpl.class);

    private static final int VOTE_RATE_LIMIT = 30;       // max votes per window
    private static final int VOTE_RATE_WINDOW_SEC = 60;  // window in seconds

    private final ArticleVoteRepository voteRepository;
    private final ArticleRepository articleRepository;
    private final RateLimiter rateLimiter;

    public ArticleVoteServiceImpl(ArticleVoteRepository voteRepository,
                                   ArticleRepository articleRepository,
                                   RateLimiter rateLimiter) {
        this.voteRepository = voteRepository;
        this.articleRepository = articleRepository;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public VoteResponse vote(Long articleId, String voteType, Long currentUserId, String ip) {
        // ── 频率限制 ──
        if (!rateLimiter.tryAcquireGlobal(ip, "vote", VOTE_RATE_LIMIT, VOTE_RATE_WINDOW_SEC)) {
            throw new BadRequestException("操作太频繁，请稍后再试");
        }

        // Validate article exists
        articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        Optional<ArticleVote> existing = voteRepository.findByArticleIdAndUserId(articleId, currentUserId);

        if ("NONE".equals(voteType)) {
            // Cancel current vote
            if (existing.isPresent()) {
                ArticleVote.VoteType oldType = existing.get().getVoteType();
                voteRepository.delete(existing.get());
                adjustCount(articleId, oldType, -1);
            }
        } else {
            ArticleVote.VoteType newType = ArticleVote.VoteType.valueOf(voteType);

            if (existing.isPresent()) {
                ArticleVote existingVote = existing.get();
                if (existingVote.getVoteType() == newType) {
                    // Toggle off: same vote clicked again
                    voteRepository.delete(existingVote);
                    adjustCount(articleId, newType, -1);
                } else {
                    // Switch vote: UPDATE type in-place (avoids UNIQUE constraint violation
                    // that would happen with DELETE + INSERT before transaction flush)
                    adjustCount(articleId, existingVote.getVoteType(), -1);
                    existingVote.setVoteType(newType);
                    voteRepository.save(existingVote);
                    adjustCount(articleId, newType, +1);
                }
            } else {
                // New vote
                createVote(articleId, currentUserId, newType);
                adjustCount(articleId, newType, +1);
            }
        }

        // Fetch fresh state (counts were atomically updated, re-read for accuracy)
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));
        String userVote = getCurrentUserVote(articleId, currentUserId);

        return VoteResponse.builder()
                .upvoteCount(article.getUpvoteCount())
                .downvoteCount(article.getDownvoteCount())
                .userVote(userVote)
                .build();
    }

    @Override
    @Transactional
    public String getCurrentUserVote(Long articleId, Long currentUserId) {
        return voteRepository.findByArticleIdAndUserId(articleId, currentUserId)
                .map(v -> v.getVoteType().name())
                .orElse(null);
    }

    private void createVote(Long articleId, Long userId, ArticleVote.VoteType type) {
        ArticleVote vote = ArticleVote.builder()
                .articleId(articleId)
                .userId(userId)
                .voteType(type)
                .build();
        voteRepository.save(vote);
    }

    private void adjustCount(Long articleId, ArticleVote.VoteType type, int delta) {
        if (type == ArticleVote.VoteType.UP) {
            articleRepository.updateUpvoteCount(articleId, delta);
        } else {
            articleRepository.updateDownvoteCount(articleId, delta);
        }
    }
}
