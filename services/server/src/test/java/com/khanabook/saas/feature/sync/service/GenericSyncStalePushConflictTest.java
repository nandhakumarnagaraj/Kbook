package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.auth.service.UserProfileSyncService;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.service.BillPaymentSyncService;
import com.khanabook.saas.feature.billing.service.BillSyncService;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Another device already changed this since your last sync" has to actually be detectable.
 *
 * <p>{@code handlePushSync} stamps {@code serverUpdatedAt = serverTime} at the TOP of its record
 * loop — before it even looks the existing row up — and the stale-push guard further down decides
 * whether to quarantine by comparing the existing row's {@code serverUpdatedAt} against the
 * INCOMING one. Because that incoming value had just been overwritten with the server's own clock,
 * the comparison was {@code serverTime > serverTime}: never true. So for the three entities the
 * guard covers (category, item variant, restaurant profile) a device that had fallen behind could
 * push an edit built on a snapshot another device had already superseded, and last-write-wins would
 * quietly overwrite the newer state instead of telling the user to pull. No test in the suite named
 * {@code STALE_PUSH_CONFLICT} at all.
 *
 * <p>These tests drive the real push path and assert on both the response the till receives and the
 * row handed to the saver, so the guard cannot silently go vacuous again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericSyncStalePushConflictTest {

	private static final long TENANT = 42L;
	private static final String DEVICE = "device-stale-push-test";

	/** Revision the server last wrote for the row (i.e. what the row carries right now). */
	private static final long SERVER_REVISION = System.currentTimeMillis() - 120_000L;
	/** What a till that pulled before the other device's edit still believes the revision is. */
	private static final long REVISION_THE_STALE_TILL_SAW = SERVER_REVISION - 60_000L;
	/** The edit being pushed is genuinely newer than the row — LWW alone would accept it. */
	private static final long JUST_EDITED_AT = System.currentTimeMillis() - 1_000L;

	@Mock private BillRepository billRepository;
	@Mock private BillPaymentRepository billPaymentRepository;
	@Mock private MenuItemRepository menuItemRepository;
	@Mock private ItemVariantRepository itemVariantRepository;
	@Mock private CategoryRepository categoryRepository;
	@Mock private RestaurantTerminalRepository terminalRepository;
	@Mock private SecurityAuditService securityAuditService;
	@Mock private SyncFallbackSaver syncFallbackSaver;
	@Mock private com.khanabook.saas.feature.staff.service.PermissionService permissionService;
	@Mock private StaffPermissionRevisionRepository revisionRepo;
	@Mock private RelationalIdResolver relationalIdResolver;
	@Mock private TerminalOwnershipService terminalOwnershipService;
	@Mock private BillSyncService billSyncService;
	@Mock private SyncNotificationService syncNotificationService;
	@Mock private UserProfileSyncService userProfileSyncService;
	@Mock private BillPaymentSyncService billPaymentSyncService;
	@Mock private RestaurantProfileRepository profileRepository;

	private GenericSyncService service;

	@BeforeEach
	void setUp() throws Exception {
		service = new GenericSyncService(billRepository, billPaymentRepository, menuItemRepository,
				itemVariantRepository, categoryRepository, terminalRepository, securityAuditService,
				syncFallbackSaver, permissionService, revisionRepo, relationalIdResolver,
				terminalOwnershipService, billSyncService, syncNotificationService,
				userProfileSyncService, billPaymentSyncService);

		setField("terminalSyncStrict", false);
		setField("maxClockSkewMs", 300_000L);
		setField("offlineGraceMs", 2_592_000_000L);

		TenantContext.setCurrentTenant(TENANT);
		TenantContext.setCurrentRole("OWNER");
		TenantContext.setCurrentUserId(1L);

		when(relationalIdResolver.buildMaps(anyList(), any(), anyString()))
				.thenReturn(new RelationalIdResolver.IdMaps());
		doNothing().when(relationalIdResolver).resolve(any(), any());

		when(syncFallbackSaver.saveBatchInNewTx(any(), anyList()))
				.thenAnswer(inv -> inv.getArgument(1));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private void setField(String name, Object value) throws Exception {
		var field = GenericSyncService.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(service, value);
	}

	/** The row as the server holds it: last written at {@link #SERVER_REVISION}. */
	private RestaurantProfile storedProfile() {
		RestaurantProfile stored = new RestaurantProfile();
		stored.setId(9L);
		stored.setLocalId(1L);
		stored.setRestaurantId(TENANT);
		stored.setDeviceId("first-device");
		stored.setShopName("Original Name");
		stored.setCreatedAt(SERVER_REVISION - 90L * 86_400_000L);
		stored.setUpdatedAt(SERVER_REVISION);
		stored.setServerUpdatedAt(SERVER_REVISION);
		stored.setIsDeleted(false);
		return stored;
	}

	/**
	 * What SyncMapper builds from a {@code RestaurantProfileSyncDto}: the till reports the server
	 * revision it last saw for this row, which is how the server can tell it is behind.
	 */
	private RestaurantProfile pushedProfile(Long claimedRevision) {
		RestaurantProfile push = new RestaurantProfile();
		push.setLocalId(1L);
		push.setDeviceId(DEVICE);
		push.setRestaurantId(TENANT);
		push.setShopName("Renamed on the stale till");
		push.setCreatedAt(null);
		push.setUpdatedAt(JUST_EDITED_AT);
		push.setServerUpdatedAt(claimedRevision);
		push.setIsDeleted(false);
		return push;
	}

	@Test
	@DisplayName("an edit built on a superseded snapshot is quarantined, not silently applied")
	void staleSnapshotIsRejected() {
		when(profileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(storedProfile()));

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(pushedProfile(REVISION_THE_STALE_TILL_SAW)), profileRepository);

		assertThat(response.getFailedLocalIds())
				.as("the till must be told its edit was not applied")
				.contains(1L);
		assertThat(response.getFailedReasons().get(1L))
				.as("and why, so the UI can say 'pull to see the latest version'")
				.startsWith("STALE_PUSH_CONFLICT");
		assertThat(response.getSuccessfulLocalIds()).doesNotContain(1L);
		verify(syncFallbackSaver, never()).saveBatchInNewTx(any(), anyList());
	}

	@Test
	@DisplayName("a till that is up to date is not falsely accused")
	void currentSnapshotIsAccepted() {
		when(profileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(storedProfile()));

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(pushedProfile(SERVER_REVISION)), profileRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedReasons()).isEmpty();

		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<List<RestaurantProfile>> captor = ArgumentCaptor.forClass(List.class);
		verify(syncFallbackSaver).saveBatchInNewTx(any(), captor.capture());
		assertThat(captor.getValue().get(0).getShopName()).isEqualTo("Renamed on the stale till");
	}

	@Test
	@DisplayName("a till that has never pulled the row (no revision to report) is still allowed to push")
	void unknownSnapshotIsNotTreatedAsRevisionZero() {
		when(profileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(storedProfile()));

		// A device that has never received this row reports 0 — that is "no information", not
		// "revision zero". Treating it as a real revision would reject the first push from every
		// fresh install whose pull has not succeeded yet.
		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(pushedProfile(0L)), profileRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedReasons()).isEmpty();
	}
}
