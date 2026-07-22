package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ConversationResponse;
import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import com.gamesaves.gamesaves.entity.ConversationReadState;
import com.gamesaves.gamesaves.entity.DirectConversation;
import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.ForbiddenException;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ConversationReadStateRepository;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.DirectMessageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public DirectMessageServiceImpl(DirectConversationRepository conversationRepository,
                                    DirectMessageRepository messageRepository,
                                    ConversationReadStateRepository readStateRepository,
                                    UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.readStateRepository = readStateRepository;
        this.userRepository = userRepository;
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
        PageRequest pageable = pageRequest(0, size);
        Page<DirectMessage> messages = beforeId == null
                ? messageRepository.findByConversationIdOrderByIdDesc(conversation.getId(), pageable)
                : messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(conversation.getId(), beforeId, pageable);

        List<DirectMessage> chronological = new ArrayList<>(messages.getContent());
        Collections.reverse(chronological);
        return PageDTO.of(chronological.stream().map(DirectMessageResponse::fromEntity).toList(),
                0, messages.getSize(), messageRepository.countByConversationId(conversation.getId()));
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

        long userOneId = Math.min(senderId, targetUserId);
        long userTwoId = Math.max(senderId, targetUserId);
        DirectConversation conversation = findOrCreateConversation(userOneId, userTwoId);

        DirectMessage message = messageRepository.save(DirectMessage.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .messageType(messageTypeFor(hasImage, normalizedContent))
                .content(normalizedContent.isEmpty() ? null : normalizedContent)
                .build());

        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);
        updateReadStates(conversation, senderId, targetUserId, message.getId());
        return DirectMessageResponse.fromEntity(message);
    }

    @Override
    public void markRead(Long conversationId, Long userId) {
        DirectConversation conversation = getParticipantConversation(conversationId, userId);
        ConversationReadState state = readStateRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                .orElseGet(() -> ConversationReadState.builder()
                        .conversationId(conversation.getId())
                        .userId(userId)
                        .build());
        state.setLastReadMessageId(conversation.getLastMessageId());
        readStateRepository.save(state);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        requireUserId(userId, "User");
        return readStateRepository.countUnreadMessages(userId);
    }

    private DirectConversation findOrCreateConversation(long userOneId, long userTwoId) {
        return conversationRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                .orElseGet(() -> createConversationOrReread(userOneId, userTwoId));
    }

    private DirectConversation createConversationOrReread(long userOneId, long userTwoId) {
        try {
            return conversationRepository.saveAndFlush(DirectConversation.builder()
                    .userOneId(userOneId)
                    .userTwoId(userTwoId)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            return conversationRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                    .orElseThrow(() -> exception);
        }
    }

    private void updateReadStates(DirectConversation conversation, Long senderId, Long recipientId, Long messageId) {
        ConversationReadState senderState = readStateRepository
                .findByConversationIdAndUserId(conversation.getId(), senderId)
                .orElseGet(() -> ConversationReadState.builder()
                        .conversationId(conversation.getId())
                        .userId(senderId)
                        .build());
        senderState.setLastReadMessageId(messageId);
        readStateRepository.save(senderState);

        readStateRepository.findByConversationIdAndUserId(conversation.getId(), recipientId)
                .orElseGet(() -> readStateRepository.save(ConversationReadState.builder()
                        .conversationId(conversation.getId())
                        .userId(recipientId)
                        .lastReadMessageId(null)
                        .build()));
    }

    private ConversationResponse toConversationResponse(DirectConversation conversation, Long userId) {
        Long peerId = Objects.equals(conversation.getUserOneId(), userId)
                ? conversation.getUserTwoId() : conversation.getUserOneId();
        User peer = userRepository.findById(peerId).orElse(null);
        DirectMessageResponse lastMessage = conversation.getLastMessageId() == null ? null
                : messageRepository.findById(conversation.getLastMessageId()).map(DirectMessageResponse::fromEntity).orElse(null);

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
}
