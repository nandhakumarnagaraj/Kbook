package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.auth.service.UserProfileSyncService;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.service.BillPaymentSyncService;
import com.khanabook.saas.feature.billing.service.BillSyncService;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.sync.data.BaseSyncEntity;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin /admin/businesses "Joined" column shows {@code restaurantprofiles.created_at}, and the
 * till's profile push used to move it.
 *
 * <p>{@code RestaurantProfileSyncDto} carries no {@code createdAt}, so the mapped entity arrives
 * with the field null. {@link GenericSyncService#handlePushSync} normalises every record BEFORE it
 * looks the existing row up — `if (createdAt == null) createdAt = updatedAt` — so the fill that
 * exists to date a brand-new row also rewrote the creation time of an existing one, to the
 * device's {@code updatedAt}: the moment that till last saved a setting. Nothing restored it
 * afterwards, and {@code created_at} is NOT NULL, so every push succeeded while the joining date
 * drifted. A shop that had used the POS for months showed Joined = its last settings edit, which
 * is what made the Joined and Last Used columns look misaligned on the admin table.
 *
 * <p>The guard is entity-wide: creation time is never a device-owned field. These tests drive the
 * real push path ({@code handlePushSync}) and assert on the row actually handed to the saver, so a
 * future refactor that drops the restore fails here rather than in production six weeks later.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericSyncCreatedAtImmutableTest {

	private static final long TENANT = 42L;
	private static final String DEVICE = "device-joined-date-test";
	/** The row was created long ago and the shop has been using the POS since. */
	private static final long JOINED_AT = System.currentTimeMillis() - 90L * 86_400_000L;

	/**
	 * "A setting was just saved on the till." The pushed {@code updatedAt} is what the clock-skew
	 * guard checks, so a fixture dated months back would be rejected as a stale replay before the
	 * save path is ever reached.
	 */
	private static final long JUST_SAVED_AT = System.currentTimeMillis() - 1_000L;

	@Mock private BillRepository billRepository;
	@Mock private BillPaymentRepository billPaymentRepository;
	@Mock private MenuItemRepository menuItemRepository;
	@Mock private com.khanabook.saas.feature.menu.data.ItemVariantRepository itemVariantRepository;
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

		// The saver is the last stop before the database: capture what it is handed.
		when(syncFallbackSaver.saveBatchInNewTx(any(), anyList()))
				.thenAnswer(inv -> inv.getArgument(1));

		Category category = new Category();
		category.setId(77L);
		category.setRestaurantId(TENANT);
		when(categoryRepository.findByRestaurantIdAndDeviceIdAndLocalId(TENANT, DEVICE, 7L))
				.thenReturn(Optional.of(category));
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private <T extends BaseSyncEntity> List<T> capturedSaved() {
		ArgumentCaptor<List<T>> captor = ArgumentCaptor.forClass(List.class);
		verify(syncFallbackSaver).saveBatchInNewTx(any(), captor.capture());
		return captor.getValue();
	}

	/** The row as the server already holds it: joined months ago, last saved at the same moment. */
	private RestaurantProfile storedProfile() {
		RestaurantProfile stored = new RestaurantProfile();
		stored.setId(9L);
		stored.setLocalId(1L);
		stored.setRestaurantId(TENANT);
		stored.setDeviceId("first-device");
		stored.setShopName("Original Name");
		stored.setCreatedAt(JOINED_AT);
		stored.setUpdatedAt(JOINED_AT);
		stored.setServerUpdatedAt(JOINED_AT);
		stored.setIsDeleted(false);
		return stored;
	}

	private RestaurantProfile pushedProfile(Long createdAt, long updatedAt) {
		RestaurantProfile push = new RestaurantProfile();
		push.setLocalId(1L);
		push.setDeviceId(DEVICE);
		push.setRestaurantId(TENANT);
		push.setShopName("Renamed on the POS");
		// Exactly what SyncMapper builds from RestaurantProfileSyncDto: no createdAt on the wire.
		push.setCreatedAt(createdAt);
		push.setUpdatedAt(updatedAt);
		push.setServerUpdatedAt(JOINED_AT);
		push.setIsDeleted(false);
		return push;
	}

	@Test
	@DisplayName("a POS settings push must not move the joining date the admin table shows")
	void profilePushWithoutCreatedAtKeepsTheJoiningDate() {
		when(profileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(storedProfile()));
		long settingsSavedAt = JUST_SAVED_AT;

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(pushedProfile(null, settingsSavedAt)), profileRepository);

		List<RestaurantProfile> savedRows = capturedSaved();
		RestaurantProfile saved = savedRows.get(0);
		assertThat(saved.getCreatedAt())
				.as("Joined must stay the signup date, not the last settings edit")
				.isEqualTo(JOINED_AT);
		assertThat(saved.getUpdatedAt())
				.as("the settings edit is still the newer row")
				.isEqualTo(settingsSavedAt);
		assertThat(saved.getShopName()).isEqualTo("Renamed on the POS");
		assertThat(response.getSuccessfulLocalIds())
				.as("the update path ran, so this is not passing through an insert branch")
				.contains(1L);
	}

	@Test
	@DisplayName("a stale createdAt from any client cannot rewrite it either")
	void pushedCreatedAtCannotOverwriteTheStoredOne() {
		when(profileRepository.findByRestaurantId(TENANT)).thenReturn(Optional.of(storedProfile()));

		// A second device whose clock (or first-install date) is wrong, or an old build that
		// guessed: the field is the server's, so neither value may win.
		service.handlePushSync(TENANT,
				List.of(pushedProfile(JOINED_AT + 20L * 86_400_000L, JUST_SAVED_AT)),
				profileRepository);

		List<RestaurantProfile> savedRows = capturedSaved();
		assertThat(savedRows.get(0).getCreatedAt()).isEqualTo(JOINED_AT);
	}

	@Test
	@DisplayName("the guard covers every entity: a menu-item payload without createdAt keeps its own")
	void entityWhosePayloadOmitsCreatedAtIsProtectedToo() {
		long itemUpdatedAt = JUST_SAVED_AT;
		MenuItem stored = new MenuItem();
		stored.setId(50L);
		stored.setLocalId(5L);
		stored.setDeviceId(DEVICE);
		stored.setRestaurantId(TENANT);
		stored.setName("Paneer");
		stored.setBasePrice(new BigDecimal("100.00"));
		stored.setCategoryId(7L);
		stored.setHasVariants(false);
		stored.setCreatedAt(JOINED_AT);
		stored.setUpdatedAt(JOINED_AT);
		stored.setServerUpdatedAt(JOINED_AT);
		stored.setIsDeleted(false);
		when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(TENANT, DEVICE, List.of(5L)))
				.thenReturn(List.of(stored));

		MenuItem push = new MenuItem();
		push.setId(50L);
		push.setLocalId(5L);
		push.setDeviceId(DEVICE);
		push.setRestaurantId(TENANT);
		push.setName("Paneer Tikka");
		push.setBasePrice(new BigDecimal("120.00"));
		push.setCategoryId(7L);
		push.setHasVariants(false);
		push.setCreatedAt(null);
		push.setUpdatedAt(itemUpdatedAt);
		push.setServerUpdatedAt(JOINED_AT);
		push.setIsDeleted(false);
		push.setChangedFields("name,basePrice");

		service.handlePushSync(TENANT, List.of(push), menuItemRepository);

		List<MenuItem> savedRows = capturedSaved();
		MenuItem saved = savedRows.get(0);
		assertThat(saved.getCreatedAt()).isEqualTo(JOINED_AT);
		assertThat(saved.getName()).isEqualTo("Paneer Tikka");
		assertThat(saved.getBasePrice()).isEqualByComparingTo("120.00");
	}
}
