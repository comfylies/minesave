package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.request.CommentCreateRequest;
import com.gamesaves.gamesaves.dto.response.CommentResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Comment;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.CommentRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.CommentService;
import com.gamesaves.gamesaves.util.XssFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public CommentServiceImpl(CommentRepository commentRepository,
                               ArticleRepository articleRepository,
                               UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    @Override
    public CommentResponse createComment(CommentCreateRequest request) {
        // Validate article
        Article article = articleRepository.findById(request.getArticleId())
                .orElseThrow(() -> new ResourceNotFoundException("Article", request.getArticleId()));

        if (article.getStatus() != Article.ArticleStatus.READY) {
            throw new BadRequestException("Cannot comment on article that is not READY");
        }

        // Validate user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        // Validate parent comment (for reply)
        if (request.getParentId() != null) {
            if (!commentRepository.existsById(request.getParentId())) {
                throw new ResourceNotFoundException("Parent comment", request.getParentId());
            }
        }

        // XSS filter content
        String sanitizedContent = XssFilter.sanitize(request.getContent());

        Comment comment = Comment.builder()
                .articleId(request.getArticleId())
                .userId(request.getUserId())
                .content(sanitizedContent)
                .anchor(request.getAnchor())
                .selectedText(request.getSelectedText())
                .quoteStart(request.getQuoteStart())
                .quoteEnd(request.getQuoteEnd())
                .parentId(request.getParentId())
                .build();

        comment = commentRepository.save(comment);
        // Force load user for nickname
        comment = commentRepository.findById(comment.getId()).orElse(comment);

        log.info("Comment {} created on article {}", comment.getId(), request.getArticleId());
        return CommentResponse.fromEntity(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByArticle(Long articleId) {
        List<Comment> allComments = commentRepository.findByArticleIdWithUser(articleId);

        // Group: top-level comments vs replies
        Map<Long, List<Comment>> childrenMap = allComments.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Comment::getParentId));

        // Convert top-level comments with nested children
        return allComments.stream()
                .filter(c -> c.getParentId() == null)
                .map(c -> {
                    CommentResponse resp = CommentResponse.fromEntity(c);
                    List<Comment> children = childrenMap.getOrDefault(c.getId(), new ArrayList<>());
                    resp.setChildren(children.stream()
                            .map(CommentResponse::fromEntity)
                            .collect(Collectors.toList()));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        // Only comment author or admin can delete
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!comment.getUserId().equals(userId) && !"admin".equals(user.getRole())) {
            throw new BadRequestException("Only the comment author or an admin can delete this comment");
        }

        // Cascade delete children
        commentRepository.deleteByParentId(commentId);
        commentRepository.delete(comment);
        log.info("Comment {} deleted by user {}", commentId, userId);
    }
}
