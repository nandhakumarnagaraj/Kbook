package com.khanabook.lite.pos.feature.sync.data
import com.khanabook.lite.pos.feature.printing.data.*
import com.khanabook.lite.pos.feature.inventory.data.StockLogEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.UserEntity
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillPaymentEntity


import com.khanabook.lite.pos.core.util.AppConstants

import com.khanabook.lite.pos.feature.notifications.data.NotificationEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity

/**
 * Entity → SyncDto mappers.
 *
 * These are the ONLY place where Room entity fields are referenced for network
 * serialization. If a Room column changes, the compiler flags it HERE — no more
 * silent JSON breakage. The DTO classes remain the stable API contract.
 *
 * Mapping convention:
 *   - Entity.id         → DTO.id        (Room primary key = device-local id)
 *   - DTO.localId       = entity.id     (server uses localId to de-dupe per device)
 *   - DTO.serverId      = entity.serverId (null until first successful sync round-trip)
 *   - Fields not in the DTO (isSynced, Room-only state) are intentionally dropped.
 */

fun BillEntity.toSyncDto(serverCreatedBy: Long? = null) = BillSyncDto(
    localDbId       = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    terminalId      = terminalId,
    createdTerminalId = createdTerminalId,
    createdDeviceId = createdDeviceId,
    currentOwnerTerminalId = currentOwnerTerminalId,
    localId         = id,
    serverId        = serverId,
    dailyOrderId    = dailyOrderId,
    dailyOrderDisplay = dailyOrderDisplay,
    lifetimeOrderId = lifetimeOrderId,
    terminalSeries  = terminalSeries,
    financialYear   = financialYear,
    invoiceSeries   = invoiceSeries,
    invoiceSequence = invoiceSequence,
    invoiceNumber   = invoiceNumber,
    orderType       = orderType,
    sourceChannel   = sourceChannel,
    customerName    = customerName,
    customerWhatsapp = customerWhatsapp,
    subtotal        = subtotal,
    gstPercentage   = gstPercentage,
    cgstAmount      = cgstAmount,
    sgstAmount      = sgstAmount,
    customTaxAmount = customTaxAmount,
    totalAmount     = totalAmount,
    paymentMode     = paymentMode,
    partAmount1     = partAmount1,
    partAmount2     = partAmount2,
    paymentStatus   = paymentStatus,
    orderStatus     = orderStatus,
    cancelReason    = cancelReason,
    createdBy       = serverCreatedBy ?: createdBy,
    createdAt       = createdAt,
    paidAt          = paidAt,
    updatedAt       = updatedAt,
    isDeleted       = isDeleted,
    lastResetDate   = lastResetDate,
    serverUpdatedAt = serverUpdatedAt,
    version         = version,
    operationId     = operationId,
    // refundAmount intentionally omitted — server-owned field.
    // GenericSyncService ignores the pushed value; server state is authoritative.
    publicToken     = publicToken,
)

fun BillItemEntity.toSyncDto() = BillItemSyncDto(
    localDbId       = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    terminalId      = null,
    localId         = id,
    serverId        = serverId,
    billId          = billId,
    serverBillId    = serverBillId,
    menuItemId      = menuItemId ?: if (serverMenuItemId == null) 0L else null,
    serverMenuItemId = serverMenuItemId,
    itemName        = itemName,
    variantId       = variantId,
    serverVariantId = serverVariantId,
    variantName     = variantName,
    price           = price,
    quantity        = quantity,
    itemTotal       = itemTotal,
    specialInstruction = specialInstruction,
    updatedAt       = updatedAt,
    isDeleted       = isDeleted,
    serverUpdatedAt = serverUpdatedAt,
    version         = 0L,
    sentToKot       = sentToKot,
)

