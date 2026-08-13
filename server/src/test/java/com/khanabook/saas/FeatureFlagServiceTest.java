package com.khanabook.saas;

import com.khanabook.saas.config.FeatureConfigGuard;
import com.khanabook.saas.entity.FeatureFlag;
import com.khanabook.saas.entity.FeatureFlagAudit;
import com.khanabook.saas.repository.FeatureFlagAuditRepository;
import com.khanabook.saas.repository.FeatureFlagOverrideRepository;
import com.khanabook.saas.repository.FeatureFlagRepository;
import com.khanabook.saas.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requirement 30.28/30.29/30.30 — the six operational states reachable by the
 * three-state model (design section 1):
 *
 *   | Intent                   | kill_switched | default_enabled | Overrides            |
 *   | Not yet deployed         | true          | false           | —                    |
 *   | Internal test (staff)    | false         | false           | staff true           |
 *   | Single-restaurant pilot  | false         | false           | pilot true           |
 *   | Staged rollout           | false         | false           | N restaurants true   |
 *   | General availability     | false         | true            | opt-outs false       |
 *   | Emergency kill           | true          | any             | ignored              |
 *
 * Uses EASEBUZZ_MERCHANT_KEY + EASEBUZZ_SALT so the `easebuzz_payments` guard
 * (step 2) passes, isolating the persisted-state logic. Config-forces-disabled
 * (Req 30.30) is tested last because it mutates the shared guard bean and would
 * poison the cached resolution of later tests otherwise.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeatureFlagServiceTest extends BaseIntegrationTest {

    private static final String FLAG = "easebuzz_payments";
    private static final long RESTAURANT_A = 101L;
    private static final long RESTAURANT_B = 102L;

    @Autowired private FeatureFlagService featureFlagService;
    @Autowired private FeatureConfigGuard featureConfigGuard;
    @Autowired private FeatureFlagRepository flagRepository;
    @Autowired private FeatureFlagOverrideRepository overrideRepository;
    @Autowired private FeatureFlagAuditRepository auditRepository;

    @DynamicPropertySource
    static void featureFlagProperties(DynamicPropertyRegistry registry) {
        registry.add("easebuzz.merchant-key", () -> "test_merchant_key");
        registry.add("easebuzz.salt", () -> "test_salt");
        registry.add("khanabook.feature-flags.propagation-deadline-seconds", () -> "30");
    }

    @BeforeEach
    void seedFlagRow() {
        long now = System.currentTimeMillis();
        flagRepository.save(new FeatureFlag(FLAG, true, false, "test flag", now, now));
    }

    @AfterEach
    void cleanTables() {
        overrideRepository.deleteAll();
        auditRepository.deleteAll();
        flagRepository.deleteAll();
    }

    @Test
    void notYetDeployed_resolvesDisabled() {
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isFalse();
    }

    @Test
    void singleRestaurantPilot_enablesOnlyPilot() {
        setKillSwitch(false);
        setDefault(false);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, true);

        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isTrue();
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_B)).isFalse();
        assertThat(featureFlagService.isEnabled(FLAG, null)).isFalse();
    }

    @Test
    void stagedRollout_enablesEachOverrideOnly() {
        setKillSwitch(false);
        setDefault(false);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, true);
        featureFlagService.setOverride(FLAG, RESTAURANT_B, true);

        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isTrue();
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_B)).isTrue();
    }

    @Test
    void generalAvailability_defaultsOn_withOptOutsOff() {
        setKillSwitch(false);
        setDefault(true);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, false);

        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isFalse();
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_B)).isTrue();
    }

    @Test
    void emergencyKill_dominatesEveryOverride() {
        setKillSwitch(true);
        setDefault(true);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, true);

        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isFalse();
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_B)).isFalse();
    }

    @Test
    void absentRow_resolvesDisabled() {
        assertThat(featureFlagService.isEnabled("does_not_exist", RESTAURANT_A)).isFalse();
    }

    @Test
    @Order(100)
    void missingConfig_forcesDisabledEvenWhenPersistedOn() {
        setKillSwitch(false);
        setDefault(true);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, true);
        // Simulate config loss: credentials become blank on a peer instance.
        clearConfigOverrides();
        try {
            assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isFalse();
            assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_B)).isFalse();
        } finally {
            restoreConfig();
        }
    }

    @Test
    void isProviderProcessable_requiresRowPresentNotKillSwitchedAndConfigOk() {
        // kill_switched=true (seed) → not processable.
        assertThat(featureFlagService.isProviderProcessable(FLAG)).isFalse();

        setKillSwitch(false);
        assertThat(featureFlagService.isProviderProcessable(FLAG)).isTrue();

        assertThat(featureFlagService.isProviderProcessable("does_not_exist")).isFalse();
    }

    @Test
    void resolveAllForRestaurant_listsEveryFlagWithEffectiveState() {
        setKillSwitch(false);
        setDefault(true);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, false);

        Map<String, Boolean> all = featureFlagService.resolveAllForRestaurant(RESTAURANT_A);
        assertThat(all).containsKey(FLAG);
        assertThat(all.get(FLAG)).isFalse();

        Map<String, Boolean> other = featureFlagService.resolveAllForRestaurant(RESTAURANT_B);
        assertThat(other.get(FLAG)).isTrue();
    }

    @Test
    void everyMutationWritesAuditRows() {
        setKillSwitch(false);
        setDefault(true);
        featureFlagService.setOverride(FLAG, RESTAURANT_A, true);
        featureFlagService.clearOverride(FLAG, RESTAURANT_A);

        List<FeatureFlagAudit> rows = auditRepository.findByFlagKeyOrderByChangedAtDesc(FLAG);
        assertThat(rows).isNotEmpty();

        FeatureFlagAudit first = rows.get(rows.size() - 1);
        assertThat(first.getScope()).isEqualTo("KILL_SWITCH");
        assertThat(first.getPreviousState()).isEqualTo("ENABLED");
        assertThat(first.getNewState()).isEqualTo("DISABLED");

        assertThat(rows).anyMatch(r -> "DEFAULT".equals(r.getScope()));
        assertThat(rows).anyMatch(r -> "OVERRIDE".equals(r.getScope()));
        assertThat(rows).anyMatch(r -> "ABSENT".equals(r.getPreviousState()));
    }

    @Test
    void overrideFlipInvalidatesCacheEagerly() {
        setKillSwitch(false);
        setDefault(false);

        featureFlagService.setOverride(FLAG, RESTAURANT_A, true);
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isTrue();

        // Exercise an override mutation while the value is cached.
        featureFlagService.setOverride(FLAG, RESTAURANT_A, false);
        assertThat(featureFlagService.isEnabled(FLAG, RESTAURANT_A)).isFalse();
    }

    private void setKillSwitch(boolean value) {
        featureFlagService.setKillSwitch(FLAG, value);
    }

    private void setDefault(boolean value) {
        featureFlagService.setDefault(FLAG, value);
    }

    private void clearConfigOverrides() {
        setGuardField("easebuzzMerchantKey", "");
        setGuardField("easebuzzSalt", "");
    }

    private void restoreConfig() {
        setGuardField("easebuzzMerchantKey", "test_merchant_key");
        setGuardField("easebuzzSalt", "test_salt");
    }

    private void setGuardField(String name, String value) {
        // FeatureServiceImpl reads the guard's @Value fields once at construction;
        // mutate the injected guard's fields to simulate a peer instance (or a
        // restart) with different credentials.
        try {
            var field = FeatureConfigGuard.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(featureConfigGuard, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}