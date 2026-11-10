package com.khanabook.saas.feature.subscription;

import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.subscription.data.*;
import com.khanabook.saas.feature.subscription.dto.AdminSubscriptionUpdateRequestDTO;
import com.khanabook.saas.feature.subscription.dto.ChangePlanRequestDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionPlanDTO;
import com.khanabook.saas.feature.subscription.dto.SubscriptionSummaryDTO;
import com.khanabook.saas.feature.subscription.service.SubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private RestaurantSubscriptionRepository subscriptionRepository;

    @Mock
    private RestaurantTerminalRepository terminalRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private SubscriptionPlan starterPlan;
    private SubscriptionPlan proPlan;
    private SubscriptionPlan trialPlan;
    private RestaurantSubscription testSubscription;
    private final Long testRestaurantId = 42001L;

    @BeforeEach
    void setUp() {
        starterPlan = new SubscriptionPlan();
        starterPlan.setCode("STARTER");
        starterPlan.setName("Starter Plan");
        starterPlan.setPriceMonthly(new BigDecimal("499.00"));
        starterPlan.setPriceYearly(new BigDecimal("4990.00"));
        starterPlan.setMaxTerminals(1);
        starterPlan.setMaxStaffMembers(2);
        starterPlan.setInventoryEnabled(false);
        starterPlan.setAiMenuEnabled(false);
        starterPlan.setOnlinePaymentsEnabled(true);
        starterPlan.setAdvancedReportsEnabled(false);
        starterPlan.setIsActive(true);

        proPlan = new SubscriptionPlan();
        proPlan.setCode("PRO");
        proPlan.setName("Professional Plan");
        proPlan.setPriceMonthly(new BigDecimal("1299.00"));
        proPlan.setPriceYearly(new BigDecimal("12990.00"));
        proPlan.setMaxTerminals(3);
        proPlan.setMaxStaffMembers(10);
        proPlan.setInventoryEnabled(true);
        proPlan.setAiMenuEnabled(false);
        proPlan.setOnlinePaymentsEnabled(true);
        proPlan.setAdvancedReportsEnabled(true);
        proPlan.setIsActive(true);

        trialPlan = new SubscriptionPlan();
        trialPlan.setCode("FREE_TRIAL");
        trialPlan.setName("Free Trial");
        trialPlan.setPriceMonthly(BigDecimal.ZERO);
        trialPlan.setPriceYearly(BigDecimal.ZERO);
        trialPlan.setMaxTerminals(3);
        trialPlan.setMaxStaffMembers(10);
        trialPlan.setInventoryEnabled(true);
        trialPlan.setAiMenuEnabled(true);
        trialPlan.setOnlinePaymentsEnabled(true);
        trialPlan.setAdvancedReportsEnabled(true);
        trialPlan.setIsActive(true);

        long now = System.currentTimeMillis();
        testSubscription = new RestaurantSubscription();
        testSubscription.setId(101L);
        testSubscription.setRestaurantId(testRestaurantId);
        testSubscription.setPlan(proPlan);
        testSubscription.setStatus(SubscriptionStatus.ACTIVE);
        testSubscription.setBillingCycle(BillingCycle.MONTHLY);
        testSubscription.setStartedAt(now - 1000000L);
        testSubscription.setCurrentPeriodStart(now);
        testSubscription.setCurrentPeriodEnd(now + 15L * 24 * 60 * 60 * 1000L);
        testSubscription.setAutoRenew(true);
    }

    @Test
    @DisplayName("getSubscriptionSummary computes remaining days and active quotas correctly")
    void testGetSubscriptionSummary() {
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(terminalRepository.countByRestaurantIdAndStatus(testRestaurantId, "ACTIVE")).thenReturn(2L);
        when(userRepository.countByRestaurantIdAndIsDeletedFalse(testRestaurantId)).thenReturn(4L);

        SubscriptionSummaryDTO summary = subscriptionService.getSubscriptionSummary(testRestaurantId);

        assertThat(summary).isNotNull();
        assertThat(summary.getPlanCode()).isEqualTo("PRO");
        assertThat(summary.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(summary.getDaysRemaining()).isGreaterThanOrEqualTo(14L);
        assertThat(summary.getActiveTerminalsCount()).isEqualTo(2);
        assertThat(summary.getMaxTerminals()).isEqualTo(3);
        assertThat(summary.getInventoryEnabled()).isTrue();
        assertThat(summary.getAiMenuEnabled()).isFalse();
        assertThat(summary.getFeatures().get("inventory")).isTrue();
        assertThat(summary.getFeatures().get("multi_terminal")).isTrue();
    }

    @Test
    @DisplayName("assertTerminalLimit throws CONFLICT when active terminals equal or exceed plan limit")
    void testAssertTerminalLimitExceeded() {
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(terminalRepository.countByRestaurantIdAndStatus(testRestaurantId, "ACTIVE")).thenReturn(3L);

        assertThatThrownBy(() -> subscriptionService.assertTerminalLimit(testRestaurantId))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.CONFLICT)
                .hasMessageContaining("TERMINAL_LIMIT_REACHED");
    }

    @Test
    @DisplayName("assertTerminalLimit passes when active terminals are below plan limit")
    void testAssertTerminalLimitPasses() {
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(terminalRepository.countByRestaurantIdAndStatus(testRestaurantId, "ACTIVE")).thenReturn(2L);

        // Should not throw
        subscriptionService.assertTerminalLimit(testRestaurantId);
    }

    @Test
    @DisplayName("changePlan prevents downgrade when active terminals exceed target plan limit")
    void testChangePlanPreventsDowngradeWithExcessTerminals() {
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.of(starterPlan));
        when(terminalRepository.countByRestaurantIdAndStatus(testRestaurantId, "ACTIVE")).thenReturn(2L);

        ChangePlanRequestDTO request = new ChangePlanRequestDTO("STARTER", BillingCycle.MONTHLY);

        assertThatThrownBy(() -> subscriptionService.changePlan(testRestaurantId, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.CONFLICT)
                .hasMessageContaining("Cannot switch to Starter Plan");
    }

    @Test
    @DisplayName("changePlan successfully switches plan when requirements are met")
    void testChangePlanSuccess() {
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.of(starterPlan));
        when(terminalRepository.countByRestaurantIdAndStatus(testRestaurantId, "ACTIVE")).thenReturn(1L);
        when(subscriptionRepository.findByRestaurantIdWithLock(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(RestaurantSubscription.class))).thenAnswer(i -> i.getArgument(0));

        ChangePlanRequestDTO request = new ChangePlanRequestDTO("STARTER", BillingCycle.YEARLY);
        SubscriptionSummaryDTO summary = subscriptionService.changePlan(testRestaurantId, request);

        assertThat(summary.getPlanCode()).isEqualTo("STARTER");
        assertThat(summary.getBillingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(summary.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("initializeTrialSubscription sets 14 days and TRIAL status")
    void testInitializeTrialSubscription() {
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.empty());
        when(planRepository.findByCode("FREE_TRIAL")).thenReturn(Optional.of(trialPlan));
        when(subscriptionRepository.save(any(RestaurantSubscription.class))).thenAnswer(i -> i.getArgument(0));

        RestaurantSubscription sub = subscriptionService.initializeTrialSubscription(testRestaurantId);

        assertThat(sub).isNotNull();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(sub.getPlan().getCode()).isEqualTo("FREE_TRIAL");
        assertThat(sub.getTrialEndAt()).isNotNull();
        assertThat(sub.getCurrentPeriodEnd()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("isFeatureAllowed and assertFeatureAllowed respect plan feature gates")
    void testFeatureGates() {
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.of(testSubscription));

        assertThat(subscriptionService.isFeatureAllowed(testRestaurantId, "inventory")).isTrue();
        assertThat(subscriptionService.isFeatureAllowed(testRestaurantId, "ai_menu")).isFalse();

        assertThatThrownBy(() -> subscriptionService.assertFeatureAllowed(testRestaurantId, "ai_menu"))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.FORBIDDEN)
                .hasMessageContaining("FEATURE_LOCKED");
    }

    @Test
    @DisplayName("evaluateSubscriptionExpirations transitions expired subscriptions to GRACE_PERIOD or EXPIRED")
    void testEvaluateSubscriptionExpirations() {
        long now = System.currentTimeMillis();

        RestaurantSubscription inGrace = new RestaurantSubscription();
        inGrace.setRestaurantId(1L);
        inGrace.setStatus(SubscriptionStatus.ACTIVE);
        inGrace.setCurrentPeriodEnd(now - 1000L);
        inGrace.setGracePeriodEndAt(now + 2L * 24 * 60 * 60 * 1000L);

        RestaurantSubscription expired = new RestaurantSubscription();
        expired.setRestaurantId(2L);
        expired.setStatus(SubscriptionStatus.ACTIVE);
        expired.setCurrentPeriodEnd(now - 10000L);
        expired.setGracePeriodEndAt(now - 5000L);

        when(subscriptionRepository.findAllByStatusInAndCurrentPeriodEndBefore(any(), anyLong()))
                .thenReturn(List.of(inGrace, expired));

        subscriptionService.evaluateSubscriptionExpirations();

        assertThat(inGrace.getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        verify(subscriptionRepository, times(2)).save(any(RestaurantSubscription.class));
    }

    @Test
    @DisplayName("adminUpdateSubscription updates plan, status and extends period")
    void testAdminUpdateSubscription() {
        when(subscriptionRepository.findByRestaurantIdWithLock(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.findByRestaurantId(testRestaurantId)).thenReturn(Optional.of(testSubscription));
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.of(starterPlan));
        when(subscriptionRepository.save(any(RestaurantSubscription.class))).thenAnswer(i -> i.getArgument(0));

        AdminSubscriptionUpdateRequestDTO request = new AdminSubscriptionUpdateRequestDTO();
        request.setPlanCode("STARTER");
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setExtendDays(30);
        request.setNotes("Extended by support");

        SubscriptionSummaryDTO summary = subscriptionService.adminUpdateSubscription(testRestaurantId, request);

        assertThat(summary.getPlanCode()).isEqualTo("STARTER");
        assertThat(summary.getNotes()).isEqualTo("Extended by support");
    }
}
