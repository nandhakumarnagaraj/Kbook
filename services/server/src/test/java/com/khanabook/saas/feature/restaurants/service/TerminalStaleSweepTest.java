package com.khanabook.saas.feature.restaurants.service;

import com.khanabook.saas.core.utility.JwtUtility;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.notifications.data.DeviceRegistrationRequestRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the stale-terminal sweeper: ACTIVE terminals that stopped
 * sending token-authenticated sync heartbeats (last_seen_at older than the
 * threshold, or never written) are deactivated, their credential version is
 * bumped so outstanding tokens stop authorizing pushes, and the restaurant's
 * next ACTIVE terminal is promoted to primary.
 */
@ExtendWith(MockitoExtension.class)
class TerminalStaleSweepTest {

    @Mock private RestaurantTerminalRepository terminalRepository;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;
    @Mock private DeviceRegistrationRequestRepository requestRepository;
    @Mock private JwtUtility jwtUtility;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TerminalManagementService service;

    @BeforeEach
    void setUp() {
        // Primary promotion path looks up the primary among remaining ACTIVEs.
        lenient().when(terminalRepository.findByRestaurantIdAndIsPrimaryTrue(anyLong()))
                .thenReturn(Optional.empty());
    }

    private RestaurantTerminal terminal(Long id, Long restaurantId, String series, Long lastSeenAt) {
        RestaurantTerminal t = new RestaurantTerminal();
        t.setId(id);
        t.setRestaurantId(restaurantId);
        t.setTerminalSeries(series);
        t.setStatus("ACTIVE");
        t.setIsActive(true);
        t.setIsPrimary(false);
        t.setCredentialVersion(1L);
        t.setLastSeenAt(lastSeenAt);
        return t;
    }

    @Test
    void sweep_deactivatesStaleTerminal_andRevokesCredential() {
        RestaurantTerminal stale = terminal(1L, 100L, "A", 1L /* ancient */);
        when(terminalRepository.findStaleActive(anyLong())).thenReturn(List.of(stale));
        when(terminalRepository.save(any(RestaurantTerminal.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.sweepStaleTerminals();

        assertThat(stale.getStatus()).isEqualTo("INACTIVE");
        assertThat(stale.getIsActive()).isFalse();
        assertThat(stale.getCredentialVersion()).isEqualTo(2L);
        verify(securityAuditService).record(eq("TERMINAL_SYNC"), eq("TERMINAL_STALE_SWEPT"),
                eq("A"), any());
    }

    @Test
    void sweep_neverSeenTerminal_countsAsStale() {
        // last_seen_at NULL: a pre-heartbeat terminal holding an ACTIVE slot forever.
        RestaurantTerminal neverSeen = terminal(2L, 100L, "B", null);
        when(terminalRepository.findStaleActive(anyLong())).thenReturn(List.of(neverSeen));
        when(terminalRepository.save(any(RestaurantTerminal.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.sweepStaleTerminals();

        assertThat(neverSeen.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void sweep_noStaleTerminals_noop() {
        when(terminalRepository.findStaleActive(anyLong())).thenReturn(List.of());

        service.sweepStaleTerminals();

        verify(terminalRepository, never()).save(any(RestaurantTerminal.class));
        verify(securityAuditService, never())
                .record(anyString(), anyString(), any(), any());
    }

    @Test
    void sweep_primaryStale_demotesAndFreesSlot() {
        RestaurantTerminal stalePrimary = terminal(3L, 200L, "C", 1L);
        stalePrimary.setIsPrimary(true);
        when(terminalRepository.findStaleActive(anyLong())).thenReturn(List.of(stalePrimary));
        when(terminalRepository.save(any(RestaurantTerminal.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.sweepStaleTerminals();

        assertThat(stalePrimary.getStatus()).isEqualTo("INACTIVE");
        assertThat(stalePrimary.getIsPrimary()).isFalse();
        // The demotion clears this restaurant's primary slot...
        verify(terminalRepository).save(stalePrimary);
    }
}
