package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import com.gamesaves.gamesaves.repository.ConversationReadStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DirectMessageConcurrencyTest {

    @Autowired
    private DirectMessageService service;
    @Autowired
    private ConversationReadStateRepository readStateRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM conversation_read_states");
        jdbcTemplate.update("DELETE FROM direct_messages");
        jdbcTemplate.update("DELETE FROM direct_conversations");
        ensureActiveUser(10L, "dm_concurrent_sender");
        ensureActiveUser(20L, "dm_concurrent_recipient");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void concurrentFirstSendsKeepTheNewestConversationSummaryAndBothReadStates() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DirectMessageResponse>> futures = List.of(
                executor.submit(sendWhenStarted(20L, "one", 10L, ready, start)),
                executor.submit(sendWhenStarted(10L, "two", 20L, ready, start)));

        ready.await();
        start.countDown();
        List<DirectMessageResponse> messages = futures.stream().map(this::get).toList();

        Long conversationId = jdbcTemplate.queryForObject("SELECT id FROM direct_conversations", Long.class);
        Long lastMessageId = jdbcTemplate.queryForObject(
                "SELECT last_message_id FROM direct_conversations WHERE id = ?", Long.class, conversationId);
        Long readStateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_read_states WHERE conversation_id = ?", Long.class, conversationId);

        assertThat(lastMessageId).isEqualTo(messages.stream().mapToLong(DirectMessageResponse::getId).max().orElseThrow());
        assertThat(readStateCount).isEqualTo(2L);
    }

    @Test
    void concurrentReadStateAdvancesNeverRegressTheCursor() throws Exception {
        DirectMessageResponse low = service.send(20L, "low", null, 10L, "127.0.0.1");
        DirectMessageResponse high = service.send(20L, "high", null, 10L, "127.0.0.1");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        Future<?> lowAdvance = executor.submit(advanceWhenStarted(transactions, low.getConversationId(), 20L, low.getId(), ready, start));
        Future<?> highAdvance = executor.submit(advanceWhenStarted(transactions, high.getConversationId(), 20L, high.getId(), ready, start));

        ready.await();
        start.countDown();
        lowAdvance.get();
        highAdvance.get();

        Long cursor = jdbcTemplate.queryForObject("""
                SELECT last_read_message_id FROM conversation_read_states
                WHERE conversation_id = ? AND user_id = ?
                """, Long.class, high.getConversationId(), 20L);
        assertThat(cursor).isEqualTo(high.getId());
    }

    private Callable<DirectMessageResponse> sendWhenStarted(Long targetId, String text, Long senderId,
                                                             CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return service.send(targetId, text, null, senderId, "127.0.0.1");
        };
    }

    private Runnable advanceWhenStarted(TransactionTemplate transactions, Long conversationId, Long userId,
                                        Long messageId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            transactions.executeWithoutResult(status -> readStateRepository.advanceReadState(conversationId, userId, messageId));
        };
    }

    private DirectMessageResponse get(Future<DirectMessageResponse> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void ensureActiveUser(Long id, String username) {
        jdbcTemplate.update("""
                INSERT INTO users (id, username, password, nickname, email, role, is_active, created_at, updated_at)
                VALUES (?, ?, 'test-password', ?, ?, 'user', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE is_active = TRUE
                """, id, username, username, username + "@example.com");
    }
}
