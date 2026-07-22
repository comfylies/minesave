package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import com.gamesaves.gamesaves.service.MessageEventDispatcher;
import com.gamesaves.gamesaves.service.MessageLongPollService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageEventDispatcherImpl implements MessageEventDispatcher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageLongPollService longPollService;

    public MessageEventDispatcherImpl(SimpMessagingTemplate messagingTemplate, MessageLongPollService longPollService) {
        this.messagingTemplate = messagingTemplate;
        this.longPollService = longPollService;
    }

    @Override
    public void publish(Long userId, MessageEventResponse event) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", event);
        longPollService.complete(userId, event);
    }
}
