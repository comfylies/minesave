package com.gamesaves.gamesaves.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationResponse {

    private Long id;
    private Long peerId;
    private String peerUsername;
    private String peerNickname;
    private String peerAvatarUrl;
    private DirectMessageResponse lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}
