package com.gamesaves.gamesaves.service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DirectMessageRetentionScheduler {
    private final DirectMessageRetentionService retentionService;

    public DirectMessageRetentionScheduler(DirectMessageRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${app.messaging.retention.cron:0 15 3 * * *}")
    public void scheduledCleanup() { retentionService.cleanup(LocalDateTime.now()); }

    @PostConstruct
    public void startupCleanup() { retentionService.cleanup(LocalDateTime.now()); }
}