fun BillPaymentEntity.toSyncDto() = BillPaymentSyncDto(
    localDbId       = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    terminalId      = terminalId,
    localId         = id,
    serverId        = serverId,
    billId          = billId,
    serverBillId    = serverBillId,
    paymentMode     = paymentMode,
    amount          = amount,
    gatewayTxnId    = gatewayTxnId,
    gatewayStatus   = gatewayStatus,
    verifiedBy      = verifiedBy,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
    isDeleted       = isDeleted,
    serverUpdatedAt = serverUpdatedAt,
    operationId     = operationId,
    version         = version,
)

fun CategoryEntity.toSyncDto() = CategorySyncDto(
    localDbId       = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    localId         = id,
    serverId        = serverId,
    name            = name,
    isVeg           = isVeg,
    sortOrder       = sortOrder,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
    isDeleted       = isDeleted,
    serverUpdatedAt = serverUpdatedAt,
)

fun MenuItemEntity.toSyncDto() = MenuItemSyncDto(
    localDbId        = id,
    restaurantId     = restaurantId,
    deviceId         = deviceId,
    localId          = id,
    serverId         = serverId,
    categoryId       = categoryId,
    serverCategoryId = serverCategoryId,
    name             = name,
    basePrice        = basePrice,
    foodType         = foodType,
    description      = description,
    isAvailable      = isAvailable,
    // currentStock / lowStockThreshold are String in the entity but Int? in DTO.
    // Parse to Int safely; null if not parseable (server treats null as "untracked").
    currentStock     = currentStock.toIntOrNull(),
    lowStockThreshold = lowStockThreshold.toIntOrNull(),
    barcode          = barcode,
    createdAt        = createdAt,
    updatedAt        = updatedAt,
    isDeleted        = isDeleted,
    serverUpdatedAt  = serverUpdatedAt,
    permissionRevisionAtCreation = permissionRevisionAtCreation,
    changedFields             = changedFields,
)

fun ItemVariantEntity.toSyncDto() = ItemVariantSyncDto(
    localDbId        = id,
    restaurantId     = restaurantId,
    deviceId         = deviceId,
    localId          = id,
    serverId         = serverId,
    menuItemId       = menuItemId,
    serverMenuItemId = serverMenuItemId,
    variantName      = variantName,
    price            = price,
    isAvailable      = isAvailable,
    sortOrder        = sortOrder,
    currentStock     = currentStock.toIntOrNull(),
    lowStockThreshold = lowStockThreshold.toIntOrNull(),
    updatedAt        = updatedAt,
    isDeleted        = isDeleted,
    serverUpdatedAt  = serverUpdatedAt,
)

fun RestaurantProfileEntity.toSyncDto() = RestaurantProfileSyncDto(
    localDbId        = id.toLong(),
    restaurantId     = restaurantId,
    deviceId         = deviceId,
    localId          = id.toLong(),
    serverId         = serverId,
    shopName         = shopName.orEmpty(),
    shopAddress      = shopAddress.orEmpty(),
    whatsappNumber   = whatsappNumber.orEmpty(),
    email            = email.orEmpty(),
    logoPath         = logoPath,
    logoUrl          = logoUrl,
    logoVersion      = logoVersion,
    fssaiNumber      = fssaiNumber.orEmpty(),
    fssaiExpiryDate  = fssaiExpiryDate,
    country          = country.orEmpty(),
    currency         = currency.orEmpty(),
    timezone         = AppConstants.DEFAULT_TIMEZONE,
    gstEnabled       = gstEnabled,
    gstin            = gstin.orEmpty(),
    isTaxInclusive   = isTaxInclusive,
    gstPercentage    = gstPercentage,
    customTaxName    = customTaxName.orEmpty(),
    customTaxNumber  = customTaxNumber.orEmpty(),
    customTaxPercentage = customTaxPercentage,
    upiEnabled       = upiEnabled,
    upiQrPath        = upiQrPath,
    upiQrUrl         = upiQrUrl,
    upiQrVersion     = upiQrVersion,
    upiHandle        = upiHandle.orEmpty(),
    upiMobile        = upiMobile.orEmpty(),
    cashEnabled      = cashEnabled,
    posEnabled       = posEnabled,
    printerEnabled   = printerEnabled,
    printerName      = printerName.orEmpty(),
    printerMac       = printerMac.orEmpty(),
    paperSize        = paperSize,
    autoPrintOnSuccess  = autoPrintOnSuccess,
    includeLogoInPrint  = includeLogoInPrint,
    dailyOrderCounter   = dailyOrderCounter,
    lifetimeOrderCounter = lifetimeOrderCounter,
    lastResetDate       = lastResetDate.orEmpty(),
    sessionTimeoutMinutes = sessionTimeoutMinutes,
    orderPaymentFlowMode = orderPaymentFlowMode,
    updatedAt        = updatedAt,
    isDeleted        = isDeleted,
    serverUpdatedAt  = serverUpdatedAt,
    kitchenPrinterEnabled   = kitchenPrinterEnabled,
    kitchenPrinterName      = kitchenPrinterName,
    kitchenPrinterMac       = kitchenPrinterMac,
    kitchenPrinterPaperSize = kitchenPrinterPaperSize,
    invoiceFooter           = invoiceFooter,
    reviewUrl               = reviewUrl,
    changedFields           = changedFields,
)

