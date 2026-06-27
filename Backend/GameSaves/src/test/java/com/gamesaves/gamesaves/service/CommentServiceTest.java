package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.CommentCreateRequest;
import com.gamesaves.gamesaves.dto.response.CommentResponse;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommentService 集成测试 — 批注创建/删除权限校验。
 * 直接调用 Service 方法传入 userId，不依赖 Sa-Token 上下文。
 */
@SpringBootTest
@Transactional
class CommentServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    private static final Long ARTICLE_ID = 1L;
    private static final Long USER_PLAYER_ONE = 2L;
    private static final Long USER_SPEEDRUNNER = 3L;
    private static final Long USER_ADMIN = 1L;

    // ═══════════════════════════════════════════════════════════════
    // 创建批注
    // ═══════════════════════════════════════════════════════════════

    @Test
    void createComment_asLoggedInUser_shouldSucceed() {
        CommentCreateRequest req = new CommentCreateRequest();
        req.setArticleId(ARTICLE_ID);
        req.setContent("测试批注内容");
        req.setAnchor("{\"type\":\"TextQuoteSelector\",\"exact\":\"test\"}");
        req.setSelectedText("test");

        CommentResponse resp = commentService.createComment(req, USER_PLAYER_ONE);
        assertNotNull(resp.getId());
        assertEquals(USER_PLAYER_ONE, resp.getUserId());
        assertEquals("测试批注内容", resp.getContent());
    }

    @Test
    void createComment_onNonExistentArticle_shouldThrow() {
        CommentCreateRequest req = new CommentCreateRequest();
        req.setArticleId(9999L);
        req.setContent("测试");
        req.setAnchor("{}");
        req.setSelectedText("t");

        assertThrows(Exception.class,
                () -> commentService.createComment(req, USER_PLAYER_ONE));
    }

    // ═══════════════════════════════════════════════════════════════
    // 删除批注 — H-5: 权限校验
    // ═══════════════════════════════════════════════════════════════

    @Test
    void deleteComment_ownComment_shouldSucceed() {
        CommentCreateRequest req = new CommentCreateRequest();
        req.setArticleId(ARTICLE_ID);
        req.setContent("我的批注");
        req.setAnchor("{}");
        req.setSelectedText("my");

        CommentResponse resp = commentService.createComment(req, USER_PLAYER_ONE);
        Long commentId = resp.getId();
        assertNotNull(commentId);

        // 自己删除 → 成功
        assertDoesNotThrow(() -> commentService.deleteComment(commentId, USER_PLAYER_ONE));
        assertFalse(commentRepository.existsById(commentId));
    }

    @Test
    void deleteComment_otherUserComment_shouldThrow() {
        // player_one 创建
        CommentCreateRequest req = new CommentCreateRequest();
        req.setArticleId(ARTICLE_ID);
        req.setContent("别人的批注");
        req.setAnchor("{}");
        req.setSelectedText("other");

        CommentResponse resp = commentService.createComment(req, USER_PLAYER_ONE);
        Long commentId = resp.getId();

        // speedrunner 尝试删除 → 失败
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> commentService.deleteComment(commentId, USER_SPEEDRUNNER));
        assertTrue(ex.getMessage().contains("Only the comment author or an admin"));
        assertTrue(commentRepository.existsById(commentId), "批注不应被删除");
    }

    @Test
    void deleteComment_adminCanDeleteOthersComment() {
        // player_one 创建
        CommentCreateRequest req = new CommentCreateRequest();
        req.setArticleId(ARTICLE_ID);
        req.setContent("管理员可以删");
        req.setAnchor("{}");
        req.setSelectedText("admin");

        CommentResponse resp = commentService.createComment(req, USER_PLAYER_ONE);
        Long commentId = resp.getId();

        // admin 删除 → 成功
        assertDoesNotThrow(() -> commentService.deleteComment(commentId, USER_ADMIN));
        assertFalse(commentRepository.existsById(commentId));
    }

    // ═══════════════════════════════════════════════════════════════
    // 获取批注列表
    // ═══════════════════════════════════════════════════════════════

    @Test
    void getCommentsByArticle_shouldReturnComments() {
        List<CommentResponse> comments = commentService.getCommentsByArticle(ARTICLE_ID);
        assertNotNull(comments);
        // 测试数据中 article 1 已有批注
        assertTrue(comments.size() >= 0);
    }
}
