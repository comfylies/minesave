package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.DirectConversation;
import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.service.DirectMessageRetentionService;
import com.gamesaves.gamesaves.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class DirectMessageRetentionServiceImpl implements DirectMessageRetentionService {
    private static final Logger log = LoggerFactory.getLogger(DirectMessageRetentionServiceImpl.class);

    private final DirectMessageRepository messages;
    private final DirectConversationRepository conversations;
    private final StorageService storage;
    // 每个批次在独立事务（REQUIRES_NEW）中执行：查询 + 修改一起提交，
    // 与设计文档 "transactional per batch" 一致，避免整轮清理挤在一个大事务里长占锁和 undo log
    private final TransactionTemplate transactionTemplate;

    @Value("${app.messaging.retention.image-days:14}") private int imageDays = 14;
    @Value("${app.messaging.retention.message-days:30}") private int messageDays = 30;
    @Value("${app.messaging.retention.batch-size:200}") private int batchSize = 200;

    public DirectMessageRetentionServiceImpl(DirectMessageRepository messages, DirectConversationRepository conversations,
                                             StorageService storage, PlatformTransactionManager transactionManager) {
        this.messages = messages; this.conversations = conversations; this.storage = storage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public RetentionResult cleanup(LocalDateTime now) {
        int imagesExpired = 0, messagesDeleted = 0;
        // 图片过期循环：无进展保护 —— 存储故障导致整批删除失败时，失败消息不会获得 imageExpiredAt，
        // 若不加保护会无限重取同一批数据空转。整批零成功即终止本轮，失败的留到下次调度重试。
        while (true) {
            BatchOutcome outcome = transactionTemplate.execute(status -> expireImageBatch(now));
            imagesExpired += outcome.processed;
            if (outcome.batchSize < batchSize) break;
            if (outcome.processed == 0) {
                log.warn("Image expiry made no progress on a full batch of {} messages (likely storage errors) — stopping this run, will retry on next schedule", outcome.batchSize);
                break;
            }
        }
        while (true) {
            BatchOutcome outcome = transactionTemplate.execute(status -> purgeBatch(now));
            messagesDeleted += outcome.processed;
            if (outcome.batchSize < batchSize) break;
        }
        return new RetentionResult(imagesExpired, messagesDeleted);
    }

    /** 一批图片过期（在调用方开启的事务内执行）。processed = 成功过期的数量。 */
    private BatchOutcome expireImageBatch(LocalDateTime now) {
        List<DirectMessage> batch = messages.findImageExpiryCandidates(now.minusDays(imageDays), PageRequest.of(0, batchSize));
        int expired = 0;
        for (DirectMessage message : batch) if (expireImage(message, now)) expired++;
        return new BatchOutcome(batch.size(), expired);
    }

    /** 一批 30 天消息删除（在调用方开启的事务内执行）。processed = 删除的行数。 */
    private BatchOutcome purgeBatch(LocalDateTime now) {
        List<DirectMessage> batch = messages.findPurgeCandidates(now.minusDays(messageDays), PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return new BatchOutcome(0, 0);
        // 删行前最后一次尝试删除残留图片文件：14 天过期失败过的消息（如存储故障恢复前就被 purge）
        // 仍带着 image key，直接删行会产生对象存储孤儿 blob。失败只记日志，不阻塞删行。
        for (DirectMessage message : batch) deleteImageFilesBestEffort(message);
        Set<Long> ids = batch.stream().map(DirectMessage::getConversationId).collect(java.util.stream.Collectors.toSet());
        messages.deleteAllInBatch(batch);
        for (Long id : ids) conversations.findById(id).ifPresent(conversation -> repairConversation(conversation, now));
        return new BatchOutcome(batch.size(), batch.size());
    }

    boolean expireImage(DirectMessage message, LocalDateTime now) {
        try {
            delete(message.getImageOriginalKey()); delete(message.getImageThumbnailKey());
            message.setImageOriginalKey(null); message.setImageThumbnailKey(null);
            message.setImageWidth(null); message.setImageHeight(null); message.setImageExpiredAt(now);
            return true;
        } catch (RuntimeException e) {
            // 记录失败原因；异常发生在 setXxx 之前，实体未修改，消息保持候选资格留待下次重试
            //（两个存储实现的 delete 对已不存在的对象均幂等，重试安全）
            log.warn("Failed to expire image files of message {}: {}", message.getId(), e.getMessage());
            return false;
        }
    }
    private void delete(String key) { if (key != null && !key.isBlank()) storage.delete(key); }

    private void deleteImageFilesBestEffort(DirectMessage message) {
        try {
            delete(message.getImageOriginalKey()); delete(message.getImageThumbnailKey());
        } catch (RuntimeException e) {
            log.warn("Failed to delete residual image files of purged message {}: {}", message.getId(), e.getMessage());
        }
    }

    private void repairConversation(DirectConversation conversation, LocalDateTime now) {
        if (conversation.getHistoryPurgedAt() == null) conversation.setHistoryPurgedAt(now);
        messages.findFirstByConversationIdOrderByIdDesc(conversation.getId()).ifPresentOrElse(latest -> {
            conversation.setLastMessageId(latest.getId()); conversation.setLastMessageAt(latest.getCreatedAt());
        }, () -> { conversation.setLastMessageId(null); conversation.setLastMessageAt(null); });
    }

    private record BatchOutcome(int batchSize, int processed) { }
}
