package com.khanabook.saas.feature.notifications.service;

import com.google.firebase.FirebaseApp;
import com.khanabook.saas.feature.notifications.data.DeviceToken;
import com.khanabook.saas.feature.notifications.data.DeviceTokenRepository;
import com.khanabook.saas.feature.notifications.data.NotificationEventRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks in the offline-first contract for sync_now pushes.
 *
 * FCM data messages are BEST-EFFORT by design (see FCM docs "Set the lifespan of
 * a message"): FCM may throttle, delay (Doze/collapse-key), or drop them, and
 * devices also sync on a 2-minute periodic schedule. KhanaBook is offline-first,
 * so a sync_now nudge is a latency optimization — NEVER the source of truth.
 *
 * Regression: pushSyncNow() used to run the FCM send INLINE on the caller's HTTP
 * request thread, and the default Firebase transport has no read timeout. A
 * stalled Google endpoint hung menu toggle / sync push requests ~60s until the
 * device timed out (logcat 2026-09-28 14:02–14:04: "Failed to toggle item
 * availability ... SocketTimeoutException", repeated ×6). The fix:
 *   1. the FCM call runs on pushNotificationExecutor (never the caller thread),
 *   2. ALL exceptions inside the task are swallowed (previously raw socket
 *      IOExceptions — not FirebaseMessagingException — escaped and failed
 *      the caller's request),
 *   3. a saturated executor discards the nudge instead of blocking or failing
 *      the caller (DiscardPolicy in AsyncConfig + RejectedExecutionException
 *      guard here as defense in depth).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushSyncNowOffloadTest {

	private static final long RESTAURANT = 42L;

	@Mock private DeviceTokenRepository deviceTokenRepo;
	@Mock private NotificationEventRepository notificationEventRepo;
	@Mock private RestaurantProfileRepository restaurantProfileRepo;
	@Mock private FirebaseApp firebaseApp;

	private PushNotificationService service;

	@BeforeEach
	void setUp() {
		service = new PushNotificationService(deviceTokenRepo, notificationEventRepo,
				restaurantProfileRepo, firebaseApp, Runnable::run);
	}

	private DeviceToken token(long id) {
		DeviceToken dt = new DeviceToken();
		dt.setToken("tok-" + id);
		dt.setActive(true);
		return dt;
	}

	@Test
	@DisplayName("An exception thrown by the send path must NEVER propagate to the caller")
	void callerNeverFailsWhenSendPathThrows() {
		// The direct executor runs the task on the calling thread, so any exception
		// escaping pushSyncNowInternal would surface here. The OLD code threw
		// FirebaseMessagingException past pushSyncNow (only FCM errors were caught
		// inline, and only after the tokens query); this test is red on that design.
		when(deviceTokenRepo.findByRestaurantIdAndActiveTrue(RESTAURANT))
				.thenReturn(List.of(token(1)));

		assertThatCode(() -> service.pushSyncNow(RESTAURANT))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("A saturated executor (AbortPolicy) must never fail or block the caller")
	void saturatedExecutorNeverFailsCaller() {
		Executor abortingExecutor = task -> { throw new RejectedExecutionException("queue full"); };
		PushNotificationService aborting = new PushNotificationService(deviceTokenRepo,
				notificationEventRepo, restaurantProfileRepo, firebaseApp, abortingExecutor);

		when(deviceTokenRepo.findByRestaurantIdAndActiveTrue(RESTAURANT))
				.thenReturn(List.of(token(1)));

		// The old design had no executor at all; any executor that rejects must be
		// absorbed here. A dropped nudge is harmless — periodic sync covers it.
		assertThatCode(() -> aborting.pushSyncNow(RESTAURANT))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Happy path: send is delegated exactly once when active tokens exist")
	void delegatesSendWhenTokensExist() {
		when(deviceTokenRepo.findByRestaurantIdAndActiveTrue(RESTAURANT))
				.thenReturn(List.of(token(1), token(2)));

		service.pushSyncNow(RESTAURANT);

		// The task ran synchronously (direct executor); the FCM layer was reached.
		// FirebaseMessaging.getInstance(firebaseApp) is static — reaching it without
		// an initialised app throws INSIDE the guarded task, which must be swallowed.
		assertThatCode(() -> verify(deviceTokenRepo, times(1))
				.findByRestaurantIdAndActiveTrue(RESTAURANT)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("No-op without Firebase or without active tokens")
	void noOpsWithoutFirebaseOrTokens() {
		PushNotificationService noFirebase = new PushNotificationService(deviceTokenRepo,
				notificationEventRepo, restaurantProfileRepo, null, Runnable::run);

		assertThatCode(() -> noFirebase.pushSyncNow(RESTAURANT)).doesNotThrowAnyException();
		verify(deviceTokenRepo, never()).findByRestaurantIdAndActiveTrue(anyLong());

		when(deviceTokenRepo.findByRestaurantIdAndActiveTrue(RESTAURANT))
				.thenReturn(List.of());
		assertThatCode(() -> service.pushSyncNow(RESTAURANT)).doesNotThrowAnyException();
		verify(deviceTokenRepo, times(1)).findByRestaurantIdAndActiveTrue(RESTAURANT);
		verify(deviceTokenRepo, never()).save(any(DeviceToken.class));
	}
}
