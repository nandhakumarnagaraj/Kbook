package com.khanabook.saas.service.impl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.khanabook.saas.config.FeatureConfigGuard;
import com.khanabook.saas.entity.FeatureFlag;
import com.khanabook.saas.entity.FeatureFlagAudit;
import com.khanabook.saas.entity.FeatureFlagOverride;
import com.khanabook.saas.repository.FeatureFlagAuditRepository;
import com.khanabook.saas.repository.FeatureFlagOverrideRepository;
import com.khanabook.saas.repository.FeatureFlagRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.FeatureFlagService;

/**
 * Default {@link FeatureFlagService} over V48 tables.
 *
 * Resolution steps and their order are defined in the interface Javadoc.
 * A flag whose row is absent resolves disabled host-side (step 1) but is NOT
 * written into the cache, so the absence is always re-evaluated and an
 * externally-created row is honoured without a restart.
 *
 * The Caffeine instance is owned directly by this service (design D4) with
 * {@code expireAfterWrite} equal to the propagation deadline: mutating writes
 * invalidate eagerly on this instance, and the TTL bounds staleness on any peer
 * that was unable to observe the write. Production runs one container, so eager
 * invalidation is effectively immediate.
 */
@Service
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagServiceImpl.class);
    private static final String SCOPE_KILL_SWITCH = "KILL_SWITCH";
    private static final String SCOPE_DEFAULT = "DEFAULT";
    private static final String SCOPE_OVERRIDE = "OVERRIDE";
    private static final String STATE_ENABLED = "ENABLED";
    private static final String STATE_DISABLED = "DISABLED";
    private static final String STATE_ABSENT = "ABSENT";

    private final FeatureFlagRepository flagRepository;
    private final FeatureFlagOverrideRepository overrideRepository;
    private final FeatureFlagAuditRepository auditRepository;
    private final FeatureConfigGuard configGuard;

    private final Cache<String, Boolean> cache;

    public FeatureFlagServiceImpl(FeatureFlagRepository flagRepository,
                                  FeatureFlagOverrideRepository overrideRepository,
                                  FeatureFlagAuditRepository auditRepository,
                                  FeatureConfigGuard configGuard,
                                  @Value("${khanabook.feature-flags.propagation-deadline-seconds:30}")
                                  long propagationDeadlineSeconds) {
        this.flagRepository = flagRepository;
        this.overrideRepository = overrideRepository;
        this.auditRepository = auditRepository;
        this.configGuard = configGuard;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, propagationDeadlineSeconds)))
                .build();
    }

    @Override
    public FlagState resolve(String flagKey, Long restaurantId) {
        return isEnabled(flagKey, restaurantId) ? FlagState.ENABLED : FlagState.DISABLED;
    }

    @Override
    public boolean isEnabled(String flagKey, Long restaurantId) {
        String cacheKey = key(flagKey, restaurantId);
        Boolean cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        boolean enabled = computeEnabled(flagKey, restaurantId);
        cache.put(cacheKey, enabled);
        return enabled;
    }

    private boolean computeEnabled(String flagKey, Long restaurantId) {
        // Step 1: row absent → disabled (Req 30.11).
        FeatureFlag flag = flagRepository.findById(flagKey).orElse(null);
        if (flag == null) {
            return false;
        }
        // Step 2: config guard fails → disabled, dominates persisted state (Req 30.10).
        if (!configGuard.isSatisfied(flagKey)) {
            return false;
        }
        // Step 3: kill switch → disabled, dominates any override (Req 30.8).
        if (flag.isKillSwitched()) {
            return false;
        }
        // Step 4: per-restaurant override (Req 30.6) — null restaurant has no override.
        if (restaurantId != null) {
            FeatureFlagOverride override = overrideRepository
                    .findByFlagKeyAndRestaurantId(flagKey, restaurantId).orElse(null);
            if (override != null) {
                return override.isEnabled();
            }
        }
        // Step 5: rollout default (Req 30.7).
        return flag.isDefaultEnabled();
    }

    @Override
    public Map<String, Boolean> resolveAllForRestaurant(Long restaurantId) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (FeatureFlag flag : flagRepository.findAll()) {
            result.put(flag.getFlagKey(), isEnabled(flag.getFlagKey(), restaurantId));
        }
        return result;
    }

    @Override
    public boolean isProviderProcessable(String flagKey) {
        FeatureFlag flag = flagRepository.findById(flagKey).orElse(null);
        return flag != null
                && !flag.isKillSwitched()
                && configGuard.isSatisfied(flagKey);
    }

    @Override
    @Transactional
    public void setKillSwitch(String flagKey, boolean killSwitched) {
        long now = System.currentTimeMillis();
        FeatureFlag flag = flagRepository.findById(flagKey).orElse(null);
        if (flag == null) {
            throw new IllegalArgumentException("Unknown feature flag: " + flagKey);
        }
        boolean before = flag.isKillSwitched();
        boolean changed = before != killSwitched;
        flag.setKillSwitched(killSwitched);
        flag.setUpdatedAt(now);
        flagRepository.save(flag);
        if (changed) {
            recordAudit(flagKey, SCOPE_KILL_SWITCH, null,
                    before ? STATE_ENABLED : STATE_DISABLED,
                    killSwitched ? STATE_ENABLED : STATE_DISABLED,
                    now);
        }
        invalidate(flagKey, null);
    }

    @Override
    @Transactional
    public void setDefault(String flagKey, boolean defaultEnabled) {
        long now = System.currentTimeMillis();
        FeatureFlag flag = flagRepository.findById(flagKey).orElse(null);
        if (flag == null) {
            throw new IllegalArgumentException("Unknown feature flag: " + flagKey);
        }
        boolean before = flag.isDefaultEnabled();
        boolean changed = before != defaultEnabled;
        flag.setDefaultEnabled(defaultEnabled);
        flag.setUpdatedAt(now);
        flagRepository.save(flag);
        if (changed) {
            recordAudit(flagKey, SCOPE_DEFAULT, null,
                    before ? STATE_ENABLED : STATE_DISABLED,
                    defaultEnabled ? STATE_ENABLED : STATE_DISABLED,
                    now);
        }
        invalidate(flagKey, null);
    }

    @Override
    @Transactional
    public void setOverride(String flagKey, Long restaurantId, boolean enabled) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId is required for an override");
        }
        long now = System.currentTimeMillis();
        FeatureFlagOverride existing = overrideRepository
                .findByFlagKeyAndRestaurantId(flagKey, restaurantId).orElse(null);
        String previous = existing != null
                ? (existing.isEnabled() ? STATE_ENABLED : STATE_DISABLED)
                : STATE_ABSENT;
        if (existing != null) {
            existing.setEnabled(enabled);
            existing.setUpdatedAt(now);
            overrideRepository.save(existing);
        } else {
            overrideRepository.save(new FeatureFlagOverride(flagKey, restaurantId, enabled, now, now));
        }
        String effective = enabled ? STATE_ENABLED : STATE_DISABLED;
        if (!effective.equals(previous)) {
            recordAudit(flagKey, SCOPE_OVERRIDE, restaurantId, previous, effective, now);
        }
        invalidate(flagKey, restaurantId);
    }

    @Override
    @Transactional
    public void clearOverride(String flagKey, Long restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId is required for an override");
        }
        long now = System.currentTimeMillis();
        FeatureFlagOverride existing = overrideRepository
                .findByFlagKeyAndRestaurantId(flagKey, restaurantId).orElse(null);
        if (existing != null) {
            recordAudit(flagKey, SCOPE_OVERRIDE, restaurantId,
                    existing.isEnabled() ? STATE_ENABLED : STATE_DISABLED,
                    STATE_ABSENT, now);
            overrideRepository.delete(existing);
        }
        invalidate(flagKey, restaurantId);
    }

    /**
     * Writes one audit row for a state change, capturing the actor from
     * {@link TenantContext} when present. Audit is best-effort: a failure to
     * persist must not roll back the flag transition itself.
     */
    private void recordAudit(String flagKey, String scope, Long restaurantId,
                             String previous, String newState, long now) {
        try {
            Long actorUserId = TenantContext.getCurrentUserId();
            String actorUsername = TenantContext.getCurrentRole();
            auditRepository.save(new FeatureFlagAudit(flagKey, scope, restaurantId,
                    previous, newState, actorUserId, actorUsername, now));
        } catch (Exception e) {
            log.error("Failed to persist feature_flag_audit flag={} scope={} newState={}", flagKey, scope, newState, e);
        }
    }

    private void invalidate(String flagKey, Long restaurantId) {
        // Restaurant-level change invalidates that restaurant only; a global
        // change invalidates every cached value for the flag.
        if (restaurantId != null) {
            cache.invalidate(key(flagKey, restaurantId));
        } else {
            List<String> toRemove = cache.asMap().keySet().stream()
                    .filter(k -> k.startsWith(flagKey + ":"))
                    .toList();
            toRemove.forEach(cache::invalidate);
        }
    }

    private static String key(String flagKey, Long restaurantId) {
        return flagKey + ":" + (restaurantId != null ? restaurantId : "null");
    }
}