package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.DirectConversation;
import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.service.DirectMessageRetentionService;
import com.gamesaves.gamesaves.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class DirectMessageRetentionServiceImpl implements DirectMessageRetentionService {
    private final DirectMessageRepository messages;
    private final DirectConversationRepository conversations;
    private final StorageService storage;
    @Value("${app.messaging.retention.image-days:14}") private int imageDays = 14;
    @Value("${app.messaging.retention.message-days:30}") private int messageDays = 30;
    @Value("${app.messaging.retention.batch-size:200}") private int batchSize = 200;

    public DirectMessageRetentionServiceImpl(DirectMessageRepository messages, DirectConversationRepository conversations, StorageService storage) {
        this.messages = messages; this.conversations = conversations; this.storage = storage;
    }

    @Override public RetentionResult cleanup(LocalDateTime now) {
        int imagesExpired = 0, messagesDeleted = 0;
        while (true) {
            List<DirectMessage> batch = messages.findImageExpiryCandidates(now.minusDays(imageDays), PageRequest.of(0, batchSize));
            for (DirectMessage message : batch) if (expireImage(message, now)) imagesExpired++;
            if (batch.size() < batchSize) break;
        }
        while (true) {
            List<DirectMessage> batch = messages.findPurgeCandidates(now.minusDays(messageDays), PageRequest.of(0, batchSize));
            if (batch.isEmpty()) break;
            purge(batch, now); messagesDeleted += batch.size();
            if (batch.size() < batchSize) break;
        }
        return new RetentionResult(imagesExpired, messagesDeleted);
    }

    boolean expireImage(DirectMessage message, LocalDateTime now) {
        try {
            delete(message.getImageOriginalKey()); delete(message.getImageThumbnailKey());
            message.setImageOriginalKey(null); message.setImageThumbnailKey(null);
            message.setImageWidth(null); message.setImageHeight(null); message.setImageExpiredAt(now);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }
    private void delete(String key) { if (key != null && !key.isBlank()) storage.delete(key); }
    private void purge(List<DirectMessage> batch, LocalDateTime now) {
        Set<Long> ids = batch.stream().map(DirectMessage::getConversationId).collect(java.util.stream.Collectors.toSet());
        messages.deleteAllInBatch(batch);
        for (Long id : ids) conversations.findById(id).ifPresent(conversation -> repairConversation(conversation, now));
    }
    private void repairConversation(DirectConversation conversation, LocalDateTime now) {
        if (conversation.getHistoryPurgedAt() == null) conversation.setHistoryPurgedAt(now);
        messages.findFirstByConversationIdOrderByIdDesc(conversation.getId()).ifPresentOrElse(latest -> {
            conversation.setLastMessageId(latest.getId()); conversation.setLastMessageAt(latest.getCreatedAt());
        }, () -> { conversation.setLastMessageId(null); conversation.setLastMessageAt(null); });
    }
}
