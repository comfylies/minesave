package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.entity.Savings;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.service.SavingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class SavingsServiceImpl implements SavingsService {

    private final SavingsRepository savingsRepository;

    public SavingsServiceImpl(SavingsRepository savingsRepository) {
        this.savingsRepository = savingsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Savings> getSavingsByArticleId(Long articleId) {
        return savingsRepository.findByArticleId(articleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Savings> checkZipDedup(String zipHash) {
        return savingsRepository.findByZipHash(zipHash);
    }
}
