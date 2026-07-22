package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import com.gamesaves.gamesaves.service.MessageLongPollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;
import java.util.UUID;

@Service
public class MessageLongPollServiceImpl implements MessageLongPollService {

    private final long timeoutMs;
    private final String epoch;
    private final ConcurrentHashMap<String, DeferredResult<ApiResponse<MessageEventResponse>>> waits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MessageEventResponse> latestEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> cursors = new ConcurrentHashMap<>();

    @Autowired
    public MessageLongPollServiceImpl(@Value("${app.messaging.long-poll-timeout-ms:25000}") long timeoutMs) {
        this(timeoutMs, UUID.randomUUID().toString());
    }

    public MessageLongPollServiceImpl(long timeoutMs, String epoch) {
        this.timeoutMs = timeoutMs;
        this.epoch = epoch;
    }

    @Override
    public DeferredResult<ApiResponse<MessageEventResponse>> awaitEvent(Long userId, String clientId, String clientEpoch, Long cursor) {
        String key = userId + ":" + clientId;
        DeferredResult<ApiResponse<MessageEventResponse>> result = new DeferredResult<>(timeoutMs);
        MessageEventResponse latest = latestEvents.get(userId);
        if (shouldDeliver(latest, clientEpoch, cursor)) {
            result.setResult(ApiResponse.success(latest));
            return result;
        }
        afterInitialLatestCheck(userId);
        DeferredResult<ApiResponse<MessageEventResponse>> previous = waits.put(key, result);
        if (previous != null) previous.setResult(ApiResponse.success(null));
        result.onCompletion(() -> waits.remove(key, result));
        result.onTimeout(() -> result.setResult(ApiResponse.success(null)));

        latest = latestEvents.get(userId);
        if (shouldDeliver(latest, clientEpoch, cursor) && waits.remove(key, result)) {
            result.setResult(ApiResponse.success(latest));
        }
        return result;
    }

    protected void afterInitialLatestCheck(Long userId) {
        // Keeps the registration/recheck boundary observable for deterministic concurrency tests.
    }

    @Override
    public MessageEventResponse complete(Long userId, MessageEventResponse event) {
        event.setCursor(cursors.computeIfAbsent(userId, ignored -> new AtomicLong()).incrementAndGet());
        event.setEpoch(epoch);
        // Events only signal REST refreshes, so coalescing to the latest event cannot lose persisted messages.
        latestEvents.put(userId, event);
        String prefix = userId + ":";
        waits.forEach((key, result) -> {
            if (key.startsWith(prefix) && result.setResult(ApiResponse.success(event))) waits.remove(key, result);
        });
        return event;
    }

    private boolean isNewerThanCursor(MessageEventResponse event, Long cursor) {
        if (event == null) return false;
        return cursor == null || (event.getCursor() != null && event.getCursor() > cursor);
    }

    private boolean shouldDeliver(MessageEventResponse event, String clientEpoch, Long cursor) {
        if (event == null) return false;
        boolean clientEpochIsStale = clientEpoch != null && !Objects.equals(epoch, clientEpoch);
        return clientEpochIsStale || isNewerThanCursor(event, cursor);
    }
}
