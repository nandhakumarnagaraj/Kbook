package com.khanabook.saas.security;

import com.khanabook.saas.repository.TokenBlocklistRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup, loads all non-expired revoked tokens into the in-memory cache.
 * Without this, tokens revoked before a server restart would pass the cache
 * check and only be caught by the slower DB fallback on first use.
 */
@Component
@RequiredArgsConstructor
public class TokenRevocationCacheInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationCacheInitializer.class);

    private final TokenBlocklistRepository tokenBlocklistRepository;
    private final TokenRevocationCache tokenRevocationCache;

    @Override
    public void run(ApplicationArguments args) {
        long now = System.currentTimeMillis();
        var active = tokenBlocklistRepository.findAll().stream()
                .filter(t -> t.getExpiresAt() > now)
                .toList();
        active.forEach(t -> tokenRevocationCache.populateFromDb(t.getJti(), t.getExpiresAt()));
        log.info("Token revocation cache preloaded with {} active revoked tokens", active.size());
    }
}
