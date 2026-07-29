package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ForbiddenException;
import com.gamesaves.gamesaves.util.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class DirectMessageServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DirectMessageService service;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void prepareUsersAndClearDirectMessages() {
        rateLimiter.clear();
        jdbcTemplate.update("DELETE FROM conversation_read_states");
        jdbcTemplate.update("DELETE FROM direct_messages");
        jdbcTemplate.update("DELETE FROM direct_conversations");
        ensureActiveUser(10L, "dm_sender");
        ensureActiveUser(20L, "dm_receiver");
        ensureActiveUser(30L, "dm_observer");
    }

    @Test
    void flywayAppliesDirectMessagingMigrationsAndRejectsNonNormalizedPairs() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('5', '6')
                  AND success = TRUE
                """, Integer.class);

        assertEquals(2, migrationCount);

        DataAccessException exception = assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO direct_conversations (user_one_id, user_two_id, created_at, updated_at)
                VALUES (900001, 900001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """));
        SQLException sqlException = assertInstanceOf(SQLException.class, exception.getRootCause());
        assertEquals(3819, sqlException.getErrorCode());
    }

    @Test
    void sendNormalizesPairAndMarksOnlyReceiverUnread() {
        DirectMessageResponse sent = service.send(20L, "hello", null, 10L, "127.0.0.1");

        List<List<Long>> pair = jdbcTemplate.query("""
                SELECT user_one_id, user_two_id
                FROM direct_conversations
                WHERE id = ?
                """, (resultSet, rowNum) -> List.of(
                resultSet.getLong("user_one_id"), resultSet.getLong("user_two_id")), sent.getConversationId());
        assertThat(pair).containsExactly(List.of(10L, 20L));
        assertThat(service.getUnreadCount(10L)).isZero();
        assertThat(service.getUnreadCount(20L)).isEqualTo(1L);
    }

    @Test
    void getMessagesRejectsAUserOutsideTheConversation() {
        DirectMessageResponse sent = service.send(20L, "private", null, 10L, "127.0.0.1");

        assertThatThrownBy(() -> service.getMessages(sent.getConversationId(), null, 40, 30L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsInvalidTargetsAndText() {
        assertThatThrownBy(() -> service.send(10L, "hello", null, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.send(999999L, "hello", null, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);

        jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = 20");
        assertThatThrownBy(() -> service.send(20L, "hello", null, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        jdbcTemplate.update("UPDATE users SET is_active = TRUE WHERE id = 20");

        assertThatThrownBy(() -> service.send(20L, "   ", null, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.send(20L, "x".repeat(4001), null, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsImagesUntilChatImageStorageIsImplemented() {
        MockMultipartFile image = new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.send(20L, "caption", image, 10L, "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM direct_messages", Long.class)).isZero();
    }

    @Test
    void historyUsesBeforeIdCursorAndReturnsMessagesChronologically() {
        DirectMessageResponse first = service.send(20L, "one", null, 10L, "127.0.0.1");
        DirectMessageResponse second = service.send(20L, "two", null, 10L, "127.0.0.1");
        DirectMessageResponse third = service.send(20L, "three", null, 10L, "127.0.0.1");
        DirectMessageResponse fourth = service.send(20L, "four", null, 10L, "127.0.0.1");
        DirectMessageResponse fifth = service.send(20L, "five", null, 10L, "127.0.0.1");

        PageDTO<DirectMessageResponse> recent = service.getMessages(fifth.getConversationId(), null, 2, 20L);
        PageDTO<DirectMessageResponse> older = service.getMessages(fifth.getConversationId(), fourth.getId(), 2, 20L);

        assertThat(recent.getContent()).extracting(DirectMessageResponse::getId)
                .containsExactly(fourth.getId(), fifth.getId());
        assertThat(recent.getTotalElements()).isEqualTo(5L);
        assertThat(older.getContent()).extracting(DirectMessageResponse::getId)
                .containsExactly(second.getId(), third.getId());
        assertThat(older.getTotalElements()).isEqualTo(3L);
        assertThat(service.getMessages(fifth.getConversationId(), second.getId(), 2, 20L).getContent())
                .extracting(DirectMessageResponse::getId)
                .containsExactly(first.getId());
    }

    @Test
    void rejectsAnInvalidHistoryCursor() {
        DirectMessageResponse sent = service.send(20L, "one", null, 10L, "127.0.0.1");

        assertThatThrownBy(() -> service.getMessages(sent.getConversationId(), 0L, 20, 20L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void markReadClearsReceiverUnreadCount() {
        DirectMessageResponse sent = service.send(20L, "read me", null, 10L, "127.0.0.1");

        service.markRead(sent.getConversationId(), 20L);

        assertThat(service.getUnreadCount(20L)).isZero();
    }

    @Test
    void conversationsExposeOnlyTheCallingUsersPeer() {
        service.send(20L, "hello", null, 10L, "127.0.0.1");

        assertThat(service.getConversations(10L, 0, 20).getContent())
                .hasSize(1)
                .allSatisfy(conversation -> {
                    assertThat(conversation.getPeerId()).isEqualTo(20L);
                    assertThat(conversation.getUnreadCount()).isZero();
                });
    }

    private void ensureActiveUser(Long id, String username) {
        jdbcTemplate.update("""
                INSERT INTO users (id, username, password, nickname, email, role, is_active, created_at, updated_at)
                VALUES (?, ?, 'test-password', ?, ?, 'user', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE is_active = TRUE
                """, id, username, username, username + "@example.com");
    }
}
