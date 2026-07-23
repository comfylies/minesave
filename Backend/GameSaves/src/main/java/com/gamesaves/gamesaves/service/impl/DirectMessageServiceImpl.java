package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ConversationResponse;
import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import com.gamesaves.gamesaves.entity.DirectConversation;
import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ForbiddenException;
import com.gamesaves.gamesaves.exception.RateLimitException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ConversationReadStateRepository;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.DirectMessageService;
import com.gamesaves.gamesaves.service.ChatImageService;
import com.gamesaves.gamesaves.service.MessageEventDispatcher;
import com.gamesaves.gamesaves.util.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DirectMessageServiceImpl implements DirectMessageService {

    private static final int MAX_TEXT_LENGTH = 4_000;
    private static final int MAX_PAGE_SIZE = 100;

    private final DirectConversationRepository conversationRepository;
    private final DirectMessageRepository messageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final UserRepository userRepository;
    private final ChatImageService chatImageService;
    private final MessageEventDispatcher eventDispatcher;
    private final RateLimiter rateLimiter;

    @Value("${app.messaging.send-rate-limit:20}")
    private int sendRateLimit;

    @Value("${app.messaging.send-rate-window-seconds:60}")
    private int sendRateWindowSeconds;

    @Value("${app.messaging.send-burst-limit:5}")
    private int sendBurstLimit;

    @Value("${app.messaging.send-burst-window-seconds:5}")
    private int sendBurstWindowSeconds;

    @Value("${app.messaging.send-burst-ban-seconds:30}")
    private int sendBurstBanSeconds;

    public DirectMessageServiceImpl(DirectConversationRepository conversationRepository,
                                    DirectMessageRepository messageRepository,
                                    ConversationReadStateRepository readStateRepository,
                                    UserRepository userRepository,
                                    ChatImageService chatImageService,
                                    MessageEventDispatcher eventDispatcher,
                                    RateLimiter rateLimiter) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.readStateRepository = readStateRepository;
        this.userRepository = userRepository;
        this.chatImageService = chatImageService;
        this.eventDispatcher = eventDispatcher;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<ConversationResponse> getConversations(Long userId, int page, int size) {
        requireUserId(userId, "User");
        PageRequest pageable = pageRequest(page, size);
        Page<DirectConversation> conversations = conversationRepository
                .findByUserOneIdOrUserTwoIdOrderByLastMessageAtDesc(userId, userId, pageable);

        List<ConversationResponse> content = conversations.getContent().stream()
                .map(conversation -> toConversationResponse(conversation, userId))
                .toList();
        return PageDTO.of(content, conversations.getNumber(), conversations.getSize(), conversations.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PageDTO<DirectMessageResponse> getMessages(Long conversationId, Long beforeId, int size, Long userId) {
        DirectConversation conversation = getParticipantConversation(conversationId, userId);
        if (beforeId != null && beforeId <= 0) {
            throw new BadRequestException("History cursor must be a positive message id");
        }
        PageRequest pageable = pageRequest(0, size);
        Page<DirectMessage> messages = beforeId == null
                ? messageRepository.findByConversationIdOrderByIdDesc(conversation.getId(), pageable)
                : messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(conversation.getId(), beforeId, pageable);

        List<DirectMessage> chronological = new ArrayList<>(messages.getContent());
        Collections.reverse(chronological);
        long totalElements = beforeId == null
                ? messageRepository.countByConversationId(conversation.getId())
                : messageRepository.countByConversationIdAndIdLessThan(conversation.getId(), beforeId);
        return PageDTO.of(chronological.stream().map(DirectMessageResponse::fromEntity).toList(),
                0, messages.getSize(), totalElements);
    }

    @Override
    public DirectMessageResponse send(Long targetUserId, String content, MultipartFile image, Long senderId, String ip) {
        requireUserId(senderId, "Sender");
        requireUserId(targetUserId, "Target user");
        if (Objects.equals(senderId, targetUserId)) {
            throw new BadRequestException("Cannot send a direct message to yourself");
        }

        requireActiveUser(senderId, "Sender");
        requireActiveUser(targetUserId, "Target user");

        String normalizedContent = content == null ? "" : content.trim();
        boolean hasImage = image != null && !image.isEmpty();
        if (!hasImage && normalizedContent.isEmpty()) {
            throw new BadRequestException("A message must include text or an image");
        }
        if (content != null && content.length() > MAX_TEXT_LENGTH) {
            throw new BadRequestException("Message text cannot exceed " + MAX_TEXT_LENGTH + " characters");
        }
        if (hasImage) {
            chatImageService.validate(image);
        }

        // 秒级突发限流：5秒内最多5条，超限封禁30秒
        if (rateLimiter.isIpBanned(ip, "send-message-burst")) {
            throw new RateLimitException("发送过于频繁，请稍后再试");
        }
        if (!rateLimiter.tryAcquireGlobal(ip, "send-message-burst", sendBurstLimit, sendBurstWindowSeconds)) {
            rateLimiter.banIp(ip, "send-message-burst", sendBurstBanSeconds);
            throw new RateLimitException("发送过于频繁，已被限制" + sendBurstBanSeconds + "秒");
        }

        // 分钟级全局限流：每分钟最多 N 条
        if (!rateLimiter.tryAcquireGlobal(String.valueOf(senderId), "send-message", sendRateLimit, sendRateWindowSeconds)) {
            throw new RateLimitException("发送消息太频繁，请稍后再试");
        }

        long userOneId = Math.min(senderId, targetUserId);
        long userTwoId = Math.max(senderId, targetUserId);
        DirectConversation conversation = findOrCreateConversation(userOneId, userTwoId);

        DirectMessage message = messageRepository.save(DirectMessage.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .messageType(messageTypeFor(hasImage, normalizedContent))
                .content(normalizedContent.isEmpty() ? null : normalizedContent)
                .build());

        if (hasImage) {
            ChatImageService.ChatImageData storedImage = chatImageService.store(image, conversation.getId(), message.getId());
            message.setImageOriginalKey(storedImage.originalKey());
            message.setImageThumbnailKey(storedImage.thumbnailKey());
            message.setImageWidth(storedImage.width());
            message.setImageHeight(storedImage.height());
        }

        conversationRepository.advanceLastMessage(conversation.getId(), message.getId(), message.getCreatedAt());
        updateReadStates(conversation, senderId, targetUserId, message.getId());
        publishAfterCommit(senderId, new MessageEventResponse("MESSAGE_UPDATED", conversation.getId(), message.getId(), getUnreadCount(senderId)));
        publishAfterCommit(targetUserId, new MessageEventResponse("MESSAGE_UPDATED", conversation.getId(), message.getId(), getUnreadCount(targetUserId)));
        return DirectMessageResponse.fromEntity(message);
    }

    @Override
    public void markRead(Long conversationId, Long userId) {
        DirectConversation conversation = getParticipantConversation(conversationId, userId);
        readStateRepository.ensureReadState(conversation.getId(), userId);
        messageRepository.findFirstByConversationIdOrderByIdDesc(conversation.getId())
                .ifPresent(message -> readStateRepository.advanceReadState(conversation.getId(), userId, message.getId()));
        publishAfterCommit(userId, new MessageEventResponse("MESSAGE_UPDATED", conversation.getId(), null, getUnreadCount(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        requireUserId(userId, "User");
        return readStateRepository.countUnreadMessages(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String getImageKey(Long messageId, boolean thumbnail, Long userId) {
        DirectMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Direct message", messageId));
        getParticipantConversation(message.getConversationId(), userId);
        String key = thumbnail ? message.getImageThumbnailKey() : message.getImageOriginalKey();
        if (key == null || key.isBlank()) throw new ResourceNotFoundException("Chat image", messageId);
        return key;
    }

    private DirectConversation findOrCreateConversation(long userOneId, long userTwoId) {
        conversationRepository.upsertPair(userOneId, userTwoId);
        return conversationRepository.findPairForUpdate(userOneId, userTwoId)
                .orElseThrow(() -> new IllegalStateException("Conversation upsert did not return a pair"));
    }

    private void updateReadStates(DirectConversation conversation, Long senderId, Long recipientId, Long messageId) {
        long firstUserId = Math.min(senderId, recipientId);
        long secondUserId = Math.max(senderId, recipientId);
        readStateRepository.ensureReadState(conversation.getId(), firstUserId);
        readStateRepository.ensureReadState(conversation.getId(), secondUserId);
        readStateRepository.advanceReadState(conversation.getId(), senderId, messageId);
    }

    private ConversationResponse toConversationResponse(DirectConversation conversation, Long userId) {
        Long peerId = Objects.equals(conversation.getUserOneId(), userId)
                ? conversation.getUserTwoId() : conversation.getUserOneId();
        User peer = userRepository.findById(peerId).orElse(null);
        DirectMessageResponse lastMessage = conversation.getLastMessageId() == null ? null
                : messageRepository.findById(conversation.getLastMessageId())
                .map(DirectMessageResponse::fromEntity).orElse(null);

        return ConversationResponse.builder()
                .id(conversation.getId())
                .peerId(peerId)
                .peerUsername(peer == null ? null : peer.getUsername())
                .peerNickname(peer == null ? null : peer.getNickname())
                .peerAvatarUrl(peer == null ? null : peer.getAvatarUrl())
                .lastMessage(lastMessage)
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(readStateRepository.countUnreadMessagesForConversation(conversation.getId(), userId))
                .build();
    }

    private DirectConversation getParticipantConversation(Long conversationId, Long userId) {
        requireUserId(conversationId, "Conversation");
        requireUserId(userId, "User");
        DirectConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Direct conversation", conversationId));
        if (!Objects.equals(conversation.getUserOneId(), userId) && !Objects.equals(conversation.getUserTwoId(), userId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
        return conversation;
    }

    private void requireActiveUser(Long userId, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException(description + " does not exist"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException(description + " is inactive");
        }
    }

    private void requireUserId(Long userId, String description) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException(description + " id is required");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Invalid pagination arguments");
        }
        return PageRequest.of(page, size);
    }

    private DirectMessage.MessageType messageTypeFor(boolean hasImage, String content) {
        if (!hasImage) {
            return DirectMessage.MessageType.TEXT;
        }
        return content.isEmpty() ? DirectMessage.MessageType.IMAGE : DirectMessage.MessageType.IMAGE_WITH_TEXT;
    }

    private void publishAfterCommit(Long userId, MessageEventResponse event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventDispatcher.publish(userId, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventDispatcher.publish(userId, event);
            }
        });
    }
}
