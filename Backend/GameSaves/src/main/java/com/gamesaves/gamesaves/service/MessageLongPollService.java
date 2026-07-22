package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import org.springframework.web.context.request.async.DeferredResult;

public interface MessageLongPollService {
    DeferredResult<ApiResponse<MessageEventResponse>> awaitEvent(Long userId, String clientId, Long cursor);
    void complete(Long userId, MessageEventResponse event);
}
