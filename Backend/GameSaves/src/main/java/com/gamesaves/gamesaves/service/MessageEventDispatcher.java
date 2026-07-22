package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.MessageEventResponse;

public interface MessageEventDispatcher {
    void publish(Long userId, MessageEventResponse event);
}
