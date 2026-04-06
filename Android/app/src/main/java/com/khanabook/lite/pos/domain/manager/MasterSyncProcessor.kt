package com.khanabook.lite.pos.domain.manager

import android.util.Log
import androidx.room.withTransaction
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterSyncProcessor @Inject constructor(
    private val api: KhanaBookApi,
    private val database: AppDatabase,
    private val billDao: BillDao,
    private val restaurantDao: RestaurantDao,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val menuDao: MenuDao,
    private val inventoryDao: InventoryDao
) {

    private fun String?.orFallback(default: String): String = this?.takeUnless { it.isBlank() } ?: default
    
    private fun Double?.toSafeString(): String {
        if (this == null) return "0.00"
        return java.math.BigDecimal.valueOf(this)
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
        return try {
            // Guard: never push data with an invalid tenant — it would be written to the null bucket
            val profile = restaurantDao.getProfile()
            val restaurantId = profile?.restaurantId ?: 0L
            if (restaurantId <= 0L) {
                Log.w("MasterSyncProcessor", "Push aborted: restaurantId not set (value=$restaurantId)")
                return false
            }

            val unsyncedProfiles = restaurantDao.getUnsyncedRestaurantProfiles()
            if (unsyncedProfiles.isNotEmpty()) {
                unsyncedProfiles.chunked(50).forEach { batch ->
                    val response = api.pushRestaurantProfiles(batch)
                    restaurantDao.markRestaurantProfilesAsSynced(response.successfulLocalIds)
                }
            }

            val unsyncedUsers = userDao.getUnsyncedUsers()
            if (unsyncedUsers.isNotEmpty()) {
                unsyncedUsers.chunked(50).forEach { batch ->
                    val response = api.pushUsers(batch)
                    userDao.markUsersAsSynced(response.successfulLocalIds)
                }
            }

            
            val unsyncedCategories = categoryDao.getUnsyncedCategories()
            if (unsyncedCategories.isNotEmpty()) {
                unsyncedCategories.chunked(50).forEach { batch ->
                    val response = api.pushCategories(batch)
                    categoryDao.markCategoriesAsSynced(response.successfulLocalIds)
                }
            }

            val unsyncedMenuItems = menuDao.getUnsyncedMenuItems()
            if (unsyncedMenuItems.isNotEmpty()) {
                unsyncedMenuItems.chunked(50).forEach { batch ->
                    val response = api.pushMenuItems(batch)
                    menuDao.markMenuItemsAsSynced(response.successfulLocalIds)
                }
            }

            val unsyncedVariants = menuDao.getUnsyncedItemVariants()
            if (unsyncedVariants.isNotEmpty()) {
                unsyncedVariants.chunked(50).forEach { batch ->
                    val response = api.pushItemVariants(batch)
                    menuDao.markItemVariantsAsSynced(response.successfulLocalIds)
                }
            }

            
            val unsyncedStockLogs = inventoryDao.getUnsyncedStockLogs()
            if (unsyncedStockLogs.isNotEmpty()) {
                unsyncedStockLogs.chunked(50).forEach { batch ->
                    val response = api.pushStockLogs(batch)
                    inventoryDao.markStockLogsAsSynced(response.successfulLocalIds)
                }
            }

            
            val unsyncedBills = billDao.getUnsyncedBills()
            if (unsyncedBills.isNotEmpty()) {
                unsyncedBills.chunked(50).forEach { batch ->
                    val response = api.pushBills(batch)
                    billDao.markBillsAsSynced(response.successfulLocalIds)
                }
            }

            val unsyncedBillItems = billDao.getUnsyncedBillItems()
            if (unsyncedBillItems.isNotEmpty()) {
                unsyncedBillItems.chunked(50).forEach { batch ->
                    val response = api.pushBillItems(batch)
                    billDao.markBillItemsAsSynced(response.successfulLocalIds)
                }
            }

            val unsyncedBillPayments = billDao.getUnsyncedBillPayments()
            if (unsyncedBillPayments.isNotEmpty()) {
                unsyncedBillPayments.chunked(50).forEach { batch ->
                    val response = api.pushBillPayments(batch)
                    billDao.markBillPaymentsAsSynced(response.successfulLocalIds)
                }
            }

            true
        } catch (e: Exception) {
            Log.e("MasterSyncProcessor", "Push failed", e)
            false
        }
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
                        serverUpdatedAt = remoteProfile.serverUpdatedAt ?: 0L
                    )
                }
            )
        }

        if (masterData.users.isNotEmpty()) {
            val localUsersByIdentity = userDao.getAllUsersOnce().associateBy { it.loginId?.takeIf { value -> value.isNotBlank() } ?: it.email }
            userDao.insertSyncedUsers(
                masterData.users.map { remoteUser ->
                    val remoteIdentity = remoteUser.loginId?.takeIf { it.isNotBlank() } ?: remoteUser.email
                    val localUser = localUsersByIdentity[remoteIdentity] ?: remoteUser.email?.let(localUsersByIdentity::get)

                    UserEntity(
                        id = remoteUser.id,
                        name = remoteUser.name.orFallback("User"),
                        email = remoteUser.email.orFallback(localUser?.email ?: remoteIdentity.orFallback("")),
                        loginId = remoteUser.loginId ?: localUser?.loginId ?: remoteUser.email,
                        phoneNumber = remoteUser.phoneNumber ?: localUser?.phoneNumber,
                        googleEmail = remoteUser.googleEmail,
                        authProvider = remoteUser.authProvider ?: "PHONE",
                        whatsappNumber = remoteUser.whatsappNumber ?: localUser?.whatsappNumber ?: "",
                        role = remoteUser.role ?: localUser?.role ?: "OWNER",
                        isActive = remoteUser.isActive ?: true,
                        tokenInvalidatedAt = remoteUser.tokenInvalidatedAt ?: localUser?.tokenInvalidatedAt,
                        createdAt = remoteUser.createdAt ?: System.currentTimeMillis(),
                        restaurantId = remoteUser.restaurantId ?: 0L,
                        deviceId = remoteUser.deviceId.orFallback(""),
                        isSynced = true,
                        serverId = remoteUser.serverId,
                        serverUpdatedAt = remoteUser.serverUpdatedAt ?: 0L
                    )
                }
            )
        }

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

        if (masterData.menuItems.isNotEmpty()) {
            menuDao.insertSyncedMenuItems(
                masterData.menuItems.map { remoteMenuItem ->
                    // categoryIdMap keys are server IDs (Long). Only look up via serverCategoryId.
                    // Falling back to categoryId (a foreign device's local ID) would look up the
                    // wrong key and silently link the item to a wrong or non-existent category.
                    val localCategoryId = remoteMenuItem.serverCategoryId?.let { serverId ->
                        categoryIdMap[serverId] ?: serverId
                    } ?: remoteMenuItem.categoryId

                    MenuItemEntity(
                        id = remoteMenuItem.id,
                        categoryId = localCategoryId,
                        name = remoteMenuItem.name.orFallback("Unnamed Item"),
                        basePrice = remoteMenuItem.basePrice.toSafeAmount(),
                        foodType = remoteMenuItem.foodType.orFallback("veg"),
                        description = remoteMenuItem.description,
                        isAvailable = remoteMenuItem.isAvailable ?: true,
                        currentStock = remoteMenuItem.currentStock,
                        lowStockThreshold = remoteMenuItem.lowStockThreshold,
                        barcode = remoteMenuItem.barcode,
                        createdAt = remoteMenuItem.createdAt ?: System.currentTimeMillis(),
                        restaurantId = remoteMenuItem.restaurantId ?: 0L,
                        deviceId = remoteMenuItem.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteMenuItem.updatedAt,
                        isDeleted = remoteMenuItem.isDeleted ?: false,
                        serverId = remoteMenuItem.serverId,
                        serverUpdatedAt = remoteMenuItem.serverUpdatedAt ?: 0L
                    )
                }
            )
        }

        // Fetch menu item ID mapping
        val menuItemIdMap = menuDao.getAllMenuItemServerIds().associate { it.serverId to it.id }

        if (masterData.itemVariants.isNotEmpty()) {
            menuDao.insertSyncedItemVariants(
                masterData.itemVariants.map { remoteVariant ->
                    val localMenuItemId = remoteVariant.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteVariant.menuItemId

                    ItemVariantEntity(
                        id = remoteVariant.id,
                        menuItemId = localMenuItemId,
                        variantName = remoteVariant.variantName.orFallback("Default"),
                        price = remoteVariant.price.toSafeAmount(),
                        isAvailable = remoteVariant.isAvailable ?: true,
                        sortOrder = remoteVariant.sortOrder ?: 0,
                        currentStock = remoteVariant.currentStock,
                        lowStockThreshold = remoteVariant.lowStockThreshold,
                        restaurantId = remoteVariant.restaurantId ?: 0L,
                        deviceId = remoteVariant.deviceId.orFallback(""),
                        isSynced = true,
                        updatedAt = remoteVariant.updatedAt,
                        isDeleted = remoteVariant.isDeleted ?: false,
                        serverId = remoteVariant.serverId,
                        serverUpdatedAt = remoteVariant.serverUpdatedAt ?: 0L
                    )
                }
            )
        }

        // Fetch variant ID mapping
        val variantIdMap = menuDao.getAllVariantServerIds().associate { it.serverId to it.id }

        if (masterData.stockLogs.isNotEmpty()) {
            inventoryDao.deleteAllSyncedStockLogs()
            inventoryDao.insertSyncedStockLogs(
                masterData.stockLogs.map { remoteStockLog ->
                    val localMenuItemId = remoteStockLog.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteStockLog.menuItemId
                    val localVariantId = remoteStockLog.serverVariantId?.let { serverId ->
                        variantIdMap[serverId] ?: serverId
                    } ?: remoteStockLog.variantId

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
            )
        }

        if (masterData.bills.isNotEmpty()) {
            billDao.insertSyncedBills(
                masterData.bills.map { remoteBill ->
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
                        createdBy = remoteBill.createdBy?.toLong(),
                        createdAt = remoteBill.createdAt ?: System.currentTimeMillis(),
                        paidAt = remoteBill.paidAt,
                        isSynced = true,
                        updatedAt = remoteBill.updatedAt ?: System.currentTimeMillis(),
                        isDeleted = remoteBill.isDeleted ?: false,
                        serverId = remoteBill.serverId,
                        serverUpdatedAt = remoteBill.serverUpdatedAt ?: 0L
                    )
                }
            )
        }

        // Get mapping of serverId to localId for bills to ensure items are linked correctly
        val billServerIdMap = billDao.getAllBillServerIds().associate { it.serverId to it.id }

        if (masterData.billItems.isNotEmpty()) {
            billDao.deleteAllSyncedBillItems()
            billDao.insertSyncedBillItems(
                masterData.billItems.map { remoteBillItem ->
                    val localBillId = remoteBillItem.serverBillId?.let { serverId ->
                        billServerIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.billId
                    val localMenuItemId = remoteBillItem.serverMenuItemId?.let { serverId ->
                        menuItemIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.menuItemId
                    val localVariantId = remoteBillItem.serverVariantId?.let { serverId ->
                        variantIdMap[serverId] ?: serverId
                    } ?: remoteBillItem.variantId
                    
                    BillItemEntity(
                        id = 0, // Let SQLite generate local ID
                        billId = localBillId,
                        menuItemId = localMenuItemId,
                        itemName = remoteBillItem.itemName.orFallback("Unnamed Item"),
                        variantId = localVariantId,
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
            )
        }

        if (masterData.billPayments.isNotEmpty()) {
            billDao.deleteAllSyncedBillPayments()
            billDao.insertSyncedBillPayments(
                masterData.billPayments.map { remoteBillPayment ->
                    val localBillId = remoteBillPayment.serverBillId?.let { serverId ->
                        billServerIdMap[serverId] ?: serverId
                    } ?: remoteBillPayment.billId

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
            )
        }
    } // end withTransaction
}
