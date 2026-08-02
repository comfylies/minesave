package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.DirectMessage;
import com.gamesaves.gamesaves.repository.DirectConversationRepository;
import com.gamesaves.gamesaves.repository.DirectMessageRepository;
import com.gamesaves.gamesaves.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageRetentionServiceImplTest {
    @Mock private DirectMessageRepository messages;
    @Mock private DirectConversationRepository conversations;
    @Mock private StorageService storage;
    @InjectMocks private DirectMessageRetentionServiceImpl retention;

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
}
