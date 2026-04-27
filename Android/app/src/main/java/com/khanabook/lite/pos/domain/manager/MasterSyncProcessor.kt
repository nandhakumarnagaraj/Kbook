package com.khanabook.lite.pos.domain.manager

import android.util.Log
import androidx.room.withTransaction
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.data.remote.dto.*
import com.khanabook.lite.pos.domain.util.SyncConflictException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class MasterSyncProcessor @Inject constructor(
    private val api: KhanaBookApi,
    private val database: AppDatabase,
    private val billDao: BillDao,
    private val restaurantDao: RestaurantDao,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val menuDao: MenuDao,
    private val inventoryDao: InventoryDao,
    private val printerProfileDao: PrinterProfileDao
) {
    private fun normalizeUserRole(role: String?): String {
        return when (role) {
            "OWNER", "KBOOK_ADMIN" -> role
            else -> "OWNER"
        }
    }


    private val tag = "MasterSyncProcessor"

    // INFO logs are verbose; suppress in release to reduce logcat noise.
    // WARN / ERROR always surface — production sync failures must be visible.
    private fun logInfo(msg: String) { if (BuildConfig.DEBUG) Log.i(tag, msg) }
    private fun logWarn(msg: String, t: Throwable? = null) { if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg) }
    private fun logError(msg: String, t: Throwable? = null) { if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg) }

    /**
     * Pushes [Entity] records to the server as [Dto] payloads.
     *
     * [T] = Room entity type (source of truth for local state)
     * [R] = Network DTO type (stable API contract, decoupled from Room schema)
     *
     * The [transform] lambda converts each entity to its DTO immediately before
     * the batch is sent — no entity objects ever reach Retrofit/Moshi directly.
     */
    private suspend fun <T, R> pushBatches(
        label: String,
        records: List<T>,
        transform: (T) -> R,
        push: suspend (List<R>) -> PushSyncResponse,
        markSynced: suspend (List<Long>) -> Unit
    ) {
        if (records.isEmpty()) return

        val batches = records.chunked(50)
        logInfo("Pushing ${records.size} $label record(s) in ${batches.size} batch(es)")

        batches.forEachIndexed { index, batch ->
            try {
                val response = push(batch.map(transform))
                markSynced(response.successfulLocalIds)

                logInfo(
                    "Pushed $label batch ${index + 1}/${batches.size}: success=${response.successfulLocalIds.size}, failed=${response.failedLocalIds.size}"
                )

                if (response.failedLocalIds.isNotEmpty()) {
                    logWarn(
                        "Server rejected $label localIds=${response.failedLocalIds.joinToString(",")}"
                    )
                }
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 409) {
                    logWarn(
                        "Conflict while pushing $label batch ${index + 1}/${batches.size}"
                    )
                    throw SyncConflictException(e)
                }
                logError(
                    "Failed pushing $label batch ${index + 1}/${batches.size}",
                    e
                )
                throw e
            }
        }
    }

    private fun String?.orFallback(default: String): String = this?.takeUnless { it.isBlank() } ?: default

    private fun <T> logSkippedRecords(label: String, skipped: List<T>, describe: (T) -> String) {
        if (skipped.isEmpty()) return
        val preview = skipped.take(5).joinToString("; ") { describe(it) }
        Log.w(
            "MasterSyncProcessor",
            "Skipping ${skipped.size} orphaned $label record(s) during pull: $preview"
        )
    }

    private fun logRepairedRecords(label: String, repaired: List<String>) {
        if (repaired.isEmpty()) return
        val preview = repaired.take(5).joinToString("; ")
        Log.w(
            "MasterSyncProcessor",
            "Repairing ${repaired.size} $label record(s) during pull: $preview"
        )
    }

    private fun UserEntity.identityKeys(): List<String> {
        return buildList {
            serverId?.let { add("server:$it") }
            loginId?.takeIf { it.isNotBlank() }?.let { add("login:${it.lowercase()}") }
            email.takeIf { it.isNotBlank() }?.let { add("email:${it.lowercase()}") }
            googleEmail?.takeIf { it.isNotBlank() }?.let { add("google:${it.lowercase()}") }
            whatsappNumber?.takeIf { it.isNotBlank() }?.let { add("whatsapp:$it") }
        }
    }
    
    private fun Double?.toSafeString(): String {
        if (this == null) return "0.00"
        return java.math.BigDecimal.valueOf(this)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString()
    }

    private fun java.math.BigDecimal?.toSafeString(): String {
        if (this == null) return "0.00"
        return this
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString()
    }

    private fun String?.toSafeAmount(): String {
        if (this.isNullOrBlank()) return "0.00"
        return try {
            // Use setScale(2) WITHOUT stripTrailingZeros so "10.00" stays "10.00"
            // and matches server's BigDecimal serialization — avoids repeated sync loops.
            java.math.BigDecimal(this)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString()
        } catch (e: Exception) {
            "0.00"
        }
    }

    suspend fun pushAll(): Boolean {
        // Guard: never push data with an invalid tenant
        val profile = restaurantDao.getProfile()
        val restaurantId = profile?.restaurantId ?: 0L
        if (restaurantId <= 0L) {
            Log.w("MasterSyncProcessor", "Push aborted: restaurantId not set (value=$restaurantId)")
            return false
        }

        // Embed kitchen printer into restaurant profile before pushing.
        syncKitchenPrinterIntoProfile(profile)

        pushBatches(
            label = "restaurant profiles",
            records = restaurantDao.getUnsyncedRestaurantProfiles(),
            transform = RestaurantProfileEntity::toSyncDto,
            push = api::pushRestaurantProfiles,
            markSynced = restaurantDao::markRestaurantProfilesAsSynced
        )

        pushBatches(
            label = "users",
            records = userDao.getUnsyncedUsers(),
            transform = UserEntity::toSyncDto,
            push = api::pushUsers,
            markSynced = userDao::markUsersAsSynced
        )

        pushBatches(
            label = "categories",
            records = categoryDao.getUnsyncedCategories(),
            transform = CategoryEntity::toSyncDto,
            push = api::pushCategories,
            markSynced = categoryDao::markCategoriesAsSynced
        )

        pushBatches(
            label = "menu items",
            records = menuDao.getUnsyncedMenuItems(),
            transform = MenuItemEntity::toSyncDto,
            push = api::pushMenuItems,
            markSynced = menuDao::markMenuItemsAsSynced
        )

        pushBatches(
            label = "item variants",
            records = menuDao.getUnsyncedItemVariants(),
            transform = ItemVariantEntity::toSyncDto,
            push = api::pushItemVariants,
            markSynced = menuDao::markItemVariantsAsSynced
        )

        pushBatches(
            label = "stock logs",
            records = inventoryDao.getUnsyncedStockLogs(),
            transform = StockLogEntity::toSyncDto,
            push = api::pushStockLogs,
            markSynced = inventoryDao::markStockLogsAsSynced
        )

        val unsyncedBills = billDao.getUnsyncedBills()
        val validBills = unsyncedBills.filter { it.restaurantId == restaurantId }
        val skippedBills = unsyncedBills.size - validBills.size
        if (skippedBills > 0) {
            Log.w("MasterSyncProcessor", "Skipping $skippedBills bill(s) with mismatched restaurantId (expected=$restaurantId)")
        }
        pushBatches(
            label = "bills",
            records = validBills,
            transform = BillEntity::toSyncDto,
            push = api::pushBills,
            markSynced = billDao::markBillsAsSynced
        )

        pushBatches(
            label = "bill items",
            records = billDao.getUnsyncedBillItems().filter { it.restaurantId == restaurantId },
            transform = BillItemEntity::toSyncDto,
            push = api::pushBillItems,
            markSynced = billDao::markBillItemsAsSynced
        )

        val unsyncedBillPayments = billDao.getUnsyncedBillPayments().filter { it.restaurantId == restaurantId }
        val gatewayOwnedBillPayments = unsyncedBillPayments.filter { payment ->
            !payment.gatewayTxnId.isNullOrBlank() && payment.verifiedBy.equals("easebuzz", ignoreCase = true)
        }
        if (gatewayOwnedBillPayments.isNotEmpty()) {
            Log.i(
                "MasterSyncProcessor",
                "Marking ${gatewayOwnedBillPayments.size} gateway-owned bill payment row(s) as synced; backend payments are authoritative"
            )
            billDao.markBillPaymentsAsSynced(gatewayOwnedBillPayments.map { it.id })
        }
        pushBatches(
            label = "bill payments",
            records = unsyncedBillPayments.filterNot { payment ->
                !payment.gatewayTxnId.isNullOrBlank() && payment.verifiedBy.equals("easebuzz", ignoreCase = true)
            },
            transform = BillPaymentEntity::toSyncDto,
            push = api::pushBillPayments,
            markSynced = billDao::markBillPaymentsAsSynced
        )

        return true
    }

    suspend fun insertMasterData(masterData: MasterSyncResponse) = database.withTransaction {
        if (masterData.profiles.isNotEmpty()) {
            val currentLocalProfile = restaurantDao.getProfile()
            restaurantDao.insertSyncedRestaurantProfiles(
                masterData.profiles.map { remoteProfile ->
                    RestaurantProfileEntity(
                        id = currentLocalProfile?.id ?: 1,
                        shopName = remoteProfile.shopName.orFallback("My Shop"),
                        shopAddress = remoteProfile.shopAddress.orFallback(""),
                        whatsappNumber = remoteProfile.whatsappNumber.orFallback(""),
                        email = remoteProfile.email.orFallback(""),
                        logoPath = remoteProfile.logoPath, 
                        fssaiNumber = remoteProfile.fssaiNumber.orFallback(""),
                        emailInvoiceConsent = remoteProfile.emailInvoiceConsent ?: false,
                        country = remoteProfile.country.orFallback(currentLocalProfile?.country ?: "India"),
                        gstEnabled = remoteProfile.gstEnabled ?: false,
                        gstin = remoteProfile.gstin.orFallback(""),
                        isTaxInclusive = remoteProfile.isTaxInclusive ?: false,
                        gstPercentage = remoteProfile.gstPercentage ?: 0.0,
                        customTaxName = remoteProfile.customTaxName.orFallback(""),
                        customTaxNumber = remoteProfile.customTaxNumber.orFallback(""),
                        customTaxPercentage = remoteProfile.customTaxPercentage ?: 0.0,
                        currency = remoteProfile.currency.orFallback(currentLocalProfile?.currency ?: "INR"),
                        upiEnabled = remoteProfile.upiEnabled ?: false,
                        upiQrPath = remoteProfile.upiQrPath,
                        upiHandle = remoteProfile.upiHandle.orFallback(""),
                        upiMobile = remoteProfile.upiMobile.orFallback(""),
                        cashEnabled = remoteProfile.cashEnabled ?: true,
                        posEnabled = remoteProfile.posEnabled ?: false,
                        zomatoEnabled = remoteProfile.zomatoEnabled ?: false,
                        swiggyEnabled = remoteProfile.swiggyEnabled ?: false,
                        ownWebsiteEnabled = remoteProfile.ownWebsiteEnabled ?: false,
                        easebuzzEnabled = currentLocalProfile?.easebuzzEnabled ?: false,
                        easebuzzMerchantKey = currentLocalProfile?.easebuzzMerchantKey,
                        easebuzzSalt = currentLocalProfile?.easebuzzSalt,
                        easebuzzEnv = currentLocalProfile?.easebuzzEnv ?: "test",
                        printerEnabled = remoteProfile.printerEnabled ?: false,
                        printerName = remoteProfile.printerName.orFallback(""),
                        printerMac = remoteProfile.printerMac.orFallback(""),
                        paperSize = remoteProfile.paperSize.orFallback(currentLocalProfile?.paperSize ?: "58mm"),
                        autoPrintOnSuccess = remoteProfile.autoPrintOnSuccess ?: false,
                        includeLogoInPrint = remoteProfile.includeLogoInPrint ?: true,
                        printCustomerWhatsapp = remoteProfile.printCustomerWhatsapp ?: true,
                        dailyOrderCounter = remoteProfile.dailyOrderCounter ?: 0L,
                        lifetimeOrderCounter = remoteProfile.lifetimeOrderCounter ?: 0L,
                        lastResetDate = remoteProfile.lastResetDate.orFallback(""),
                        sessionTimeoutMinutes = remoteProfile.sessionTimeoutMinutes ?: 30,
                        restaurantId = remoteProfile.restaurantId ?: 0L,
                        deviceId = remoteProfile.deviceId.orFallback("unknown_device"),
                        isSynced = true,
                        updatedAt = remoteProfile.updatedAt,
                        timezone = remoteProfile.timezone ?: "Asia/Kolkata",
                        reviewUrl = remoteProfile.reviewUrl,
                        isDeleted = remoteProfile.isDeleted ?: false,
                        showBranding = remoteProfile.showBranding ?: true,
                        maskCustomerPhone = remoteProfile.maskCustomerPhone ?: true,
                        serverId = remoteProfile.serverId,
                        serverUpdatedAt = remoteProfile.serverUpdatedAt ?: 0L,
                        kitchenPrinterEnabled = remoteProfile.kitchenPrinterEnabled,
                        kitchenPrinterName = remoteProfile.kitchenPrinterName,
                        kitchenPrinterMac = remoteProfile.kitchenPrinterMac,
                        kitchenPrinterPaperSize = remoteProfile.kitchenPrinterPaperSize ?: "58mm"
                    )
                }
            )

            // Restore kitchen PrinterProfileEntity from server data so the kitchen printer
            // config is available immediately after first login on a new/reinstalled device.
            masterData.profiles.firstOrNull()?.let { remoteProfile ->
                val mac = remoteProfile.kitchenPrinterMac
                if (!mac.isNullOrBlank()) {
                    val existing = printerProfileDao.getByRole(com.khanabook.lite.pos.domain.model.PrinterRole.KITCHEN.name)
                    printerProfileDao.upsert(
                        PrinterProfileEntity(
                            id = existing?.id ?: 0,
                            role = com.khanabook.lite.pos.domain.model.PrinterRole.KITCHEN.name,
                            name = remoteProfile.kitchenPrinterName ?: "Kitchen Printer",
                            macAddress = mac,
                            enabled = remoteProfile.kitchenPrinterEnabled ?: true,
                            autoPrint = true,
                            paperSize = remoteProfile.kitchenPrinterPaperSize ?: "58mm",
                            includeLogo = false
                        )
                    )
                }
            }
        }

        // Maps each remote user's original local id → the local id we actually stored.
        // Bills reference users via the original device's local id (createdBy), so we need
        // this to remap the FK to whatever id we assigned on this device.
        val remoteUserIdToLocalId = mutableMapOf<Long, Long>()

        if (masterData.users.isNotEmpty()) {
            val localUsers = userDao.getAllUsersOnce()
            val localUsersByIdentity = mutableMapOf<String, UserEntity>()
            localUsers.forEach { localUser ->
                localUser.identityKeys().forEach { key ->
                    localUsersByIdentity.putIfAbsent(key, localUser)
                }
            }
            val usersToInsert = masterData.users.map { remoteUser ->
                val localUser = listOfNotNull(
                    remoteUser.serverId?.let { localUsersByIdentity["server:$it"] },
                    remoteUser.loginId?.takeIf { it.isNotBlank() }?.let { localUsersByIdentity["login:${it.lowercase()}"] },
                    remoteUser.email?.takeIf { it.isNotBlank() }?.let { localUsersByIdentity["email:${it.lowercase()}"] },
                    remoteUser.googleEmail?.takeIf { it.isNotBlank() }?.let { localUsersByIdentity["google:${it.lowercase()}"] },
                    remoteUser.whatsappNumber?.takeIf { it.isNotBlank() }?.let { localUsersByIdentity["whatsapp:$it"] }
                ).firstOrNull()
                val remoteIdentity = remoteUser.loginId?.takeIf { it.isNotBlank() } ?: remoteUser.email
                val assignedLocalId = localUser?.id ?: remoteUser.id
                // Track: original device local id → id we will persist on this device
                remoteUserIdToLocalId[remoteUser.id] = assignedLocalId

                UserEntity(
                    id = assignedLocalId,
                    name = remoteUser.name.orFallback("User"),
                    email = remoteUser.email.orFallback(localUser?.email ?: remoteIdentity.orFallback("")),
                    loginId = remoteUser.loginId ?: localUser?.loginId ?: remoteUser.email,
                    phoneNumber = remoteUser.phoneNumber ?: localUser?.phoneNumber,
                    googleEmail = remoteUser.googleEmail ?: localUser?.googleEmail,
                    authProvider = remoteUser.authProvider ?: "PHONE",
                    whatsappNumber = remoteUser.whatsappNumber ?: localUser?.whatsappNumber ?: "",
                    role = normalizeUserRole(remoteUser.role ?: localUser?.role),
                    isActive = remoteUser.isActive ?: true,
                    tokenInvalidatedAt = remoteUser.tokenInvalidatedAt ?: localUser?.tokenInvalidatedAt,
                    createdAt = localUser?.createdAt ?: remoteUser.createdAt ?: System.currentTimeMillis(),
                    restaurantId = remoteUser.restaurantId ?: 0L,
                    deviceId = localUser?.deviceId ?: remoteUser.deviceId.orFallback(""),
                    isSynced = true,
                    updatedAt = remoteUser.updatedAt,
                    isDeleted = remoteUser.isDeleted ?: false,
                    serverId = remoteUser.serverId,
                    serverUpdatedAt = remoteUser.serverUpdatedAt ?: 0L
                )
            }
            userDao.insertSyncedUsers(usersToInsert)
        }

        val knownUserIds = userDao.getAllUsersOnce().map { it.id }.toSet()

        if (masterData.categories.isNotEmpty()) {
            categoryDao.insertSyncedCategories(
                masterData.categories.map { remoteCategory ->
                    CategoryEntity(
                        id = remoteCategory.id,
                        name = remoteCategory.name.orFallback("Category"),
                        isVeg = remoteCategory.isVeg,
                        sortOrder = remoteCategory.sortOrder ?: 0,
                        createdAt = remoteCategory.createdAt ?: System.currentTimeMillis(),
                        restaurantId = remoteCategory.restaurantId ?: 0L,
                        deviceId = remoteCategory.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteCategory.updatedAt,
                        isDeleted = remoteCategory.isDeleted ?: false,
                        serverId = remoteCategory.serverId,
                        serverUpdatedAt = remoteCategory.serverUpdatedAt ?: 0L
                    )
                }
            )
        }

        // Fetch category ID mapping
        val categoryIdMap = categoryDao.getAllCategoryServerIds().associate { it.serverId to it.id }
        val knownCategoryIds = categoryIdMap.values.toMutableSet()

        if (masterData.menuItems.isNotEmpty()) {
            val resolvedMenuItems = masterData.menuItems.mapNotNull { remoteMenuItem ->
                    // categoryIdMap keys are server IDs (Long). Only look up via serverCategoryId.
                    // Falling back to categoryId (a foreign device's local ID) would look up the
                    // wrong key and silently link the item to a wrong or non-existent category.
                    val localCategoryId = remoteMenuItem.serverCategoryId?.let { serverId ->
                        categoryIdMap[serverId] ?: serverId
                    } ?: remoteMenuItem.categoryId

                    if (localCategoryId !in knownCategoryIds) {
                        null
                    } else {
                        MenuItemEntity(
                        id = remoteMenuItem.localId ?: remoteMenuItem.serverId ?: 0L,
                        categoryId = localCategoryId ?: 0L,
                        name = remoteMenuItem.name.orFallback("Unnamed Item"),
                        basePrice = remoteMenuItem.basePrice.toSafeString(),
                        foodType = remoteMenuItem.foodType.orFallback("veg"),
                        description = remoteMenuItem.description,
                        isAvailable = remoteMenuItem.isAvailable ?: true,
                        currentStock = remoteMenuItem.currentStock.toSafeString(),
                        lowStockThreshold = remoteMenuItem.lowStockThreshold.toSafeString(),
                        barcode = remoteMenuItem.barcode,
                        createdAt = remoteMenuItem.createdAt ?: System.currentTimeMillis(),
                        restaurantId = remoteMenuItem.restaurantId ?: 0L,
                        deviceId = remoteMenuItem.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteMenuItem.updatedAt ?: System.currentTimeMillis(),
                        isDeleted = remoteMenuItem.isDeleted ?: false,
                        serverId = remoteMenuItem.serverId,
                        serverUpdatedAt = remoteMenuItem.serverUpdatedAt ?: 0L
                    )
                    }
                }
            logSkippedRecords(
                label = "menu item",
                skipped = masterData.menuItems.filter { remoteMenuItem ->
                    val localCategoryId = remoteMenuItem.serverCategoryId?.let { serverId ->
                        categoryIdMap[serverId] ?: serverId
                    } ?: remoteMenuItem.categoryId
                    localCategoryId !in knownCategoryIds
                }
            ) { remoteMenuItem ->
                "menuItemId=${remoteMenuItem.localId ?: remoteMenuItem.serverId}, categoryId=${remoteMenuItem.categoryId}, serverCategoryId=${remoteMenuItem.serverCategoryId}"
            }
            menuDao.insertSyncedMenuItems(resolvedMenuItems)
        }

        // Fetch menu item ID mapping
        val menuItemIdMap = menuDao.getAllMenuItemServerIds().associate { it.serverId to it.id }
        val knownMenuItemIds = menuItemIdMap.values.toMutableSet()

        if (masterData.itemVariants.isNotEmpty()) {
            val resolvedVariants = masterData.itemVariants.mapNotNull { remoteVariant ->
                    val localMenuItemId = remoteVariant.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteVariant.menuItemId

                    if (localMenuItemId !in knownMenuItemIds) {
                        null
                    } else {
                        ItemVariantEntity(
                        id = remoteVariant.localId ?: remoteVariant.serverId ?: 0L,
                        menuItemId = localMenuItemId ?: 0L,
                        variantName = remoteVariant.variantName.orFallback("Default"),
                        price = remoteVariant.price.toSafeString(),
                        isAvailable = remoteVariant.isAvailable ?: true,
                        sortOrder = remoteVariant.sortOrder ?: 0,
                        currentStock = remoteVariant.currentStock.toSafeString(),
                        lowStockThreshold = remoteVariant.lowStockThreshold.toSafeString(),
                        restaurantId = remoteVariant.restaurantId ?: 0L,
                        deviceId = remoteVariant.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteVariant.updatedAt ?: System.currentTimeMillis(),
                        isDeleted = remoteVariant.isDeleted ?: false,
                        serverId = remoteVariant.serverId,
                        serverUpdatedAt = remoteVariant.serverUpdatedAt ?: 0L
                    )
                    }
                }
            logSkippedRecords(
                label = "item variant",
                skipped = masterData.itemVariants.filter { remoteVariant ->
                    val localMenuItemId = remoteVariant.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteVariant.menuItemId
                    localMenuItemId !in knownMenuItemIds
                }
            ) { remoteVariant ->
                "variantId=${remoteVariant.localId ?: remoteVariant.serverId}, menuItemId=${remoteVariant.menuItemId}, serverMenuItemId=${remoteVariant.serverMenuItemId}"
            }
            menuDao.insertSyncedItemVariants(resolvedVariants)
        }

        // Fetch variant ID mapping
        val variantIdMap = menuDao.getAllVariantServerIds().associate { it.serverId to it.id }
        val knownVariantIds = variantIdMap.values.toMutableSet()

        if (masterData.stockLogs.isNotEmpty()) {
            inventoryDao.deleteAllSyncedStockLogs()
            val resolvedStockLogs = masterData.stockLogs.mapNotNull { remoteStockLog ->
                    val localMenuItemId = remoteStockLog.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteStockLog.menuItemId
                    val localVariantId = remoteStockLog.serverVariantId?.let { serverId ->
                        variantIdMap[serverId] ?: serverId
                    } ?: remoteStockLog.variantId

                    if (localMenuItemId !in knownMenuItemIds || (localVariantId != null && localVariantId !in knownVariantIds)) {
                        null
                    } else {
                        StockLogEntity(
                        id = 0, // Auto-generate
                        menuItemId = localMenuItemId,
                        variantId = localVariantId,
                        delta = remoteStockLog.delta,
                        reason = remoteStockLog.reason.orFallback("adjustment"),
                        createdAt = remoteStockLog.createdAt ?: System.currentTimeMillis(),
                        restaurantId = remoteStockLog.restaurantId ?: 0L,
                        deviceId = remoteStockLog.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteStockLog.updatedAt,
                        isDeleted = remoteStockLog.isDeleted ?: false,
                        serverId = remoteStockLog.serverId,
                        serverMenuItemId = remoteStockLog.serverMenuItemId,
                        serverVariantId = remoteStockLog.serverVariantId,
                        serverUpdatedAt = remoteStockLog.serverUpdatedAt ?: 0L
                    )
                    }
                }
            logSkippedRecords(
                label = "stock log",
                skipped = masterData.stockLogs.filter { remoteStockLog ->
                    val localMenuItemId = remoteStockLog.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteStockLog.menuItemId
                    val localVariantId = remoteStockLog.serverVariantId?.let { serverId ->
                        variantIdMap[serverId] ?: serverId
                    } ?: remoteStockLog.variantId
                    localMenuItemId !in knownMenuItemIds || (localVariantId != null && localVariantId !in knownVariantIds)
                }
            ) { remoteStockLog ->
                "stockLogId=${remoteStockLog.id}, menuItemId=${remoteStockLog.menuItemId}, serverMenuItemId=${remoteStockLog.serverMenuItemId}, variantId=${remoteStockLog.variantId}, serverVariantId=${remoteStockLog.serverVariantId}"
            }
            inventoryDao.insertSyncedStockLogs(resolvedStockLogs)
        }

        if (masterData.bills.isNotEmpty()) {
            val repairedBills = mutableListOf<String>()
            billDao.insertSyncedBills(
                masterData.bills.map { remoteBill ->
                    val resolvedCreatedBy = remoteBill.createdBy?.toLong()?.let { remoteId ->
                        remoteUserIdToLocalId[remoteId] ?: remoteId
                    }?.takeIf { mappedUserId ->
                        val exists = mappedUserId in knownUserIds
                        if (!exists) {
                            repairedBills += "billId=${remoteBill.id}, missingCreatedBy=$mappedUserId"
                        }
                        exists
                    }
                    BillEntity(
                        id = remoteBill.id,
                        restaurantId = remoteBill.restaurantId ?: 0L,
                        deviceId = remoteBill.deviceId.orFallback(""),
                        dailyOrderId = remoteBill.dailyOrderId ?: 0,
                        dailyOrderDisplay = remoteBill.dailyOrderDisplay.orFallback(""),
                        lifetimeOrderId = remoteBill.lifetimeOrderId ?: 0,
                        orderType = remoteBill.orderType.orFallback("order"),
                        customerName = remoteBill.customerName,
                        customerWhatsapp = remoteBill.customerWhatsapp,
                        subtotal = remoteBill.subtotal.toSafeAmount(),
                        gstPercentage = remoteBill.gstPercentage.toSafeAmount(),
                        cgstAmount = remoteBill.cgstAmount.toSafeAmount(),
                        sgstAmount = remoteBill.sgstAmount.toSafeAmount(),
                        customTaxAmount = remoteBill.customTaxAmount.toSafeAmount(),
                        totalAmount = remoteBill.totalAmount.toSafeAmount(),
                        paymentMode = remoteBill.paymentMode.orFallback("cash"),
                        partAmount1 = remoteBill.partAmount1.toSafeAmount(),
                        partAmount2 = remoteBill.partAmount2.toSafeAmount(),
                        paymentStatus = remoteBill.paymentStatus.orFallback("success"),
                        orderStatus = remoteBill.orderStatus.orFallback("completed"),
                        createdBy = resolvedCreatedBy,
                        createdAt = remoteBill.createdAt ?: System.currentTimeMillis(),
                        paidAt = remoteBill.paidAt,
                        isSynced = true,
                        updatedAt = remoteBill.updatedAt ?: System.currentTimeMillis(),
                        isDeleted = remoteBill.isDeleted ?: false,
                        serverId = remoteBill.serverId,
                        serverUpdatedAt = remoteBill.serverUpdatedAt ?: 0L,
                        cancelReason = remoteBill.cancelReason.orFallback("")
                    )
                }
            )
            logRepairedRecords(label = "bill", repaired = repairedBills)
        }

        // Get mapping of serverId to localId for bills to ensure items are linked correctly
        val billServerIdMap = billDao.getAllBillServerIds().associate { it.serverId to it.id }
        val knownBillIds = billServerIdMap.values.toMutableSet()

        if (masterData.billItems.isNotEmpty()) {
            billDao.deleteAllSyncedBillItems()
            val resolvedBillItems = masterData.billItems.mapNotNull { remoteBillItem ->
                    val localBillId = remoteBillItem.serverBillId?.let { serverId ->
                        billServerIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.billId
                    val localMenuItemId = remoteBillItem.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.menuItemId
                    val localVariantId = remoteBillItem.serverVariantId?.let { serverId ->
                        variantIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.variantId

                    if (localBillId !in knownBillIds) {
                        null
                    } else {
                        // If the menu item / variant was deleted, set FK to null.
                        // item_name is a snapshot stored on the bill item itself — it never
                        // depends on the menu item existing, so historical reports stay correct.
                        val safeMenuItemId = localMenuItemId?.takeIf { it in knownMenuItemIds }
                        val safeVariantId  = localVariantId?.takeIf  { it in knownVariantIds  }
                        BillItemEntity(
                        id = 0, // Let SQLite generate local ID
                        billId = localBillId,
                        menuItemId = safeMenuItemId,
                        itemName = remoteBillItem.itemName.orFallback("Unnamed Item"),
                        variantId = safeVariantId,
                        variantName = remoteBillItem.variantName,
                        price = remoteBillItem.price.toSafeAmount(),
                        quantity = remoteBillItem.quantity ?: 1,
                        itemTotal = remoteBillItem.itemTotal.toSafeAmount(),
                        specialInstruction = remoteBillItem.specialInstruction,
                        restaurantId = remoteBillItem.restaurantId ?: 0L,
                        deviceId = remoteBillItem.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteBillItem.updatedAt ?: System.currentTimeMillis(),
                        isDeleted = remoteBillItem.isDeleted ?: false,
                        serverId = remoteBillItem.serverId,
                        serverBillId = remoteBillItem.serverBillId,
                        serverMenuItemId = remoteBillItem.serverMenuItemId,
                        serverVariantId = remoteBillItem.serverVariantId,
                        serverUpdatedAt = remoteBillItem.serverUpdatedAt ?: 0L
                    )
                    }
                }
            logSkippedRecords(
                label = "bill item (orphaned bill)",
                skipped = masterData.billItems.filter { remoteBillItem ->
                    val localBillId = remoteBillItem.serverBillId?.let { serverId ->
                        billServerIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.billId
                    localBillId !in knownBillIds
                }
            ) { remoteBillItem ->
                "billItemId=${remoteBillItem.id}, billId=${remoteBillItem.billId}, serverBillId=${remoteBillItem.serverBillId}"
            }
            billDao.insertSyncedBillItems(resolvedBillItems)
        }

        if (masterData.billPayments.isNotEmpty()) {
            billDao.deleteAllSyncedBillPayments()
            val resolvedBillPayments = masterData.billPayments.mapNotNull { remoteBillPayment ->
                    val localBillId = remoteBillPayment.serverBillId?.let { serverId ->
                        billServerIdMap[serverId] ?: serverId
                    } ?: remoteBillPayment.billId

                    if (localBillId !in knownBillIds) {
                        null
                    } else {
                        BillPaymentEntity(
                        id = 0, // Auto-generate
                        billId = localBillId,
                        paymentMode = remoteBillPayment.paymentMode.orFallback("cash"),
                        amount = remoteBillPayment.amount.toSafeAmount(),
                        createdAt = remoteBillPayment.createdAt ?: System.currentTimeMillis(),
                        restaurantId = remoteBillPayment.restaurantId ?: 0L,
                        deviceId = remoteBillPayment.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteBillPayment.updatedAt ?: System.currentTimeMillis(),
                        isDeleted = remoteBillPayment.isDeleted ?: false,
                        serverId = remoteBillPayment.serverId,
                        serverBillId = remoteBillPayment.serverBillId,
                        serverUpdatedAt = remoteBillPayment.serverUpdatedAt ?: 0L
                    )
                    }
                }
            logSkippedRecords(
                label = "bill payment",
                skipped = masterData.billPayments.filter { remoteBillPayment ->
                    val localBillId = remoteBillPayment.serverBillId?.let { serverId ->
                        billServerIdMap[serverId] ?: serverId
                    } ?: remoteBillPayment.billId
                    localBillId !in knownBillIds
                }
            ) { remoteBillPayment ->
                "billPaymentId=${remoteBillPayment.id}, billId=${remoteBillPayment.billId}, serverBillId=${remoteBillPayment.serverBillId}"
            }
            billDao.insertSyncedBillPayments(resolvedBillPayments)
        }

        // After pulling all data, ensure counters are never behind the actual bills on server.
        // This prevents duplicate order IDs after app reinstall or data clear, where the server
        // profile's counters can be stale (lagging behind the latest bills).
        val currentProfile = restaurantDao.getProfile()
        if (currentProfile != null && masterData.bills.isNotEmpty()) {
            val maxLifetime = masterData.bills.maxOfOrNull { it.lifetimeOrderId ?: 0L } ?: 0L
            val timezone = currentProfile.timezone ?: "Asia/Kolkata"
            val today = java.time.LocalDate.now(java.time.ZoneId.of(timezone)).toString()
            val maxDailyToday = masterData.bills
                .filter { bill ->
                    val billDate = java.time.Instant.ofEpochMilli(bill.createdAt ?: 0L)
                        .atZone(java.time.ZoneId.of(timezone))
                        .toLocalDate().toString()
                    billDate == today
                }
                .maxOfOrNull { it.dailyOrderId ?: 0L } ?: 0L

            val correctedLifetime = maxOf(currentProfile.lifetimeOrderCounter, maxLifetime)
            val correctedDaily = maxOf(currentProfile.dailyOrderCounter, maxDailyToday)
            if (correctedLifetime != currentProfile.lifetimeOrderCounter || correctedDaily != currentProfile.dailyOrderCounter) {
                Log.i("MasterSyncProcessor", "Correcting counters after pull: lifetime ${currentProfile.lifetimeOrderCounter}→$correctedLifetime, daily ${currentProfile.dailyOrderCounter}→$correctedDaily")
                restaurantDao.saveProfile(currentProfile.copy(
                    lifetimeOrderCounter = correctedLifetime,
                    dailyOrderCounter = correctedDaily,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    } // end withTransaction

    /**
     * Before a push: reads the kitchen PrinterProfileEntity and writes its fields into the
     * RestaurantProfileEntity (marking it unsynced if anything changed). This ensures the
     * server always receives the latest kitchen printer MAC even if only the printer profile
     * table was updated (which doesn't have its own sync endpoint).
     */
    private suspend fun syncKitchenPrinterIntoProfile(profile: RestaurantProfileEntity?) {
        if (profile == null) return
        val kitchen = printerProfileDao.getByRole(com.khanabook.lite.pos.domain.model.PrinterRole.KITCHEN.name)
        val mac = kitchen?.macAddress?.takeIf { it.isNotBlank() }

        val unchanged = profile.kitchenPrinterMac == mac &&
            profile.kitchenPrinterName == kitchen?.name &&
            profile.kitchenPrinterEnabled == (kitchen?.enabled ?: false) &&
            profile.kitchenPrinterPaperSize == (kitchen?.paperSize ?: "58mm")
        if (unchanged) return

        restaurantDao.insertSyncedRestaurantProfiles(
            listOf(
                profile.copy(
                    kitchenPrinterEnabled = kitchen?.enabled ?: false,
                    kitchenPrinterName = kitchen?.name,
                    kitchenPrinterMac = mac,
                    kitchenPrinterPaperSize = kitchen?.paperSize ?: "58mm",
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
    }
}
