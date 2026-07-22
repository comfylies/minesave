package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import com.gamesaves.gamesaves.service.MessageLongPollService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageLongPollServiceImpl implements MessageLongPollService {

    private final long timeoutMs;
    private final ConcurrentHashMap<String, DeferredResult<ApiResponse<MessageEventResponse>>> waits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MessageEventResponse> latestEvents = new ConcurrentHashMap<>();

    public MessageLongPollServiceImpl(@Value("${app.messaging.long-poll-timeout-ms:25000}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public DeferredResult<ApiResponse<MessageEventResponse>> awaitEvent(Long userId, String clientId, Long cursor) {
        String key = userId + ":" + clientId;
        DeferredResult<ApiResponse<MessageEventResponse>> result = new DeferredResult<>(timeoutMs);
        MessageEventResponse latest = latestEvents.get(userId);
        if (isNewerThanCursor(latest, cursor)) {
            result.setResult(ApiResponse.success(latest));
            return result;
        }
        DeferredResult<ApiResponse<MessageEventResponse>> previous = waits.put(key, result);
        if (previous != null) previous.setResult(ApiResponse.success(null));
        result.onCompletion(() -> waits.remove(key, result));
        result.onTimeout(() -> result.setResult(ApiResponse.success(null)));
        return result;
    }

    @Override
    public void complete(Long userId, MessageEventResponse event) {
        latestEvents.put(userId, event);
        String prefix = userId + ":";
        waits.forEach((key, result) -> {
            if (key.startsWith(prefix) && result.setResult(ApiResponse.success(event))) waits.remove(key, result);
        });
    }

    private boolean isNewerThanCursor(MessageEventResponse event, Long cursor) {
        if (event == null) return false;
        if (cursor == null || event.getMessageId() == null) return true;
        return event.getMessageId() > cursor;
    }
}
