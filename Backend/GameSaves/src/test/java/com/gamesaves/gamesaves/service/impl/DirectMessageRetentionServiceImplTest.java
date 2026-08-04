package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.DirectConversation;
import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageRetentionServiceImplTest {
    @Mock private DirectMessageRepository messages;
    @Mock private DirectConversationRepository conversations;
    @Mock private StorageService storage;
    private DirectMessageRetentionServiceImpl retention;

    @BeforeEach
    void setUp() {
        // 无操作事务管理器：TransactionTemplate 直接执行 lambda，不真正开事务
        PlatformTransactionManager noop = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        };
        retention = new DirectMessageRetentionServiceImpl(messages, conversations, storage, noop);
    }

    @Test
    void expiresImageFilesAtFourteenDaysButRetainsMessageText() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 3, 15);
        DirectMessage image = DirectMessage.builder().id(10L).conversationId(9L)
                .messageType(DirectMessage.MessageType.IMAGE_WITH_TEXT).content("caption")
                .imageOriginalKey("messages/9/10/original.png").imageThumbnailKey("messages/9/10/thumbnail.jpg")
                .createdAt(now.minusDays(14)).build();
        when(messages.findImageExpiryCandidates(eq(now.minusDays(14)), any())).thenReturn(List.of(image));
        when(messages.findPurgeCandidates(eq(now.minusDays(30)), any())).thenReturn(List.of());

        retention.cleanup(now);

        verify(storage).delete("messages/9/10/original.png");
        verify(storage).delete("messages/9/10/thumbnail.jpg");
        assertThat(image.getImageExpiredAt()).isEqualTo(now);
        assertThat(image.getContent()).isEqualTo("caption");
        assertThat(image.getImageOriginalKey()).isNull();
    }

    @Test
    void stopsAfterOneBatchWhenStorageErrorsBlockEveryExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 3, 15);
        // 整批 200 条（= batchSize）且全部删除失败：修复前会无限重取同一批空转
        List<DirectMessage> fullBatch = IntStream.range(0, 200)
                .mapToObj(i -> DirectMessage.builder().id((long) i).conversationId(9L)
                        .messageType(DirectMessage.MessageType.IMAGE).content("caption")
                        .imageOriginalKey("messages/9/" + i + "/original.png")
                        .imageThumbnailKey("messages/9/" + i + "/thumbnail.jpg")
                        .createdAt(now.minusDays(20)).build())
                .toList();
        when(messages.findImageExpiryCandidates(eq(now.minusDays(14)), any())).thenReturn(fullBatch);
        when(messages.findPurgeCandidates(eq(now.minusDays(30)), any())).thenReturn(List.of());
        doThrow(new RuntimeException("storage down")).when(storage).delete(anyString());

        var result = retention.cleanup(now);

        assertThat(result.imagesExpired()).isZero();
        assertThat(result.messagesDeleted()).isZero();
        // 只查询一次候选即退出，没有重试同一批
        verify(messages, times(1)).findImageExpiryCandidates(any(), any());
    }

    @Test
    void purgeDeletesResidualImageFilesAndRepairsConversation() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 3, 15);
        // 模拟 14 天图片过期失败过的消息：到 30 天被 purge 时仍带着 image key，删行前必须删文件
        DirectMessage stale = DirectMessage.builder().id(5L).conversationId(9L)
                .messageType(DirectMessage.MessageType.IMAGE_WITH_TEXT).content("caption")
                .imageOriginalKey("messages/9/5/original.png").imageThumbnailKey("messages/9/5/thumbnail.jpg")
                .createdAt(now.minusDays(35)).build();
        DirectConversation conversation = DirectConversation.builder().id(9L).build();
        when(messages.findImageExpiryCandidates(eq(now.minusDays(14)), any())).thenReturn(List.of());
        when(messages.findPurgeCandidates(eq(now.minusDays(30)), any())).thenReturn(List.of(stale));
        when(conversations.findById(9L)).thenReturn(Optional.of(conversation));
        when(messages.findFirstByConversationIdOrderByIdDesc(9L)).thenReturn(Optional.empty());

        var result = retention.cleanup(now);

        verify(storage).delete("messages/9/5/original.png");
        verify(storage).delete("messages/9/5/thumbnail.jpg");
        verify(messages).deleteAllInBatch(List.of(stale));
        assertThat(result.messagesDeleted()).isEqualTo(1);
        assertThat(conversation.getHistoryPurgedAt()).isEqualTo(now);
        assertThat(conversation.getLastMessageId()).isNull();
        assertThat(conversation.getLastMessageAt()).isNull();
    }

    @Test
    void purgeStillDeletesRowsWhenResidualImageDeletionFails() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 3, 15);
        DirectMessage stale = DirectMessage.builder().id(5L).conversationId(9L)
                .messageType(DirectMessage.MessageType.IMAGE_WITH_TEXT).content("caption")
                .imageOriginalKey("messages/9/5/original.png")
                .createdAt(now.minusDays(35)).build();
        when(messages.findImageExpiryCandidates(eq(now.minusDays(14)), any())).thenReturn(List.of());
        when(messages.findPurgeCandidates(eq(now.minusDays(30)), any())).thenReturn(List.of(stale));
        when(conversations.findById(9L)).thenReturn(Optional.of(DirectConversation.builder().id(9L).build()));
        when(messages.findFirstByConversationIdOrderByIdDesc(9L)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("storage down")).when(storage).delete(anyString());

        var result = retention.cleanup(now);

        // 图片删除失败只记日志，删行照常进行（孤儿文件比无法清理更糟）
        verify(messages).deleteAllInBatch(List.of(stale));
        assertThat(result.messagesDeleted()).isEqualTo(1);
    }
}
