package com.khanabook.saas.service;

import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private PresenceService presenceService;

    /**
     * The throttle lives in the SQL cutoff, so what matters here is that the
     * cutoff handed to the query is exactly one interval behind "now". A larger
     * one would write on every poll; a smaller one would starve the timestamp.
     */
    @Test
    void writesTheRowWithACutoffOneIntervalBehindNow() {
        long before = System.currentTimeMillis();

        presenceService.touch(42L);

        ArgumentCaptor<Long> now = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
        verify(userRepository).touchLastSeen(eq(42L), now.capture(), cutoff.capture());

        assertThat(now.getValue()).isGreaterThanOrEqualTo(before);
        assertThat(now.getValue() - cutoff.getValue())
                .as("the cutoff is the throttle: one write per interval, not one per request")
                .isEqualTo(PresenceService.HEARTBEAT_THROTTLE_MS);
    }

    @Test
    void doesNothingWithoutAUserId() {
        presenceService.touch(null);

        verify(userRepository, never()).touchLastSeen(anyLong(), anyLong(), anyLong());
    }

    /** A heartbeat must never be the reason a working page shows an error. */
    @Test
    void swallowsAFailedWrite() {
        when(userRepository.touchLastSeen(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("connection reset"));

        assertThatCode(() -> presenceService.touch(7L)).doesNotThrowAnyException();
    }
}
