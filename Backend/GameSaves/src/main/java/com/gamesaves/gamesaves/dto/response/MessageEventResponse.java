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
    private Long cursor;
    private String epoch;

    public MessageEventResponse(String type, Long conversationId, Long messageId, long unreadCount) {
        this.type = type;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.unreadCount = unreadCount;
    }
}
