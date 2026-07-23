package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import com.gamesaves.gamesaves.entity.ConversationReadState;
import com.gamesaves.gamesaves.entity.DirectConversation;
import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.ConversationReadStateRepository;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.service.ChatImageService;
import com.gamesaves.gamesaves.service.MessageEventDispatcher;
import com.gamesaves.gamesaves.util.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceImplTest {

    @Mock
    private DirectConversationRepository conversationRepository;
    @Mock
    private DirectMessageRepository messageRepository;
    @Mock
    private ConversationReadStateRepository readStateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatImageService chatImageService;
    @Mock
    private MessageEventDispatcher eventDispatcher;
    @Mock
    private RateLimiter rateLimiter;
    @InjectMocks
    private DirectMessageServiceImpl service;

    @Test
    void sendAtomicallyCreatesOrRereadsThePairBeforePersistingTheMessage() {
        User sender = User.builder().id(10L).isActive(true).build();
        User recipient = User.builder().id(20L).isActive(true).build();
        DirectConversation existingConversation = DirectConversation.builder()
                .id(42L).userOneId(10L).userTwoId(20L).build();
        DirectMessage persistedMessage = DirectMessage.builder()
                .id(99L)
                .conversationId(42L)
                .senderId(10L)
                .messageType(DirectMessage.MessageType.TEXT)
                .content("race safe")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(recipient));
        when(rateLimiter.isIpBanned(anyString(), anyString())).thenReturn(false);
        when(rateLimiter.tryAcquireGlobal(anyString(), anyString(), anyInt(), anyInt())).thenReturn(true);
        when(conversationRepository.findPairForUpdate(10L, 20L)).thenReturn(Optional.of(existingConversation));
        when(conversationRepository.upsertPair(10L, 20L)).thenReturn(1);
        when(messageRepository.save(any(DirectMessage.class))).thenReturn(persistedMessage);

        DirectMessageResponse response = service.send(20L, "race safe", null, 10L, "127.0.0.1");

        assertThat(response.getConversationId()).isEqualTo(42L);
        verify(conversationRepository).upsertPair(10L, 20L);
        verify(conversationRepository).findPairForUpdate(10L, 20L);
        verify(conversationRepository).advanceLastMessage(42L, 99L, persistedMessage.getCreatedAt());
        verify(messageRepository).save(any(DirectMessage.class));
        verify(readStateRepository).ensureReadState(42L, 10L);
        verify(readStateRepository).advanceReadState(42L, 10L, 99L);
        verify(readStateRepository).ensureReadState(42L, 20L);
    }
}
