package com.khanabook.saas.service;

import com.khanabook.saas.repository.TokenBlocklistRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenBlocklistCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlocklistCleanupService.class);

    private final TokenBlocklistRepository tokenBlocklistRepository;

    // Run every hour — remove tokens whose JWT expiry has already passed
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupExpiredTokens() {
        int deleted = tokenBlocklistRepository.deleteExpiredTokens(System.currentTimeMillis());
        if (deleted > 0) {
            log.info("Token blocklist cleanup: removed {} expired entries", deleted);
        }
    }
}
