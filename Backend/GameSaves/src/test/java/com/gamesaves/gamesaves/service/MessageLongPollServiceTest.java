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
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000, "current-instance");
        DeferredResult<ApiResponse<MessageEventResponse>> result = service.awaitEvent(20L, "browser-a", "current-instance", 41L);
        MessageEventResponse event = new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L);

        service.complete(20L, event);

        assertThat(result.getResult()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> response = (ApiResponse<?>) result.getResult();
        assertThat(response.getData()).isEqualTo(event);
    }

    @Test
    void returnsTheLatestMissedEventAfterTheGapBetweenPollRequests() {
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000, "current-instance");
        MessageEventResponse event = new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L);
        service.complete(20L, event);

        DeferredResult<ApiResponse<MessageEventResponse>> result = service.awaitEvent(20L, "browser-a", "current-instance", 0L);

        assertThat(result.getResult()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) result.getResult()).getData()).isEqualTo(event);
    }

    @Test
    void doesNotRedeliverAnEventAlreadyAcknowledgedByItsDeliveryCursor() {
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000, "current-instance");
        service.complete(20L, new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L));

        DeferredResult<ApiResponse<MessageEventResponse>> firstPoll = service.awaitEvent(20L, "browser-a", "current-instance", null);
        MessageEventResponse delivered = (MessageEventResponse) ((ApiResponse<?>) firstPoll.getResult()).getData();

        DeferredResult<ApiResponse<MessageEventResponse>> nextPoll = service.awaitEvent(20L, "browser-a", delivered.getEpoch(), delivered.getCursor());

        assertThat(nextPoll.getResult()).isNull();
    }

    @Test
    void givesReadEventsADeliveryCursorSoTheyCanBeAcknowledged() {
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000, "current-instance");
        service.complete(20L, new MessageEventResponse("MESSAGE_UPDATED", 3L, null, 0L));

        DeferredResult<ApiResponse<MessageEventResponse>> firstPoll = service.awaitEvent(20L, "browser-a", "current-instance", null);
        MessageEventResponse delivered = (MessageEventResponse) ((ApiResponse<?>) firstPoll.getResult()).getData();

        assertThat(delivered.getCursor()).isPositive();
        DeferredResult<ApiResponse<MessageEventResponse>> nextPoll = service.awaitEvent(20L, "browser-a", delivered.getEpoch(), delivered.getCursor());
        assertThat(nextPoll.getResult()).isNull();
    }

    @Test
    void deliversTheCurrentInstancesFirstEventWhenTheClientPresentsAnOlderEpoch() {
        MessageLongPollService service = new MessageLongPollServiceImpl(25_000, "current-instance");
        MessageEventResponse event = service.complete(20L, new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L));
        DeferredResult<ApiResponse<MessageEventResponse>> result = service.awaitEvent(20L, "browser-a", "previous-instance", 999L);
        MessageEventResponse delivered = (MessageEventResponse) ((ApiResponse<?>) result.getResult()).getData();

        assertThat(delivered).isEqualTo(event);
        assertThat(delivered.getEpoch()).isEqualTo("current-instance");
        assertThat(delivered.getCursor()).isPositive();
    }

    @Test
    void catchesAnEventCompletedBetweenTheInitialCheckAndWaitRegistration() {
        MessageLongPollServiceImpl service = new MessageLongPollServiceImpl(25_000, "current-instance") {
            @Override
            protected void afterInitialLatestCheck(Long userId) {
                complete(userId, new MessageEventResponse("MESSAGE_UPDATED", 3L, 42L, 4L));
            }
        };

        DeferredResult<ApiResponse<MessageEventResponse>> result = service.awaitEvent(20L, "browser-a", "current-instance", 0L);

        assertThat(result.getResult()).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse<?>) result.getResult()).getData()).isInstanceOf(MessageEventResponse.class);
    }
}
