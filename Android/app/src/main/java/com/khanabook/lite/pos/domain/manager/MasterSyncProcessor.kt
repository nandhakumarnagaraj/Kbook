package com.khanabook.lite.pos.domain.manager

import android.util.Log
import androidx.room.withTransaction
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.data.remote.dto.*
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.SyncConflictException
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import retrofit2.HttpException

@Singleton
class MasterSyncProcessor @Inject constructor(
    private val api: KhanaBookApi,
    private val databaseProvider: DatabaseProvider,
    private val billDao: BillDao,
    private val restaurantDao: RestaurantDao,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val menuDao: MenuDao,
    private val inventoryDao: InventoryDao,
    private val printerProfileDao: PrinterProfileDao,
    private val sessionManager: SessionManager
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
        markSynced: suspend (List<Long>) -> Unit,
        onServerIds: (suspend (Map<Long, Long>) -> Unit)? = null
    ): List<Long> {
        if (records.isEmpty()) return emptyList()

        val batches = records.chunked(50)
        val successfulIds = mutableListOf<Long>()
        logInfo("Pushing ${records.size} $label record(s) in ${batches.size} batch(es)")

        batches.forEachIndexed { index, batch ->
            try {
                val response = push(batch.map(transform))
                markSynced(response.successfulLocalIds)
                successfulIds += response.successfulLocalIds
                response.localToServerIdMap?.takeIf { it.isNotEmpty() }?.let { onServerIds?.invoke(it) }

                logInfo(
                    "Pushed $label batch ${index + 1}/${batches.size}: success=${response.successfulLocalIds.size}, failed=${response.failedLocalIds.size}"
                )

                if (response.failedLocalIds.isNotEmpty()) {
                    val failedReasons = response.failedReasons.orEmpty()
                    logWarn(
                        "Server rejected $label localIds=${response.failedLocalIds.joinToString(",")} reasons=$failedReasons"
                    )
                    throw SyncConflictException(
                        IllegalStateException(
                            "Server rejected $label localIds=${response.failedLocalIds.joinToString(",")}"
                        ),
                        failedLocalIds = response.failedLocalIds,
                        failedReasons = failedReasons,
                        syncEntityLabel = label
                    )
                }
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 409) {
                    val errorBody = try { e.response()?.errorBody()?.string() } catch (ignored: Exception) { null }
                    Log.e("MasterSyncProcessor", "Conflict while pushing $label batch ${index + 1}/${batches.size}. Body: $errorBody", e)
                    throw SyncConflictException(e)
                }
                logError(
                    "Failed pushing $label batch ${index + 1}/${batches.size}",
                    e
                )
                throw e
            }
        }
        return successfulIds
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

    suspend fun pushSingleBill(billLocalId: Long) {
        val restaurantId = sessionManager.getRestaurantId()
        if (restaurantId <= 0L) throw IllegalStateException("Push phase aborted: restaurantId not set in session")

        val bill = billDao.getBillById(billLocalId, restaurantId)
            ?: throw IllegalStateException("Bill $billLocalId not found locally")

        if (bill.restaurantId != restaurantId) {
            Log.w("MasterSyncProcessor", "pushSingleBill: Bill restaurantId (${bill.restaurantId}) does not match session restaurantId ($restaurantId). Skipping.")
            return
        }

        val serverCreatedBy = bill.createdBy?.let { userDao.getUserById(it)?.serverId }
        pushBatches(
            label = "bill",
            records = listOf(bill),
            transform = { it.toSyncDto(serverCreatedBy) },
            push = api::pushBills,
            markSynced = { ids -> billDao.markBillsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> billDao.updateServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        backfillChildServerBillIds(restaurantId)
        pushBatches(
            label = "bill items",
            records = billDao.getUnsyncedBillItems(restaurantId).filter { it.billId == billLocalId },
            transform = BillItemEntity::toSyncDto,
            push = api::pushBillItems,
            markSynced = { ids -> billDao.markBillItemsAsSynced(ids, restaurantId) }
        )
    }

    suspend fun pushAll(onStepChange: ((SyncStep) -> Unit)? = null): Boolean {
        // Guard: never push data with an invalid tenant
        val restaurantId = sessionManager.getRestaurantId()
        if (restaurantId <= 0L) {
            Log.w("MasterSyncProcessor", "Push aborted: restaurantId not set (value=$restaurantId)")
            return false
        }

        val profile = restaurantDao.getProfile(restaurantId) ?: restaurantDao.getProfile()
        // Embed kitchen printer into restaurant profile before pushing.
        syncKitchenPrinterIntoProfile(profile)

        val unsyncedProfiles = restaurantDao.getUnsyncedRestaurantProfiles()
        val validProfiles = unsyncedProfiles.filter { it.restaurantId == restaurantId }
        pushBatches(
            label = "restaurant profiles",
            records = validProfiles,
            transform = RestaurantProfileEntity::toSyncDto,
            push = api::pushRestaurantProfiles,
            markSynced = restaurantDao::markRestaurantProfilesAsSynced,
            onServerIds = { map -> map.forEach { (localId, serverId) -> restaurantDao.updateServerIdByLocalId(localId, serverId) } }
        )

        val unsyncedUsers = userDao.getUnsyncedUsers()
        val validUsers = unsyncedUsers.filter { it.restaurantId == restaurantId }
        pushBatches(
            label = "users",
            records = validUsers,
            transform = UserEntity::toSyncDto,
            push = api::pushUsers,
            markSynced = userDao::markUsersAsSynced,
            onServerIds = { map -> map.forEach { (localId, serverId) -> userDao.updateServerIdByLocalId(localId, serverId) } }
        )

        val unsyncedCategories = categoryDao.getUnsyncedCategories(restaurantId)
        val validCategories = unsyncedCategories.filter { it.restaurantId == restaurantId }
        pushBatches(
            label = "categories",
            records = validCategories,
            transform = CategoryEntity::toSyncDto,
            push = api::pushCategories,
            markSynced = { ids -> categoryDao.markCategoriesAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> categoryDao.updateServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        val unsyncedMenuItems = menuDao.getUnsyncedMenuItems(restaurantId)
        val validMenuItems = unsyncedMenuItems.filter { it.restaurantId == restaurantId }
        pushBatches(
            label = "menu items",
            records = validMenuItems,
            transform = MenuItemEntity::toSyncDto,
            push = api::pushMenuItems,
            markSynced = { ids -> menuDao.markMenuItemsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> menuDao.updateMenuItemServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        val unsyncedVariants = menuDao.getUnsyncedItemVariants(restaurantId)
        val validVariants = unsyncedVariants.filter { it.restaurantId == restaurantId }
        pushBatches(
            label = "item variants",
            records = validVariants,
            transform = ItemVariantEntity::toSyncDto,
            push = api::pushItemVariants,
            markSynced = { ids -> menuDao.markItemVariantsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> menuDao.updateVariantServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        onStepChange?.invoke(SyncStep.PushStockLogs)
        val unsyncedStockLogs = inventoryDao.getUnsyncedStockLogs(restaurantId)
        val validStockLogs = unsyncedStockLogs.filter { it.restaurantId == restaurantId }
        pushBatches(
            label = "stock logs",
            records = validStockLogs,
            transform = StockLogEntity::toSyncDto,
            push = api::pushStockLogs,
            markSynced = { ids -> inventoryDao.markStockLogsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> inventoryDao.updateServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        // ── Reconcile any bills that were pushed successfully in a previous cycle
        // but whose local isSynced flag was never flipped (e.g. response lost mid-air).
        // reconcileServerAcknowledgedBills() also catches the 409-loop case where the
        // server already has the bill (server_id is set via updateServerIdByLocalId)
        // but the local row still shows isSynced=false.
        val reconciledCount = billDao.reconcileServerAcknowledgedBills(restaurantId)
        if (reconciledCount > 0) {
            Log.i("MasterSyncProcessor", "Reconciled $reconciledCount bill(s) that had server_id but were still marked unsynced")
        }

        val unsyncedBills = billDao.getUnsyncedBills(restaurantId)
        val validBills = unsyncedBills.filter { it.restaurantId == restaurantId }
        val skippedBills = unsyncedBills.size - validBills.size
        if (skippedBills > 0) {
            Log.w("MasterSyncProcessor", "Skipping $skippedBills bill(s) with mismatched restaurantId (expected=$restaurantId)")
        }
        val userMap = userDao.getAllUsersOnce().associate { it.id to it.serverId }
        pushBatches(
            label = "bills",
            records = validBills,
            transform = { bill -> bill.toSyncDto(userMap[bill.createdBy]) },
            push = api::pushBills,
            markSynced = { ids -> billDao.markBillsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> billDao.updateServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        backfillChildServerBillIds(restaurantId)
        val unsyncedBillItems = billDao.getUnsyncedBillItemsWithSyncedParent(restaurantId)
        pushBatches(
            label = "bill items",
            records = unsyncedBillItems,
            transform = BillItemEntity::toSyncDto,
            push = api::pushBillItems,
            markSynced = { ids -> billDao.markBillItemsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> billDao.updateBillItemServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        val unsyncedBillPayments = billDao.getUnsyncedBillPaymentsWithSyncedParent(restaurantId)
        pushBatches(
            label = "bill payments",
            records = unsyncedBillPayments,
            transform = BillPaymentEntity::toSyncDto,
            push = api::pushBillPayments,
            markSynced = { ids -> billDao.markBillPaymentsAsSynced(ids, restaurantId) },
            onServerIds = { map -> map.forEach { (localId, serverId) -> billDao.updateBillPaymentServerIdByLocalId(localId, serverId, restaurantId) } }
        )

        return true
    }

    private suspend fun backfillChildServerBillIds(restaurantId: Long) {
        val itemCount = billDao.backfillBillItemServerBillIds(restaurantId)
        val paymentCount = billDao.backfillBillPaymentServerBillIds(restaurantId)
        if (itemCount > 0 || paymentCount > 0) {
            logInfo("Backfilled serverBillId for $itemCount bill item(s) and $paymentCount bill payment(s)")
        }
    }

    suspend fun quarantineFailedSyncRecords(exception: SyncConflictException): Int {
        if (exception.failedLocalIds.isEmpty()) return 0
        val restaurantId = sessionManager.getRestaurantId()
        if (restaurantId <= 0L) return 0

        return when (exception.syncEntityLabel) {
            "bills" -> quarantineFailedBills(exception, restaurantId)
            "bill items" -> quarantineFailedBillItems(exception, restaurantId)
            "bill payments" -> quarantineFailedBillPayments(exception, restaurantId)
            else -> 0
        }
    }

    private suspend fun quarantineFailedBills(exception: SyncConflictException, restaurantId: Long): Int {
        val failedAt = System.currentTimeMillis()
        var quarantined = 0
        exception.failedLocalIds.forEach { billId ->
            val reason = exception.failedReasons[billId]
                ?.takeIf { it.isNotBlank() }
                ?: "Bill sync rejected after automatic recovery"
            quarantined += billDao.markBillSyncFailedPermanently(
                billId = billId,
                restaurantId = restaurantId,
                reason = reason,
                failedAt = failedAt
            )
        }
        if (quarantined > 0) {
            logWarn("Quarantined $quarantined bill(s) after failed automatic sync recovery")
        }
        return quarantined
    }

    private suspend fun quarantineFailedBillItems(exception: SyncConflictException, restaurantId: Long): Int {
        val failedIds = exception.failedLocalIds.distinct()
        if (failedIds.isEmpty()) return 0

        val failedItems = billDao.getBillItemsByIds(failedIds, restaurantId)
        val itemBillIds = failedItems.map { it.billId }.distinct()
        val billsById = if (itemBillIds.isNotEmpty()) {
            billDao.getBillsByIds(itemBillIds, restaurantId).associateBy { it.id }
        } else {
            emptyMap()
        }
        val failedAt = System.currentTimeMillis()
        val quarantineRecords = failedItems.map { item ->
            val parentBill = billsById[item.billId]
            SyncQuarantineEntity(
                restaurantId = restaurantId,
                parentBillId = item.billId,
                parentBillDisplay = parentBill?.dailyOrderDisplay,
                childEntityType = "bill_item",
                childLocalId = item.id,
                childDisplayName = item.itemName,
                childSummary = "${item.itemName} x${item.quantity}",
                childSnapshotJson = buildBillItemSnapshot(item),
                syncFailureReason = exception.failedReasons[item.id]?.takeIf { it.isNotBlank() }
                    ?: "Bill item sync rejected after automatic recovery",
                quarantinedAt = failedAt
            )
        }
        if (quarantineRecords.isNotEmpty()) {
            billDao.upsertSyncQuarantineRecords(quarantineRecords)
        }
        billDao.markBillItemsAsSynced(failedIds, restaurantId)
        logWarn(
            "Quarantined ${failedIds.size} legacy bill item(s) after failed automatic sync recovery: " +
                failedIds.joinToString(",")
        )
        failedIds.forEach { id ->
            exception.failedReasons[id]?.takeIf { it.isNotBlank() }?.let { reason ->
                logWarn("Quarantined bill item localId=$id reason=$reason")
            }
        }
        return failedIds.size
    }

    private suspend fun quarantineFailedBillPayments(exception: SyncConflictException, restaurantId: Long): Int {
        val failedIds = exception.failedLocalIds.distinct()
        if (failedIds.isEmpty()) return 0

        val failedPayments = billDao.getBillPaymentsByIds(failedIds, restaurantId)
        val paymentBillIds = failedPayments.map { it.billId }.distinct()
        val billsById = if (paymentBillIds.isNotEmpty()) {
            billDao.getBillsByIds(paymentBillIds, restaurantId).associateBy { it.id }
        } else {
            emptyMap()
        }
        val failedAt = System.currentTimeMillis()
        val quarantineRecords = failedPayments.map { payment ->
            val parentBill = billsById[payment.billId]
            SyncQuarantineEntity(
                restaurantId = restaurantId,
                parentBillId = payment.billId,
                parentBillDisplay = parentBill?.dailyOrderDisplay,
                childEntityType = "bill_payment",
                childLocalId = payment.id,
                childDisplayName = payment.paymentMode,
                childSummary = "${payment.paymentMode} ${payment.amount}",
                childSnapshotJson = buildBillPaymentSnapshot(payment),
                syncFailureReason = exception.failedReasons[payment.id]?.takeIf { it.isNotBlank() }
                    ?: "Bill payment sync rejected after automatic recovery",
                quarantinedAt = failedAt
            )
        }
        if (quarantineRecords.isNotEmpty()) {
            billDao.upsertSyncQuarantineRecords(quarantineRecords)
        }
        billDao.markBillPaymentsAsSynced(failedIds, restaurantId)
        logWarn(
            "Quarantined ${failedIds.size} legacy bill payment(s) after failed automatic sync recovery: " +
                failedIds.joinToString(",")
        )
        failedIds.forEach { id ->
            exception.failedReasons[id]?.takeIf { it.isNotBlank() }?.let { reason ->
                logWarn("Quarantined bill payment localId=$id reason=$reason")
            }
        }
        return failedIds.size
    }

    private fun buildBillItemSnapshot(item: BillItemEntity): String {
        return JSONObject()
            .put("id", item.id)
            .put("billId", item.billId)
            .put("menuItemId", item.menuItemId)
            .put("itemName", item.itemName)
            .put("variantId", item.variantId)
            .put("variantName", item.variantName)
            .put("price", item.price)
            .put("quantity", item.quantity)
            .put("itemTotal", item.itemTotal)
            .put("specialInstruction", item.specialInstruction)
            .put("sentToKot", item.sentToKot)
            .put("restaurantId", item.restaurantId)
            .put("deviceId", item.deviceId)
            .put("isSynced", item.isSynced)
            .put("updatedAt", item.updatedAt)
            .put("isDeleted", item.isDeleted)
            .put("serverId", item.serverId)
            .put("serverBillId", item.serverBillId)
            .put("serverMenuItemId", item.serverMenuItemId)
            .put("serverVariantId", item.serverVariantId)
            .put("serverUpdatedAt", item.serverUpdatedAt)
            .toString()
    }

    private fun buildBillPaymentSnapshot(payment: BillPaymentEntity): String {
        return JSONObject()
            .put("id", payment.id)
            .put("billId", payment.billId)
            .put("paymentMode", payment.paymentMode)
            .put("amount", payment.amount)
            .put("createdAt", payment.createdAt)
            .put("restaurantId", payment.restaurantId)
            .put("deviceId", payment.deviceId)
            .put("isSynced", payment.isSynced)
            .put("updatedAt", payment.updatedAt)
            .put("isDeleted", payment.isDeleted)
            .put("serverId", payment.serverId)
            .put("serverBillId", payment.serverBillId)
            .put("serverUpdatedAt", payment.serverUpdatedAt)
            .put("gatewayTxnId", payment.gatewayTxnId)
            .put("gatewayStatus", payment.gatewayStatus)
            .put("verifiedBy", payment.verifiedBy)
            .toString()
    }

    suspend fun insertMasterData(masterData: MasterSyncResponse) {
        val restaurantId = sessionManager.getRestaurantId()

        // Pre-download logo to local file OUTSIDE the DB transaction so printing
        // is fully offline and we never hold a Room transaction open across a network call.
        val resolvedLogoPaths = mutableMapOf<Int, String?>()
        if (masterData.profiles.isNotEmpty()) {
            val currentLocalProfile = if (restaurantId > 0) restaurantDao.getProfile(restaurantId) else restaurantDao.getProfile()
            masterData.profiles.forEachIndexed { index, remoteProfile ->
                val useRemoteLogo = remoteProfile.logoVersion > (currentLocalProfile?.logoVersion ?: 0)
                resolvedLogoPaths[index] = if (useRemoteLogo) {
                    val url = remoteProfile.logoUrl?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        // Download succeeds → store the local assetRef;
                        // fails (offline) → fall back to whatever the server sent.
                        AppAssetStore.saveUrlToAppAsset(
                            url = url,
                            folder = "logo",
                            fileName = "logo_${remoteProfile.logoVersion}.png"
                        ) ?: remoteProfile.logoPath
                    } else {
                        remoteProfile.logoPath
                    }
                } else {
                    currentLocalProfile?.logoPath
                }
            }
        }

        databaseProvider.getDatabase().withTransaction {
            if (masterData.profiles.isNotEmpty()) {
                val currentLocalProfile = if (restaurantId > 0) restaurantDao.getProfile(restaurantId) else restaurantDao.getProfile()
                restaurantDao.insertSyncedRestaurantProfiles(
                    masterData.profiles.mapIndexed { index, remoteProfile ->
                        val localProfile = currentLocalProfile
                        val useRemoteLogo = remoteProfile.logoVersion > (localProfile?.logoVersion ?: 0)
                        val useRemoteUpiQr = remoteProfile.upiQrVersion > (localProfile?.upiQrVersion ?: 0)
                        RestaurantProfileEntity(
                            id = remoteProfile.restaurantId ?: (currentLocalProfile?.id ?: 1L),
                            shopName = remoteProfile.shopName.orFallback("My Shop"),
                            shopAddress = remoteProfile.shopAddress.orFallback(""),
                            whatsappNumber = remoteProfile.whatsappNumber.orFallback(""),
                            email = remoteProfile.email.orFallback(""),
                            logoPath = resolvedLogoPaths[index],
                        logoUrl = if (useRemoteLogo) remoteProfile.logoUrl else localProfile?.logoUrl,
                        logoVersion = maxOf(remoteProfile.logoVersion, localProfile?.logoVersion ?: 0),
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
                        upiQrPath = if (useRemoteUpiQr) remoteProfile.upiQrPath else localProfile?.upiQrPath,
                        upiQrUrl = if (useRemoteUpiQr) remoteProfile.upiQrUrl else localProfile?.upiQrUrl,
                        upiQrVersion = maxOf(remoteProfile.upiQrVersion, localProfile?.upiQrVersion ?: 0),
                        upiHandle = remoteProfile.upiHandle.orFallback(""),
                        upiMobile = remoteProfile.upiMobile.orFallback(""),
                        cashEnabled = remoteProfile.cashEnabled ?: true,
                        posEnabled = remoteProfile.posEnabled ?: false,
                        zomatoEnabled = remoteProfile.zomatoEnabled ?: false,
                        swiggyEnabled = remoteProfile.swiggyEnabled ?: false,
                        ownWebsiteEnabled = remoteProfile.ownWebsiteEnabled ?: false,
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
                        orderPaymentFlowMode = remoteProfile.orderPaymentFlowMode
                            ?.takeIf { it.isNotBlank() }
                            ?: currentLocalProfile?.orderPaymentFlowMode
                            ?: "pay_before_food",
                        restaurantId = remoteProfile.restaurantId ?: 0L,
                        deviceId = remoteProfile.deviceId.orFallback("unknown_device"),
                        isSynced = true,
                        updatedAt = remoteProfile.updatedAt,
                        timezone = "Asia/Kolkata",
                        reviewUrl = remoteProfile.reviewUrl,
                        invoiceFooter = remoteProfile.invoiceFooter,
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
                    val existing = printerProfileDao.getByRole(
                        com.khanabook.lite.pos.domain.model.PrinterRole.KITCHEN.name,
                        remoteProfile.restaurantId ?: restaurantId
                    )
                    printerProfileDao.upsert(
                        PrinterProfileEntity(
                            id = existing?.id ?: 0,
                            role = com.khanabook.lite.pos.domain.model.PrinterRole.KITCHEN.name,
                            restaurantId = remoteProfile.restaurantId ?: restaurantId,
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
                val assignedLocalId = localUser?.id ?: remoteUser.serverId ?: remoteUser.id
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
                        id = remoteCategory.serverId ?: remoteCategory.id,
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
        val categoryIdMap = categoryDao.getAllCategoryServerIds(restaurantId).associate { it.serverId to it.id }
        val knownCategoryIds = categoryIdMap.values.toMutableSet()

        if (masterData.menuItems.isNotEmpty()) {
            val currentDeviceId = sessionManager.getDeviceId()
            val preferredMenuItemIdsByServerId = mutableMapOf<Long, Long>()
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
                        val isMyDevice = remoteMenuItem.deviceId == currentDeviceId
                        val assignedId = when {
                            isMyDevice && (remoteMenuItem.localId ?: 0L) > 0L -> remoteMenuItem.localId ?: 0L
                            (remoteMenuItem.serverId ?: 0L) > 0L -> remoteMenuItem.serverId ?: 0L
                            else -> remoteMenuItem.localId ?: 0L
                        }
                        if (isMyDevice && (remoteMenuItem.serverId ?: 0L) > 0L && assignedId > 0L) {
                            preferredMenuItemIdsByServerId[remoteMenuItem.serverId ?: 0L] = assignedId
                        }
                        MenuItemEntity(
                        id = assignedId,
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
            if (preferredMenuItemIdsByServerId.isNotEmpty()) {
                menuDao.hideDuplicateMenuItemsByServerIds(
                    serverIds = preferredMenuItemIdsByServerId.keys.toList(),
                    preferredIds = preferredMenuItemIdsByServerId.values.toList(),
                    restaurantId = restaurantId
                )
            }
        }

        // Fetch menu item ID mapping
        val menuItemIdMap = menuDao.getAllMenuItemServerIds(restaurantId).associate { it.serverId to it.id }
        val knownMenuItemIds = menuItemIdMap.values.toMutableSet()

        if (masterData.itemVariants.isNotEmpty()) {
            val currentDeviceId = sessionManager.getDeviceId()
            val preferredVariantIdsByServerId = mutableMapOf<Long, Long>()
            val resolvedVariants = masterData.itemVariants.mapNotNull { remoteVariant ->
                    val localMenuItemId = remoteVariant.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteVariant.menuItemId

                    if (localMenuItemId !in knownMenuItemIds) {
                        null
                    } else {
                        val isMyDevice = remoteVariant.deviceId == currentDeviceId
                        val assignedId = when {
                            isMyDevice && (remoteVariant.localId ?: 0L) > 0L -> remoteVariant.localId ?: 0L
                            (remoteVariant.serverId ?: 0L) > 0L -> remoteVariant.serverId ?: 0L
                            else -> remoteVariant.localId ?: 0L
                        }
                        if (isMyDevice && (remoteVariant.serverId ?: 0L) > 0L && assignedId > 0L) {
                            preferredVariantIdsByServerId[remoteVariant.serverId ?: 0L] = assignedId
                        }
                        ItemVariantEntity(
                        id = assignedId,
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
            if (preferredVariantIdsByServerId.isNotEmpty()) {
                menuDao.hideDuplicateVariantsByServerIds(
                    serverIds = preferredVariantIdsByServerId.keys.toList(),
                    preferredIds = preferredVariantIdsByServerId.values.toList(),
                    restaurantId = restaurantId
                )
            }
        }

        // Fetch variant ID mapping
        val variantIdMap = menuDao.getAllVariantServerIds(restaurantId).associate { it.serverId to it.id }
        val knownVariantIds = variantIdMap.values.toMutableSet()

        if (masterData.stockLogs.isNotEmpty()) {
            val resolvedStockLogs = masterData.stockLogs.mapNotNull { remoteLog ->
                val localMenuItemId = remoteLog.serverMenuItemId?.let { menuItemIdMap[it] } ?: remoteLog.menuItemId
                val localVariantId = remoteLog.serverVariantId?.let { variantIdMap[it] } ?: remoteLog.variantId

                if (localMenuItemId !in knownMenuItemIds) return@mapNotNull null
                if (remoteLog.serverVariantId != null && remoteLog.serverVariantId > 0 && localVariantId !in knownVariantIds) return@mapNotNull null

                remoteLog.copy(
                    menuItemId = localMenuItemId,
                    variantId = localVariantId,
                    isSynced = true
                )
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
                    val isMyDevice = remoteBill.deviceId == sessionManager.getDeviceId()
                    val assignedId = if (isMyDevice && remoteBill.id > 0) remoteBill.id else (remoteBill.serverId ?: remoteBill.id)
                    BillEntity(
                        id = assignedId,
                        restaurantId = remoteBill.restaurantId ?: 0L,
                        deviceId = remoteBill.deviceId.orFallback(""),
                        dailyOrderId = remoteBill.dailyOrderId ?: 0,
                        dailyOrderDisplay = remoteBill.dailyOrderDisplay.orFallback(""),
                        lifetimeOrderId = remoteBill.lifetimeOrderId ?: 0,
                        orderType = remoteBill.orderType.orFallback("order"),
                        sourceChannel = remoteBill.sourceChannel.orFallback(""),
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
                        cancelReason = remoteBill.cancelReason.orFallback(""),
                        publicToken = remoteBill.publicToken,
                        // Server-owned: null if no refund has been recorded on this bill.
                        refundAmount = remoteBill.refundAmount?.toString()
                    )
                }
            )
            logRepairedRecords(label = "bill", repaired = repairedBills)

            // ── Post-pull reconciliation ─────────────────────────────────────────
            // The pull upserts bills by the server-assigned ID (which may differ from
            // the device-local primary key). This can leave the ORIGINAL local rows
            // (created offline) still marked isSynced=false, causing an endless 409 loop
            // on the next push. Fix in THREE tiers:
            //
            // 1. Reconcile by (deviceId, localId) — the ORIGINAL device-local ID that
            //    was used when the bill was first created. This is the most reliable
            //    identifier because it never changes and is always present in the pull
            //    response (localId field). We group by the ORIGINAL deviceId from the
            //    server response (not the current session deviceId) so this also works
            //    when the deviceId has changed (reinstall, new device, etc.).
            //
            // 2. Reconcile by (deviceId, lifetimeOrderId) — globally unique per device.
            //    Falls back to this when localId matching doesn't cover all cases.
            //
            // 3. Reconcile by server_id IS NOT NULL — catches any bills that were
            //    previously pushed successfully but whose isSynced flag was never set.
            val billsByDevice = masterData.bills.groupBy { it.deviceId }
            for ((deviceId, deviceBills) in billsByDevice) {
                val deviceLocalIds = deviceBills.map { it.id }.filter { it > 0L }
                if (deviceLocalIds.isNotEmpty()) {
                    val reconciled = billDao.markBillsSyncedByDeviceIdAndLocalIds(
                        deviceId = deviceId,
                        restaurantId = restaurantId,
                        localIds = deviceLocalIds
                    )
                    if (reconciled > 0) {
                        Log.i("MasterSyncProcessor", "Post-pull: marked $reconciled orphaned local bill(s) as synced via (deviceId=$deviceId, localId) match")
                    }
                }
            }

            val myDeviceBillLifetimeIds = masterData.bills
                .filter { it.deviceId == sessionManager.getDeviceId() }
                .mapNotNull { it.lifetimeOrderId }
                .filter { it > 0 }
            if (myDeviceBillLifetimeIds.isNotEmpty()) {
                val fixed = billDao.markBillsSyncedByLifetimeIds(
                    deviceId = sessionManager.getDeviceId(),
                    restaurantId = restaurantId,
                    lifetimeOrderIds = myDeviceBillLifetimeIds
                )
                if (fixed > 0) {
                    Log.i("MasterSyncProcessor", "Post-pull: marked $fixed orphaned local bill(s) as synced via lifetimeOrderId match")
                }
            }

            val reconciledByServerId = billDao.reconcileServerAcknowledgedBills(restaurantId)
            if (reconciledByServerId > 0) {
                Log.i("MasterSyncProcessor", "Post-pull: reconciled $reconciledByServerId bill(s) that had server_id but were still unsynced")
            }
        }

        // Get mapping of serverId to localId for bills to ensure items are linked correctly
        val billServerIdMap = billDao.getAllBillServerIds(restaurantId).associate { it.serverId to it.id }
        val knownBillIds = billServerIdMap.values.toMutableSet()

        if (masterData.billItems.isNotEmpty()) {
            billDao.deleteAllSyncedBillItems(restaurantId)
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
            billDao.deleteAllSyncedBillPayments(restaurantId)
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
        val currentProfile = if (restaurantId > 0) restaurantDao.getProfile(restaurantId) else restaurantDao.getProfile()
        if (currentProfile != null && masterData.bills.isNotEmpty()) {
            val maxLifetime = masterData.bills.maxOfOrNull { it.lifetimeOrderId ?: 0L } ?: 0L
            val timezone = "Asia/Kolkata"
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
                    isSynced = true,
                    updatedAt = System.currentTimeMillis()
                ))
            }
        }
    } // end withTransaction
    }

    /**
     * Before a push: reads the kitchen PrinterProfileEntity and writes its fields into the
     * RestaurantProfileEntity (marking it unsynced if anything changed). This ensures the
     * server always receives the latest kitchen printer MAC even if only the printer profile
     * table was updated (which doesn't have its own sync endpoint).
     */
    private suspend fun syncKitchenPrinterIntoProfile(profile: RestaurantProfileEntity?) {
        if (profile == null) return
        val kitchen = printerProfileDao.getByRole(
            com.khanabook.lite.pos.domain.model.PrinterRole.KITCHEN.name,
            profile.restaurantId
        )
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
