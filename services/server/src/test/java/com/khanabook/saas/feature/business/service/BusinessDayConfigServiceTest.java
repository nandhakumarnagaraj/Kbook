package com.khanabook.saas.feature.business.service;

import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rollout gate that stands between "cutoff implemented" and "cutoff switched on".
 *
 * <p>Each case here protects a receipt: a terminal that cannot compute a business day must
 * block activation, and an activation date that bills were already filed under must not move.
 */
class BusinessDayConfigServiceTest {

    private static final Long TENANT = 7L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private RestaurantProfileRepository profiles;
    private RestaurantTerminalRepository terminals;
    private BusinessDayConfigService service;
    private RestaurantProfile profile;

    @BeforeEach
    void setUp() {
        profiles = mock(RestaurantProfileRepository.class);
        terminals = mock(RestaurantTerminalRepository.class);
        service = new BusinessDayConfigService(profiles, terminals);
        ReflectionTestUtils.setField(service, "businessDayMinVersion", 60);

        profile = new RestaurantProfile();
        profile.setRestaurantId(TENANT);
        profile.setTimezone("Asia/Kolkata");

        when(profiles.findByRestaurantId(TENANT)).thenReturn(Optional.of(profile));
        when(profiles.save(any(RestaurantProfile.class))).thenAnswer(call -> call.getArgument(0));
        when(terminals.findByRestaurantIdAndStatus(TENANT, "ACTIVE")).thenReturn(List.of());
        when(terminals.findByRestaurantIdOrderByIdAsc(TENANT)).thenReturn(List.of());
    }

    private RestaurantTerminal terminal(long id, String series, boolean capable) {
        RestaurantTerminal terminal = new RestaurantTerminal();
        ReflectionTestUtils.setField(terminal, "id", id);
        terminal.setTerminalSeries(series);
        terminal.setStatus("ACTIVE");
        terminal.setCutoffCapable(capable);
        return terminal;
    }

    @Test
    @DisplayName("a build below the configured version is not capable, and version 0 trusts nobody")
    void capabilityRequiresAConfiguredBuild() {
        assertTrue(service.isCutoffCapable(60));
        assertFalse(service.isCutoffCapable(59));
        assertFalse(service.isCutoffCapable(null));

        ReflectionTestUtils.setField(service, "businessDayMinVersion", 0);
        assertFalse(service.isCutoffCapable(9999),
                "an unconfigured gate must trust no build, not every build");
    }

    @Test
    @DisplayName("registration records capability once, and only from a trusted build")
    void capabilityIsRecordedButNeverDowngraded() {
        RestaurantTerminal fresh = terminal(1L, "A", false);

        assertFalse(service.recordCapability(fresh, 59));
        assertFalse(service.recordCapability(fresh, null));
        assertFalse(fresh.getCutoffCapable());

        assertTrue(service.recordCapability(fresh, 60));
        assertTrue(fresh.getCutoffCapable());
        verify(terminals, times(1)).save(fresh);

        assertFalse(service.recordCapability(fresh, 60), "the second call is a no-op");
        verify(terminals, times(1)).save(fresh);
    }

    @Test
    @DisplayName("activation is refused while any active terminal cannot compute a business day")
    void activationIsBlockedByAnIncapableTerminal() {
        when(terminals.findByRestaurantIdAndStatus(TENANT, "ACTIVE"))
                .thenReturn(List.of(terminal(1L, "A", true), terminal(2L, "B", false)));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "04:00", LocalDate.now(ZONE).plusDays(1)));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertTrue(failure.getReason().contains("B"),
                "the blocked terminal must be named: " + failure.getReason());
        assertNull(profile.getCutoffEffectiveFrom(), "nothing may be written while blocked");
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("activation succeeds once every active terminal is capable")
    void activationSucceedsWhenEveryTerminalIsCapable() {
        when(terminals.findByRestaurantIdAndStatus(TENANT, "ACTIVE"))
                .thenReturn(List.of(terminal(1L, "A", true)));
        LocalDate effective = LocalDate.now(ZONE).plusDays(1);

        BusinessDayConfigService.BusinessDayConfig config = service.update(TENANT, "04:00", effective);

        assertTrue(config.active());
        assertEquals("04:00", config.cutoffTime());
        assertEquals(LocalTime.of(4, 0), profile.getBusinessDayCutoffTime());
        assertEquals(effective.atStartOfDay(ZONE).toInstant().toEpochMilli(),
                profile.getCutoffEffectiveFrom());
    }

    @Test
    @DisplayName("midnight with an effective date is refused instead of silently staying on calendar days")
    void midnightCutoffCannotBeActivated() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "00:00", LocalDate.now(ZONE)));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    @DisplayName("an afternoon cutoff is treated as a mistyped closing time")
    void cutoffMustBeBeforeNoon() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "15:30", LocalDate.now(ZONE).plusDays(1)));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    @DisplayName("a cutoff that does not parse is rejected, not silently treated as midnight")
    void unparseableCutoffIsRejected() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "4am", LocalDate.now(ZONE).plusDays(1)));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    @DisplayName("activation cannot be backdated")
    void effectiveDateCannotBeInThePast() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "04:00", LocalDate.now(ZONE).minusDays(1)));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    @DisplayName("a cutoff already in force pins its activation date")
    void inForceCutoffCannotBeMoved() {
        profile.setBusinessDayCutoffTime(LocalTime.of(4, 0));
        profile.setCutoffEffectiveFrom(LocalDate.now(ZONE).minusDays(3)
                .atStartOfDay(ZONE).toInstant().toEpochMilli());

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "04:00", LocalDate.now(ZONE).plusDays(2)));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }

    @Test
    @DisplayName("a scheduled cutoff may be postponed but never pulled closer")
    void scheduledCutoffOnlyMovesForward() {
        LocalDate scheduled = LocalDate.now(ZONE).plusDays(5);
        profile.setBusinessDayCutoffTime(LocalTime.of(4, 0));
        profile.setCutoffEffectiveFrom(scheduled.atStartOfDay(ZONE).toInstant().toEpochMilli());

        assertThrows(ResponseStatusException.class,
                () -> service.update(TENANT, "04:00", scheduled.minusDays(1)));
        assertEquals(scheduled.atStartOfDay(ZONE).toInstant().toEpochMilli(),
                profile.getCutoffEffectiveFrom(), "the earlier date must not be applied");

        service.update(TENANT, "04:00", scheduled.plusDays(1));
        assertEquals(scheduled.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli(),
                profile.getCutoffEffectiveFrom());
    }

    @Test
    @DisplayName("deactivation needs no gate: calendar days are what every build already computes")
    void deactivationIsAlwaysAllowed() {
        profile.setBusinessDayCutoffTime(LocalTime.of(4, 0));
        profile.setCutoffEffectiveFrom(Instant.now().toEpochMilli());
        when(terminals.findByRestaurantIdAndStatus(TENANT, "ACTIVE"))
                .thenReturn(List.of(terminal(1L, "A", false)));

        BusinessDayConfigService.BusinessDayConfig config = service.update(TENANT, "04:00", null);

        assertFalse(config.active());
        assertNull(profile.getCutoffEffectiveFrom());
        assertNotNull(config.terminals());
    }
}
