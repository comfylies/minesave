package com.gamesaves.gamesaves.dto.response;

import com.gamesaves.gamesaves.entity.DirectMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DirectMessageResponse {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private DirectMessage.MessageType messageType;
    private String content;
    private String imageOriginalKey;
    private String imageThumbnailKey;
    private String imageOriginalUrl;
    private String imageThumbnailUrl;
    private Integer imageWidth;
    private Integer imageHeight;
    private boolean imageExpired;
    private LocalDateTime createdAt;

    public static DirectMessageResponse fromEntity(DirectMessage message) {
        return DirectMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .imageOriginalKey(message.getImageOriginalKey())
                .imageThumbnailKey(message.getImageThumbnailKey())
                .imageWidth(message.getImageWidth())
                .imageHeight(message.getImageHeight())
                .imageExpired(message.getImageExpiredAt() != null)
                .createdAt(message.getCreatedAt())
                .build();
    }

}
