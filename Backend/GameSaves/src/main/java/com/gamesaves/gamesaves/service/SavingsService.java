package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.Savings;

import java.util.Optional;

public interface SavingsService {

    Optional<Savings> getSavingsByArticleId(Long articleId);

    Optional<Savings> checkZipDedup(String zipHash);
}
