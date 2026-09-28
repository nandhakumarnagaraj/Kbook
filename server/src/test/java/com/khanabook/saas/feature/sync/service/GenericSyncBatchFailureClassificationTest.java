package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.auth.service.UserProfileSyncService;
import com.khanabook.saas.feature.billing.service.BillPaymentSyncService;
import com.khanabook.saas.feature.billing.service.BillSyncService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.SyncNotificationService;
import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the HTTP 500 on menu-item push (logcat 2026-09-28 12:08 IST,
 * device pushing 7 menu items, server 500 → 59s sync failure → infinite retry).
 *
 * The batch saveAll() hits a per-record conflict (unique-constraint on
 * (restaurant_id, category_id, normalized_name), or a stale @Version row).
 * Depending on WHERE JPA blows up, the exception surfaces as:
 *
 *   a) Flush-time, Spring-translated: DataIntegrityViolationException /
 *      ObjectOptimisticLockingFailureException
 *   b) Commit-time, wrapped: TransactionSystemException -> RollbackException
 *      -> PersistenceException -> org.hibernate.exception.ConstraintViolationException
 *   c) Commit-time, Spring ORM wrapper: JpaSystemException -> PersistenceException
 *      -> ConstraintViolationException (NO Spring Data translation in the chain)
 *
 * Shapes (b)/(c) contain only raw JPA/Hibernate types. isRecoverableBatchFailure()
 * walked the cause chain but only matched the Spring Data types from (a), so
 * commit-time conflicts returned false → the failure was rethrown → HTTP 500 →
 * the device retried forever. These tests lock ALL three shapes in as
 * recoverable (per-record fallback, HTTP 200 with failedLocalIds), and lock
 * connection-loss/lock-timeout shapes in as propagate (they cannot be fixed
 * per-record).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericSyncBatchFailureClassificationTest {

	private static final long TENANT = 42L;
	private static final String DEVICE = "device-regression-test";

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

		// No existing rows for this device: every record is an INSERT.
		when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(anyLong(), anyString(), anyList()))
				.thenReturn(List.of());

		when(relationalIdResolver.buildMaps(anyList(), any(), anyString()))
				.thenReturn(new RelationalIdResolver.IdMaps());
		doNothing().when(relationalIdResolver).resolve(any(), any());

		// Category FK resolution: server-side category exists.
		Category category = new Category();
		category.setId(77L);
		category.setRestaurantId(TENANT);
		when(categoryRepository.findByRestaurantIdAndDeviceIdAndLocalId(TENANT, DEVICE, 7L))
				.thenReturn(java.util.Optional.of(category));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private void setField(String name, Object value) throws Exception {
		var f = GenericSyncService.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(service, value);
	}

	private MenuItem menuItem(long localId, String name) {
		MenuItem item = new MenuItem();
		item.setLocalId(localId);
		item.setDeviceId(DEVICE);
		item.setRestaurantId(TENANT);
		item.setName(name);
		item.setBasePrice(new BigDecimal("100.00"));
		item.setUpdatedAt(System.currentTimeMillis());
		item.setCreatedAt(System.currentTimeMillis());
		item.setHasVariants(false);
		item.setCategoryId(7L);
		return item;
	}

	/** Batch aborts; per-record fallback saves record 1 and fails record 2 (the conflict). */
	private void stubBatchAbortThenPerRecord(RuntimeException batchFailure) {
		when(syncFallbackSaver.saveBatchInNewTx(any(), anyList())).thenThrow(batchFailure);
		// Call order follows allRecordsToSave = [record 1, record 2].
		when(syncFallbackSaver.saveRecord(any(), any()))
				.thenAnswer(inv -> inv.getArgument(1))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException(
						"could not execute statement; ux_menuitems_active duplicate"));
	}

	@Test
	@DisplayName("Commit-time TransactionSystemException -> RollbackException -> PersistenceException -> ConstraintViolationException must fall back per-record, not 500")
	void commitTimeWrappedConstraintViolationFallsBackPerRecord() {
		PersistenceException hibernateAbort = new PersistenceException(
				new org.hibernate.exception.ConstraintViolationException(
						"could not execute statement", null, "ux_menuitems_active"));
		RollbackException commitAbort = new RollbackException(
				"Error while committing the transaction", hibernateAbort);
		org.springframework.transaction.TransactionSystemException springWrapper =
				new org.springframework.transaction.TransactionSystemException(
						"Could not commit JPA transaction", commitAbort);

		stubBatchAbortThenPerRecord(springWrapper);

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedLocalIds()).contains(2L);
		assertThat(response.getFailedReasons()).containsKey(2L);
	}

	@Test
	@DisplayName("JpaSystemException wrapping a raw constraint-violation PersistenceException must fall back per-record, not 500")
	void jpaSystemExceptionWithRawPersistenceCauseFallsBackPerRecord() {
		PersistenceException raw = new PersistenceException(
				new org.hibernate.exception.ConstraintViolationException(
						"could not execute statement", null, "ux_menuitems_active"));
		org.springframework.orm.jpa.JpaSystemException wrapped = new org.springframework.orm.jpa.JpaSystemException(raw);

		stubBatchAbortThenPerRecord(wrapped);

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedLocalIds()).contains(2L);
	}

	@Test
	@DisplayName("JPA OptimisticLockException wrapped in RollbackException must fall back per-record, not 500")
	void wrappedOptimisticLockFallsBackPerRecord() {
		RollbackException commitAbort = new RollbackException(
				"Error while committing the transaction", new OptimisticLockException("Row was updated by another transaction"));
		org.springframework.transaction.TransactionSystemException wrapper =
				new org.springframework.transaction.TransactionSystemException("Could not commit JPA transaction", commitAbort);

		stubBatchAbortThenPerRecord(wrapper);

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedLocalIds()).contains(2L);
	}

	@Test
	@DisplayName("Spring-translated DataIntegrityViolationException keeps falling back per-record (old behavior preserved)")
	void translatedDataIntegrityViolationStillFallsBack() {
		stubBatchAbortThenPerRecord(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedLocalIds()).contains(2L);
	}

	@Test
	@DisplayName("Spring-translated optimistic-lock failure keeps falling back per-record (old behavior preserved)")
	void translatedOptimisticLockStillFallsBack() {
		stubBatchAbortThenPerRecord(new org.springframework.orm.ObjectOptimisticLockingFailureException(
				MenuItem.class, 99L));

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedLocalIds()).contains(2L);
	}

	@Test
	@DisplayName("EntityExistsException (same-row insert race) must fall back per-record, not 500")
	void entityExistsFallsBackPerRecord() {
		stubBatchAbortThenPerRecord(new org.springframework.orm.jpa.JpaSystemException(
				new PersistenceException(new EntityExistsException("different object with the same identifier"))));

		PushSyncResponse response = service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository);

		assertThat(response.getSuccessfulLocalIds()).contains(1L);
		assertThat(response.getFailedLocalIds()).contains(2L);
	}

	@Test
	@DisplayName("Connection loss (JDBCConnectionException) is NOT recoverable — must propagate, not silently mark records failed")
	void connectionLossPropagates() {
		PersistenceException connectionLost = new PersistenceException(
				new org.hibernate.exception.JDBCConnectionException("Error calling DriverManager#getConnection", null));
		org.springframework.orm.jpa.JpaSystemException wrapped =
				new org.springframework.orm.jpa.JpaSystemException(connectionLost);
		when(syncFallbackSaver.saveBatchInNewTx(any(), anyList())).thenThrow(wrapped);

		assertThatThrownBy(() -> service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository))
				.isSameAs(wrapped);
	}

	@Test
	@DisplayName("Lock-wait timeout (LockAcquisitionException) is NOT recoverable — must propagate")
	void lockTimeoutPropagates() {
		PersistenceException lockTimeout = new PersistenceException(
				new org.hibernate.exception.LockAcquisitionException("could not obtain lock on row", null));
		org.springframework.orm.jpa.JpaSystemException wrapped =
				new org.springframework.orm.jpa.JpaSystemException(lockTimeout);
		when(syncFallbackSaver.saveBatchInNewTx(any(), anyList())).thenThrow(wrapped);

		assertThatThrownBy(() -> service.handlePushSync(
				TENANT, List.of(menuItem(1L, "Paneer A"), menuItem(2L, "Paneer B")), menuItemRepository))
				.isSameAs(wrapped);
	}
}
