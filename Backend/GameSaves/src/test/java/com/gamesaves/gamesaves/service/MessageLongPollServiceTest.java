package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.MessageEventResponse;
import com.gamesaves.gamesaves.service.impl.MessageLongPollServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.DeferredResult;

import static org.assertj.core.api.Assertions.assertThat;

class MessageLongPollServiceTest {

    @Test
    void completesTheMatchingUsersOutstandingWait() {
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000);
        DeferredResult<ApiResponse<MessageEventResponse>> result = service.awaitEvent(20L, "browser-a", 41L);
        MessageEventResponse event = new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L);

        service.complete(20L, event);

        assertThat(result.getResult()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> response = (ApiResponse<?>) result.getResult();
        assertThat(response.getData()).isEqualTo(event);
    }

    @Test
    void returnsTheLatestMissedEventAfterTheGapBetweenPollRequests() {
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000);
        MessageEventResponse event = new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L);
        service.complete(20L, event);

        DeferredResult<ApiResponse<MessageEventResponse>> result = service.awaitEvent(20L, "browser-a", 41L);

        assertThat(result.getResult()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) result.getResult()).getData()).isEqualTo(event);
    }
}
