package com.khanabook.lite.pos.data.local.dao

import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.domain.model.ServerIdMapping
import com.khanabook.lite.pos.domain.model.TopSellingItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantUserDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : UserDao {
    private val dao get() = databaseProvider.getDatabase().userDao()

    override suspend fun insertUser(user: UserEntity): Long = dao.insertUser(user)
    override suspend fun getUserByEmail(email: String): UserEntity? = dao.getUserByEmail(email)
    override suspend fun getUserByLoginId(loginId: String): UserEntity? = dao.getUserByLoginId(loginId)
    override suspend fun getUserById(id: Long): UserEntity? = dao.getUserById(id)
    override suspend fun getAnyUser(): UserEntity? = dao.getAnyUser()
    
    override suspend fun updateWhatsappNumber(userId: Long, newPhone: String, updatedAt: Long) {
        dao.updateWhatsappNumber(userId, newPhone, updatedAt)
    }

    override suspend fun updateAccountDetails(userId: Long, newEmail: String, newPhone: String, updatedAt: Long) {
        dao.updateAccountDetails(userId, newEmail, newPhone, updatedAt)
    }

    override suspend fun updateIdentityAndWhatsappNumber(
        userId: Long,
        newLoginId: String,
        newEmail: String,
        newPhone: String,
        isSynced: Boolean,
        updatedAt: Long,
        serverUpdatedAt: Long
    ) {
        dao.updateIdentityAndWhatsappNumber(userId, newLoginId, newEmail, newPhone, isSynced, updatedAt, serverUpdatedAt)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getAllUsers(restaurantId: Long): Flow<List<UserEntity>> = runFlow { it.userDao().getAllUsers(restaurantId) }

    override suspend fun setActivationStatus(userId: Long, isActive: Boolean, updatedAt: Long) {
        dao.setActivationStatus(userId, isActive, updatedAt)
    }

    override suspend fun markDeleted(userId: Long, updatedAt: Long) {
        dao.markDeleted(userId, updatedAt)
    }

    override suspend fun getUnsyncedUsers(): List<UserEntity> = dao.getUnsyncedUsers()

    override suspend fun markUsersAsSynced(ids: List<Long>) {
        dao.markUsersAsSynced(ids)
    }

    override suspend fun updateServerIdByLocalId(localId: Long, serverId: Long) {
        dao.updateServerIdByLocalId(localId, serverId)
    }

    override suspend fun insertSyncedUsers(items: List<UserEntity>) {
        dao.insertSyncedUsers(items)
    }

    override suspend fun getAllUsersOnce(): List<UserEntity> = dao.getAllUsersOnce()
}

@Singleton
class TenantRestaurantDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : RestaurantDao {
    private val dao get() = databaseProvider.getDatabase().restaurantDao()

    override suspend fun saveProfile(profile: RestaurantProfileEntity) {
        dao.saveProfile(profile)
    }

    override suspend fun getProfile(restaurantId: Long): RestaurantProfileEntity? = dao.getProfile(restaurantId)
    override suspend fun getProfile(): RestaurantProfileEntity? = dao.getProfile()
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getProfileFlow(restaurantId: Long): Flow<RestaurantProfileEntity?> = runFlow { it.restaurantDao().getProfileFlow(restaurantId) }
    override fun getProfileFlow(): Flow<RestaurantProfileEntity?> = runFlow { it.restaurantDao().getProfileFlow() }

    override suspend fun resetDailyCounter(restaurantId: Long, counter: Long, date: String, updatedAt: Long) {
        dao.resetDailyCounter(restaurantId, counter, date, updatedAt)
    }

    override suspend fun incrementOrderCounters(restaurantId: Long, updatedAt: Long) {
        dao.incrementOrderCounters(restaurantId, updatedAt)
    }

    override suspend fun incrementLifetimeCounterOnly(restaurantId: Long, updatedAt: Long) {
        dao.incrementLifetimeCounterOnly(restaurantId, updatedAt)
    }

    override suspend fun updateCounters(restaurantId: Long, daily: Long, lifetime: Long) {
        dao.updateCounters(restaurantId, daily, lifetime)
    }

    override suspend fun updateLifetimeCounter(restaurantId: Long, counter: Long, updatedAt: Long) {
        dao.updateLifetimeCounter(restaurantId, counter, updatedAt)
    }

    override suspend fun updateLogoPath(restaurantId: Long, path: String?, updatedAt: Long) {
        dao.updateLogoPath(restaurantId, path, updatedAt)
    }

    override suspend fun updateLogoUrl(restaurantId: Long, url: String?, version: Int, isSynced: Boolean, updatedAt: Long) {
        dao.updateLogoUrl(restaurantId, url, version, isSynced, updatedAt)
    }

    override suspend fun getUnsyncedRestaurantProfiles(): List<RestaurantProfileEntity> = dao.getUnsyncedRestaurantProfiles()

    override suspend fun markRestaurantProfilesAsSynced(ids: List<Long>) {
        dao.markRestaurantProfilesAsSynced(ids)
    }

    override suspend fun updateServerIdByLocalId(localId: Long, serverId: Long) {
        dao.updateServerIdByLocalId(localId, serverId)
    }

    override suspend fun insertSyncedRestaurantProfiles(items: List<RestaurantProfileEntity>) {
        dao.insertSyncedRestaurantProfiles(items)
    }
}

@Singleton
class TenantCategoryDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : CategoryDao {
    private val dao get() = databaseProvider.getDatabase().categoryDao()

    override suspend fun getAllCategoryServerIds(restaurantId: Long): List<ServerIdMapping> = dao.getAllCategoryServerIds(restaurantId)
    override suspend fun insertCategory(category: CategoryEntity): Long = dao.insertCategory(category)
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getAllCategoriesFlow(restaurantId: Long): Flow<List<CategoryEntity>> = runFlow { it.categoryDao().getAllCategoriesFlow(restaurantId) }
    override suspend fun getAllCategoriesOnce(restaurantId: Long): List<CategoryEntity> = dao.getAllCategoriesOnce(restaurantId)
    override suspend fun getCategoryById(id: Long, restaurantId: Long): CategoryEntity? = dao.getCategoryById(id, restaurantId)
    override suspend fun getCategoryByName(name: String, restaurantId: Long): CategoryEntity? = dao.getCategoryByName(name, restaurantId)
    override fun getActiveCategoriesFlow(restaurantId: Long): Flow<List<CategoryEntity>> = runFlow { it.categoryDao().getActiveCategoriesFlow(restaurantId) }

    override suspend fun toggleActive(id: Long, isActive: Boolean, restaurantId: Long) {
        dao.toggleActive(id, isActive, restaurantId)
    }

    override suspend fun markDeleted(id: Long, updatedAt: Long, restaurantId: Long) {
        dao.markDeleted(id, updatedAt, restaurantId)
    }

    override suspend fun updateCategory(category: CategoryEntity) {
        dao.updateCategory(category)
    }

    override suspend fun getUnsyncedCategories(restaurantId: Long): List<CategoryEntity> = dao.getUnsyncedCategories(restaurantId)

    override suspend fun markCategoriesAsSynced(ids: List<Long>, restaurantId: Long) {
        dao.markCategoriesAsSynced(ids, restaurantId)
    }

    override suspend fun updateServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun insertSyncedCategories(items: List<CategoryEntity>) {
        dao.insertSyncedCategories(items)
    }
}

@Singleton
class TenantMenuDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : MenuDao {
    private val dao get() = databaseProvider.getDatabase().menuDao()

    override suspend fun getAllMenuItemServerIds(restaurantId: Long): List<ServerIdMapping> = dao.getAllMenuItemServerIds(restaurantId)
    override suspend fun getAllVariantServerIds(restaurantId: Long): List<ServerIdMapping> = dao.getAllVariantServerIds(restaurantId)
    override suspend fun insertItem(item: MenuItemEntity): Long = dao.insertItem(item)
    override suspend fun updateItem(item: MenuItemEntity) = dao.updateItem(item)
    override suspend fun getItemById(id: Long, restaurantId: Long): MenuItemEntity? = dao.getItemById(id, restaurantId)
    override suspend fun getItemByName(name: String, restaurantId: Long): MenuItemEntity? = dao.getItemByName(name, restaurantId)
    override suspend fun getItemByBarcode(barcode: String, restaurantId: Long): MenuItemEntity? = dao.getItemByBarcode(barcode, restaurantId)
    override suspend fun getAllMenuItemsOnce(restaurantId: Long): List<MenuItemEntity> = dao.getAllMenuItemsOnce(restaurantId)
    override suspend fun getAllVariantsOnce(restaurantId: Long): List<ItemVariantEntity> = dao.getAllVariantsOnce(restaurantId)
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getAllItemsFlow(restaurantId: Long): Flow<List<MenuItemEntity>> = runFlow { it.menuDao().getAllItemsFlow(restaurantId) }
    override fun getItemsByCategoryFlow(categoryId: Long, restaurantId: Long): Flow<List<MenuItemEntity>> = runFlow { it.menuDao().getItemsByCategoryFlow(categoryId, restaurantId) }
    override suspend fun getItemsByCategoryOnce(categoryId: Long, restaurantId: Long): List<MenuItemEntity> = dao.getItemsByCategoryOnce(categoryId, restaurantId)
    override fun searchItems(query: String, restaurantId: Long): Flow<List<MenuItemEntity>> = runFlow { it.menuDao().searchItems(query, restaurantId) }

    override suspend fun toggleItemAvailability(id: Long, isAvailable: Boolean, restaurantId: Long) {
        dao.toggleItemAvailability(id, isAvailable, restaurantId)
    }

    override suspend fun updateStock(id: Long, delta: Double, restaurantId: Long) {
        dao.updateStock(id, delta, restaurantId)
    }

    override suspend fun updateLowStockThreshold(id: Long, threshold: Double) {
        dao.updateLowStockThreshold(id, threshold)
    }

    override suspend fun markItemDeleted(id: Long, updatedAt: Long, restaurantId: Long) {
        dao.markItemDeleted(id, updatedAt, restaurantId)
    }

    override suspend fun markItemsDeletedByCategory(categoryId: Long, updatedAt: Long, restaurantId: Long) {
        dao.markItemsDeletedByCategory(categoryId, updatedAt, restaurantId)
    }

    override suspend fun getItemIdsByCategory(categoryId: Long, restaurantId: Long): List<Long> = dao.getItemIdsByCategory(categoryId, restaurantId)
    override suspend fun insertVariant(variant: ItemVariantEntity): Long = dao.insertVariant(variant)
    override suspend fun updateVariant(variant: ItemVariantEntity) = dao.updateVariant(variant)
    override suspend fun getVariantById(id: Long, restaurantId: Long): ItemVariantEntity? = dao.getVariantById(id, restaurantId)

    override suspend fun updateVariantStock(id: Long, delta: Double, restaurantId: Long) {
        dao.updateVariantStock(id, delta, restaurantId)
    }

    override suspend fun updateVariantLowStockThreshold(id: Long, threshold: Double) {
        dao.updateVariantLowStockThreshold(id, threshold)
    }

    override suspend fun markVariantDeleted(id: Long, updatedAt: Long, restaurantId: Long) {
        dao.markVariantDeleted(id, updatedAt, restaurantId)
    }

    override suspend fun markVariantsDeletedByItem(itemId: Long, updatedAt: Long, restaurantId: Long) {
        dao.markVariantsDeletedByItem(itemId, updatedAt, restaurantId)
    }

    override fun getVariantsForItemFlow(itemId: Long, restaurantId: Long): Flow<List<ItemVariantEntity>> = runFlow { it.menuDao().getVariantsForItemFlow(itemId, restaurantId) }
    override fun getMenuWithVariantsByCategoryFlow(categoryId: Long, restaurantId: Long): Flow<List<MenuWithVariants>> = runFlow { it.menuDao().getMenuWithVariantsByCategoryFlow(categoryId, restaurantId) }
    override fun searchMenuWithVariants(query: String, restaurantId: Long): Flow<List<MenuWithVariants>> = runFlow { it.menuDao().searchMenuWithVariants(query, restaurantId) }
    override suspend fun getUnsyncedMenuItems(restaurantId: Long): List<MenuItemEntity> = dao.getUnsyncedMenuItems(restaurantId)

    override suspend fun markMenuItemsAsSynced(ids: List<Long>, restaurantId: Long) {
        dao.markMenuItemsAsSynced(ids, restaurantId)
    }

    override suspend fun updateMenuItemServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateMenuItemServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun insertSyncedMenuItems(items: List<MenuItemEntity>) {
        dao.insertSyncedMenuItems(items)
    }

    override suspend fun getUnsyncedItemVariants(restaurantId: Long): List<ItemVariantEntity> = dao.getUnsyncedItemVariants(restaurantId)

    override suspend fun markItemVariantsAsSynced(ids: List<Long>, restaurantId: Long) {
        dao.markItemVariantsAsSynced(ids, restaurantId)
    }

    override suspend fun updateVariantServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateVariantServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun insertSyncedItemVariants(items: List<ItemVariantEntity>) {
        dao.insertSyncedItemVariants(items)
    }
}

@Singleton
class TenantPrinterProfileDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : PrinterProfileDao {
    private val dao get() = databaseProvider.getDatabase().printerProfileDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getAllFlow(restaurantId: Long): Flow<List<PrinterProfileEntity>> = runFlow { it.printerProfileDao().getAllFlow(restaurantId) }
    override suspend fun getAll(restaurantId: Long): List<PrinterProfileEntity> = dao.getAll(restaurantId)
    override suspend fun getByRole(role: String, restaurantId: Long): PrinterProfileEntity? = dao.getByRole(role, restaurantId)

    override suspend fun upsert(profile: PrinterProfileEntity) {
        dao.upsert(profile)
    }

    override suspend fun deleteByRole(role: String, restaurantId: Long) {
        dao.deleteByRole(role, restaurantId)
    }
}

@Singleton
class TenantKitchenPrintQueueDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : KitchenPrintQueueDao {
    private val dao get() = databaseProvider.getDatabase().kitchenPrintQueueDao()

    override suspend fun upsert(job: KitchenPrintQueueEntity): Long = dao.upsert(job)
    override suspend fun getPendingForPrinter(printerMac: String, restaurantId: Long): List<KitchenPrintQueueEntity> = dao.getPendingForPrinter(printerMac, restaurantId)
    override suspend fun getByBillAndPrinter(billId: Long, printerMac: String): KitchenPrintQueueEntity? = dao.getByBillAndPrinter(billId, printerMac)
    override suspend fun getById(id: Long): KitchenPrintQueueEntity? = dao.getById(id)
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getPendingCountFlow(restaurantId: Long): Flow<Int> = runFlow { it.kitchenPrintQueueDao().getPendingCountFlow(restaurantId) }
    override suspend fun getPendingCountForBill(billId: Long): Int = dao.getPendingCountForBill(billId)

    override suspend fun markRetryingIfPending(id: Long, attemptedAt: Long): Int = dao.markRetryingIfPending(id, attemptedAt)
    override suspend fun markRetryingIfPending(billId: Long, printerMac: String, attemptedAt: Long): Int = dao.markRetryingIfPending(billId, printerMac, attemptedAt)

    override suspend fun markPending(id: Long, error: String?, updatedAt: Long) {
        dao.markPending(id, error, updatedAt)
    }

    override suspend fun markSent(id: Long, updatedAt: Long) {
        dao.markSent(id, updatedAt)
    }

    override suspend fun markSent(billId: Long, printerMac: String, updatedAt: Long) {
        dao.markSent(billId, printerMac, updatedAt)
    }

    override suspend fun deleteByBillAndPrinter(billId: Long, printerMac: String) {
        dao.deleteByBillAndPrinter(billId, printerMac)
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteByBillId(billId: Long) {
        dao.deleteByBillId(billId)
    }

    override suspend fun getBillIdsWithPendingKds(restaurantId: Long): List<Long> = dao.getBillIdsWithPendingKds(restaurantId)
}

@Singleton
class TenantBillDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : BillDao {
    private val dao get() = databaseProvider.getDatabase().billDao()

    override suspend fun getAllBillServerIds(restaurantId: Long): List<ServerIdMapping> = dao.getAllBillServerIds(restaurantId)
    override suspend fun getBillByServerId(serverId: Long): BillEntity? = dao.getBillByServerId(serverId)
    override suspend fun getBillByLocalId(localId: Long, deviceId: String, restaurantId: Long): BillEntity? = dao.getBillByLocalId(localId, deviceId, restaurantId)
    override suspend fun insertBill(bill: BillEntity): Long = dao.insertBill(bill)
    override suspend fun updateBill(bill: BillEntity) = dao.updateBill(bill)
    override suspend fun insertBillItems(items: List<BillItemEntity>) = dao.insertBillItems(items)
    override suspend fun insertBillPayments(payments: List<BillPaymentEntity>) = dao.insertBillPayments(payments)
    override suspend fun insertBillPayment(payment: BillPaymentEntity) = dao.insertBillPayment(payment)
    override suspend fun getBillById(id: Long, restaurantId: Long): BillEntity? = dao.getBillById(id, restaurantId)
    override suspend fun getBillByLifetimeId(id: Long, restaurantId: Long): BillEntity? = dao.getBillByLifetimeId(id, restaurantId)
    override suspend fun getBillByDailyIdAndDate(displayId: String, startTime: Long, endTime: Long, restaurantId: Long): BillEntity? = dao.getBillByDailyIdAndDate(displayId, startTime, endTime, restaurantId)
    override suspend fun getBillByDailyIntIdAndDate(dailyId: Long, startTime: Long, endTime: Long, restaurantId: Long): BillEntity? = dao.getBillByDailyIntIdAndDate(dailyId, startTime, endTime, restaurantId)
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getDraftBills(restaurantId: Long): Flow<List<BillEntity>> = runFlow { it.billDao().getDraftBills(restaurantId) }
    override suspend fun getLatestPendingOnlineBill(restaurantId: Long, ownerUserId: Long): BillEntity? = dao.getLatestPendingOnlineBill(restaurantId, ownerUserId)
    override suspend fun getLatestPendingOnlineBill(restaurantId: Long): BillEntity? = dao.getLatestPendingOnlineBill(restaurantId)

    override suspend fun updateOrderStatus(id: Long, status: String, restaurantId: Long) {
        dao.updateOrderStatus(id, status, restaurantId)
    }

    override suspend fun updatePaymentMode(id: Long, mode: String, restaurantId: Long) {
        dao.updatePaymentMode(id, mode, restaurantId)
    }

    override suspend fun updatePaymentStatus(id: Long, status: String, restaurantId: Long) {
        dao.updatePaymentStatus(id, status, restaurantId)
    }

    override suspend fun cancelBill(id: Long, reason: String, updatedAt: Long, restaurantId: Long) {
        dao.cancelBill(id, reason, updatedAt, restaurantId)
    }

    override suspend fun cancelStalePendingOnlineDrafts(reason: String, updatedAt: Long, restaurantId: Long): Int = dao.cancelStalePendingOnlineDrafts(reason, updatedAt, restaurantId)
    override fun getBillsByDateRange(startMillis: Long, endMillis: Long, restaurantId: Long): Flow<List<BillEntity>> = runFlow { it.billDao().getBillsByDateRange(startMillis, endMillis, restaurantId) }
    override suspend fun getBillWithItemsById(id: Long, restaurantId: Long): BillWithItems? = dao.getBillWithItemsById(id, restaurantId)
    override suspend fun getBillWithItemsByLifetimeId(id: Long, restaurantId: Long): BillWithItems? = dao.getBillWithItemsByLifetimeId(id, restaurantId)

    override suspend fun insertFullBill(bill: BillEntity, items: List<BillItemEntity>, payments: List<BillPaymentEntity>) {
        dao.insertFullBill(bill, items, payments)
    }

    override suspend fun reconcileServerAcknowledgedBills(restaurantId: Long): Int = dao.reconcileServerAcknowledgedBills(restaurantId)
    override suspend fun markBillsSyncedByLifetimeIds(deviceId: String, restaurantId: Long, lifetimeOrderIds: List<Long>): Int = dao.markBillsSyncedByLifetimeIds(deviceId, restaurantId, lifetimeOrderIds)

    override suspend fun markBillsSyncedByDeviceIdAndLocalIds(deviceId: String, restaurantId: Long, localIds: List<Long>): Int = dao.markBillsSyncedByDeviceIdAndLocalIds(deviceId, restaurantId, localIds)
    override suspend fun getUnsyncedBills(restaurantId: Long): List<BillEntity> = dao.getUnsyncedBills(restaurantId)
    override suspend fun getUnsyncedBillsForUser(userId: Long, restaurantId: Long): List<BillEntity> = dao.getUnsyncedBillsForUser(userId, restaurantId)
    override suspend fun getUnsyncedBillItemsForUser(userId: Long, restaurantId: Long): List<BillItemEntity> = dao.getUnsyncedBillItemsForUser(userId, restaurantId)
    override suspend fun getUnsyncedBillPaymentsForUser(userId: Long, restaurantId: Long): List<BillPaymentEntity> = dao.getUnsyncedBillPaymentsForUser(userId, restaurantId)
    override fun getUnsyncedCountForUser(userId: Long, restaurantId: Long): Flow<Int> = runFlow { it.billDao().getUnsyncedCountForUser(userId, restaurantId) }

    override suspend fun markBillsAsSynced(billIds: List<Long>, restaurantId: Long) {
        dao.markBillsAsSynced(billIds, restaurantId)
    }

    override suspend fun updateServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun insertSyncedBills(bills: List<BillEntity>) {
        dao.insertSyncedBills(bills)
    }

    override suspend fun getUnsyncedCountOnce(restaurantId: Long): Int = dao.getUnsyncedCountOnce(restaurantId)
    override fun getUnsyncedCount(restaurantId: Long): Flow<Int> = runFlow { it.billDao().getUnsyncedCount(restaurantId) }
    override suspend fun getUnsyncedBillItems(restaurantId: Long): List<BillItemEntity> = dao.getUnsyncedBillItems(restaurantId)
    override suspend fun getUnsyncedBillItemsWithSyncedParent(restaurantId: Long): List<BillItemEntity> = dao.getUnsyncedBillItemsWithSyncedParent(restaurantId)

    override suspend fun markBillItemsAsSynced(ids: List<Long>, restaurantId: Long) {
        dao.markBillItemsAsSynced(ids, restaurantId)
    }

    override suspend fun updateBillItemServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateBillItemServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun insertSyncedBillItems(items: List<BillItemEntity>) {
        dao.insertSyncedBillItems(items)
    }

    override suspend fun deleteAllSyncedBillItems(restaurantId: Long) {
        dao.deleteAllSyncedBillItems(restaurantId)
    }

    override suspend fun getUnsyncedBillPayments(restaurantId: Long): List<BillPaymentEntity> = dao.getUnsyncedBillPayments(restaurantId)
    override suspend fun getUnsyncedBillPaymentsWithSyncedParent(restaurantId: Long): List<BillPaymentEntity> = dao.getUnsyncedBillPaymentsWithSyncedParent(restaurantId)

    override suspend fun markBillPaymentsAsSynced(ids: List<Long>, restaurantId: Long) {
        dao.markBillPaymentsAsSynced(ids, restaurantId)
    }

    override suspend fun updateBillPaymentServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateBillPaymentServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int, restaurantId: Long): List<TopSellingItem> = dao.getTopSellingItemsInRange(startMillis, endMillis, limit, restaurantId)

    override suspend fun insertSyncedBillPayments(payments: List<BillPaymentEntity>) {
        dao.insertSyncedBillPayments(payments)
    }

    override suspend fun deleteAllSyncedBillPayments(restaurantId: Long) {
        dao.deleteAllSyncedBillPayments(restaurantId)
    }

    override suspend fun getRecentBillsWithCustomers(restaurantId: Long): List<BillEntity> = dao.getRecentBillsWithCustomers(restaurantId)
    override suspend fun getBillByLifetimeNo(lifetimeNo: Long, restaurantId: Long): BillEntity? = dao.getBillByLifetimeNo(lifetimeNo, restaurantId)
    override suspend fun getBillsWithPendingKds(restaurantId: Long): List<BillEntity> = dao.getBillsWithPendingKds(restaurantId)
}

@Singleton
class TenantInventoryDao @Inject constructor(
    private val databaseProvider: DatabaseProvider
) : InventoryDao {
    private val dao get() = databaseProvider.getDatabase().inventoryDao()

    override suspend fun insertStockLog(log: StockLogEntity) {
        dao.insertStockLog(log)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runFlow(block: (AppDatabase) -> Flow<T>): Flow<T> {
        return databaseProvider.activeDatabaseFlow.flatMapLatest { block(it) }
    }

    override fun getLogsForItem(itemId: Long, restaurantId: Long): Flow<List<StockLogEntity>> = runFlow { it.inventoryDao().getLogsForItem(itemId, restaurantId) }
    override fun getAllLogs(restaurantId: Long): Flow<List<StockLogEntity>> = runFlow { it.inventoryDao().getAllLogs(restaurantId) }
    override suspend fun getUnsyncedStockLogs(restaurantId: Long): List<StockLogEntity> = dao.getUnsyncedStockLogs(restaurantId)

    override suspend fun markStockLogsAsSynced(ids: List<Long>, restaurantId: Long) {
        dao.markStockLogsAsSynced(ids, restaurantId)
    }

    override suspend fun updateServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long) {
        dao.updateServerIdByLocalId(localId, serverId, restaurantId)
    }

    override suspend fun insertSyncedStockLogs(items: List<StockLogEntity>) {
        dao.insertSyncedStockLogs(items)
    }

    override suspend fun deleteAllSyncedStockLogs(restaurantId: Long) {
        dao.deleteAllSyncedStockLogs(restaurantId)
    }
}