fun UserEntity.toSyncDto() = UserSyncDto(
    localDbId       = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    localId         = id,
    serverId        = serverId,
    name            = name,
    email           = email,
    loginId         = loginId,
    phoneNumber     = phoneNumber,
    googleEmail     = googleEmail,
    authProvider    = authProvider,
    whatsappNumber  = whatsappNumber.orEmpty(),
    role            = role,
    isActive        = isActive,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
    isDeleted       = isDeleted,
    serverUpdatedAt = serverUpdatedAt,
)

fun StockLogEntity.toSyncDto() = StockLogSyncDto(
    localDbId        = id,
    restaurantId     = restaurantId,
    deviceId         = deviceId,
    localId          = id,
    serverId         = serverId,
    menuItemId       = menuItemId,
    serverMenuItemId = serverMenuItemId,
    variantId        = variantId ?: 0L,
    serverVariantId  = serverVariantId,
    delta            = delta?.toDoubleOrNull() ?: 0.0,
    reason           = reason ?: "",
    createdAt        = createdAt,
    updatedAt        = updatedAt,
    isDeleted        = isDeleted,
    serverUpdatedAt  = serverUpdatedAt,
)

fun com.khanabook.lite.pos.feature.printing.data.KotEventEntity.toSyncDto(restaurantId: Long) =
    com.khanabook.lite.pos.feature.sync.data.KotEventSyncDto(
        restaurantId     = restaurantId,
        deviceId         = originatingDeviceId,
        terminalId       = originTerminalId,
        terminalSeries   = null,
        publicToken      = publicToken,
        billPublicToken  = billPublicToken,
        kotRevision      = kotRevision,
        eventType        = eventType,
        itemSnapshotJson = itemSnapshotJson,
        originatingDeviceId = originatingDeviceId,
        originTerminalId = originTerminalId,
        originDeviceId   = originDeviceId,
        eventToken       = eventToken,
        eventVersion     = eventVersion,
        isPrinted        = isPrinted,
        createdAt        = createdAt,
        updatedAt        = createdAt,
    )

fun com.khanabook.lite.pos.feature.sync.data.KotEventSyncDto.toEntity() =
    com.khanabook.lite.pos.feature.printing.data.KotEventEntity(
        publicToken = publicToken,
        kotRevision = kotRevision,
        eventType = eventType,
        itemSnapshotJson = itemSnapshotJson.orEmpty(),
        originatingDeviceId = originatingDeviceId,
        originTerminalId = originTerminalId,
        originDeviceId = originDeviceId,
        eventToken = eventToken,
        eventVersion = eventVersion,
        isPrinted = isPrinted,
        createdAt = createdAt,
    )
