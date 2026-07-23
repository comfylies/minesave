package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.PageDTO;
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
import com.gamesaves.gamesaves.service.StorageService;
import com.gamesaves.gamesaves.util.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
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
    @Mock
    private StorageService storageService;
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

    @Test
    void messageListIncludesOnlyAuthorizedThumbnailUrls() {
        DirectConversation conversation = DirectConversation.builder()
                .id(42L).userOneId(10L).userTwoId(20L).build();
        DirectMessage image = DirectMessage.builder()
                .id(501L).conversationId(42L).senderId(20L)
                .messageType(DirectMessage.MessageType.IMAGE)
                .imageThumbnailKey("messages/42/501/thumbnail.jpg")
                .imageOriginalKey("messages/42/501/original.png")
                .createdAt(LocalDateTime.now()).build();

        ReflectionTestUtils.setField(service, "storageType", "s3");
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdDesc(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(image)));
        when(messageRepository.countByConversationId(42L)).thenReturn(1L);
        when(storageService.generatePresignedUrl("messages/42/501/thumbnail.jpg", 15))
                .thenReturn("https://cos.example/thumbnail");

        PageDTO<DirectMessageResponse> page = service.getMessages(42L, null, 40, 10L);

        assertThat(page.getContent()).singleElement().satisfies(message -> {
            assertThat(message.getImageThumbnailUrl()).isEqualTo("https://cos.example/thumbnail");
            assertThat(message.getImageOriginalUrl()).isNull();
        });
    }

    @Test
    void participantCanRequestAShortLivedOriginalUrl() {
        DirectConversation conversation = DirectConversation.builder()
                .id(42L).userOneId(10L).userTwoId(20L).build();
        DirectMessage image = DirectMessage.builder()
                .id(501L).conversationId(42L).senderId(20L)
                .imageOriginalKey("messages/42/501/original.png").build();

        ReflectionTestUtils.setField(service, "storageType", "s3");
        when(messageRepository.findById(501L)).thenReturn(Optional.of(image));
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(conversation));
        when(storageService.generatePresignedUrl("messages/42/501/original.png", 5))
                .thenReturn("https://cos.example/original");

        assertThat(service.getImageUrl(501L, false, 10L))
                .isEqualTo("https://cos.example/original");
    }

    @Test
    void localStorageDoesNotExposePrivateChatThumbnailsAsStaticUrls() {
        DirectConversation conversation = DirectConversation.builder()
                .id(42L).userOneId(10L).userTwoId(20L).build();
        DirectMessage image = DirectMessage.builder()
                .id(501L).conversationId(42L).senderId(20L)
                .imageThumbnailKey("messages/42/501/thumbnail.jpg")
                .createdAt(LocalDateTime.now()).build();

        ReflectionTestUtils.setField(service, "storageType", "local");
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdDesc(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(image)));
        when(messageRepository.countByConversationId(42L)).thenReturn(1L);

        PageDTO<DirectMessageResponse> page = service.getMessages(42L, null, 40, 10L);

        assertThat(page.getContent()).singleElement()
                .extracting(DirectMessageResponse::getImageThumbnailUrl)
                .isNull();
        verify(storageService, never()).generatePresignedUrl(anyString(), anyInt());
    }
}
