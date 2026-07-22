package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.PageDTO;
import com.gamesaves.gamesaves.dto.response.ConversationResponse;
import com.gamesaves.gamesaves.dto.response.DirectMessageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DirectMessageService {

    PageDTO<ConversationResponse> getConversations(Long userId, int page, int size);

    PageDTO<DirectMessageResponse> getMessages(Long conversationId, Long beforeId, int size, Long userId);

    DirectMessageResponse send(Long targetUserId, String content, MultipartFile image, Long senderId, String ip);

    void markRead(Long conversationId, Long userId);

    long getUnreadCount(Long userId);
}
