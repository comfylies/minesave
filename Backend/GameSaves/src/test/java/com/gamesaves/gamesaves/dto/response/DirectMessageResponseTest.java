package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.DirectMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessageResponseTest {

    @Test
    void expiredImageKeepsTextButDoesNotExposeStorageKeys() {
        DirectMessage message = DirectMessage.builder()
                .messageType(DirectMessage.MessageType.IMAGE_WITH_TEXT)
                .content("keep this text")
                .imageExpiredAt(LocalDateTime.of(2026, 8, 2, 3, 15))
                .build();

        DirectMessageResponse response = DirectMessageResponse.fromEntity(message);

        assertThat(response.isImageExpired()).isTrue();
        assertThat(response.getContent()).isEqualTo("keep this text");
        assertThat(response.getImageOriginalKey()).isNull();
        assertThat(response.getImageThumbnailKey()).isNull();
    }
}
