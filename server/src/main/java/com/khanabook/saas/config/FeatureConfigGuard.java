package com.khanabook.saas.config;

import java.util.Map;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-flag predicate over resolved configuration (Requirement 27.7, 30.10).
 *
 * Step 2 of flag resolution: when the guard fails for a flag, the flag resolves
 * disabled for every restaurant regardless of persisted state. This satisfies
 * Requirement 27.7 (server starts with a feature disabled when a required key is
 * absent) and property P25.
 *
 * Guards and the configuration they depend on:
 * <ul>
 *   <li>{@code easebuzz_payments} — EASEBUZZ_MERCHANT_KEY + EASEBUZZ_SALT</li>
 *   <li>{@code notifications}   — a Firebase credential (FIREBASE_CREDENTIALS_PATH
 *       or FIREBASE_REFRESH_TOKEN)</li>
 *   <li>{@code marketplace_orders} — constant {@code true}; Swiggy/Zomato keys are
 *       per-restaurant, not global (design section 1)</li>
 * </ul>
 * Flags whose feature has no global credential dependency resolve to true so the
 * persisted flag state alone governs them.
 */
@Component
public class FeatureConfigGuard {

    @Value("${easebuzz.merchant-key:}")
    private String easebuzzMerchantKey;

    @Value("${easebuzz.salt:}")
    private String easebuzzSalt;

    @Value("${firebase.credentials-path:}")
    private String firebaseCredentialsPath;

    @Value("${firebase.refresh-token:}")
    private String firebaseRefreshToken;

    private final Map<String, BooleanSupplier> guards = Map.of(
        "easebuzz_payments", this::easebuzzCredentialsPresent,
        "notifications", this::firebaseCredentialPresent
    );

    /**
     * Returns true when the flag has no configuration dependency, or when the
     * required configuration is present. Flags not listed here and flags with a
     * constant-true guard both return true.
     */
    public boolean isSatisfied(String flagKey) {
        BooleanSupplier guard = guards.get(flagKey);
        if (guard == null) {
            return true;
        }
        return guard.getAsBoolean();
    }

    private boolean easebuzzCredentialsPresent() {
        return notBlank(easebuzzMerchantKey) && notBlank(easebuzzSalt);
    }

    private boolean firebaseCredentialPresent() {
        return notBlank(firebaseCredentialsPath) || notBlank(firebaseRefreshToken);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }
}