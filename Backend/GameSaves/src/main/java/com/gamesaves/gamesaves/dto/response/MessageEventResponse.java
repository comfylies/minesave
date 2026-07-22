package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEventResponse {
    private String type;
    private Long conversationId;
    private Long messageId;
    private long unreadCount;
}
