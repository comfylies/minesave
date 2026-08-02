package com.gamesaves.gamesaves.service;

import java.time.LocalDateTime;

public interface DirectMessageRetentionService {
    RetentionResult cleanup(LocalDateTime now);

    record RetentionResult(int imagesExpired, int messagesDeleted) { }
}
