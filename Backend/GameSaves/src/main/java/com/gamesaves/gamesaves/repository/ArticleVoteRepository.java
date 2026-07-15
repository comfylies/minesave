package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.ArticleVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleVoteRepository extends JpaRepository<ArticleVote, Long> {

    Optional<ArticleVote> findByArticleIdAndUserId(Long articleId, Long userId);

    long countByArticleIdAndVoteType(Long articleId, ArticleVote.VoteType voteType);

    void deleteByArticleId(Long articleId);
}
