package com.khanabook.lite.pos.data.remote.dto

import com.khanabook.lite.pos.data.local.entity.*

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

fun BillEntity.toSyncDto() = BillSyncDto(
    id              = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    localId         = id,
    serverId        = serverId,
    dailyOrderId    = dailyOrderId,
    dailyOrderDisplay = dailyOrderDisplay,
    lifetimeOrderId = lifetimeOrderId,
    orderType       = orderType,
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
    createdBy       = createdBy,
    createdAt       = createdAt,
    paidAt          = paidAt,
    updatedAt       = updatedAt,
    isDeleted       = isDeleted,
    lastResetDate   = lastResetDate,
    serverUpdatedAt = serverUpdatedAt,
    refundAmount    = "0.00",
)

fun BillItemEntity.toSyncDto() = BillItemSyncDto(
    id              = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
    localId         = id,
    serverId        = serverId,
    billId          = billId,
    serverBillId    = serverBillId,
    menuItemId      = menuItemId,
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
)

fun BillPaymentEntity.toSyncDto() = BillPaymentSyncDto(
    id              = id,
    restaurantId    = restaurantId,
    deviceId        = deviceId,
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
)

fun CategoryEntity.toSyncDto() = CategorySyncDto(
    id              = id,
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
    id               = id,
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
)

fun ItemVariantEntity.toSyncDto() = ItemVariantSyncDto(
    id               = id,
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

fun StockLogEntity.toSyncDto() = StockLogSyncDto(
    id               = id,
    restaurantId     = restaurantId,
    deviceId         = deviceId,
    localId          = id,
    serverId         = serverId,
    menuItemId       = menuItemId,
    serverMenuItemId = serverMenuItemId,
    variantId        = variantId,
    serverVariantId  = serverVariantId,
    // delta is String in entity; DTO expects Int. Parse safely, default 0.
    delta            = delta.toIntOrNull() ?: 0,
    reason           = reason,
    createdAt        = createdAt,
    updatedAt        = updatedAt,
    isDeleted        = isDeleted,
    serverUpdatedAt  = serverUpdatedAt,
)

fun RestaurantProfileEntity.toSyncDto() = RestaurantProfileSyncDto(
    id               = id.toLong(),
    restaurantId     = restaurantId,
    deviceId         = deviceId,
    localId          = id.toLong(),
    serverId         = serverId,
    shopName         = shopName.orEmpty(),
    shopAddress      = shopAddress.orEmpty(),
    whatsappNumber   = whatsappNumber.orEmpty(),
    email            = email.orEmpty(),
    logoPath         = logoPath,
    fssaiNumber      = fssaiNumber.orEmpty(),
    country          = country.orEmpty(),
    currency         = currency.orEmpty(),
    timezone         = timezone,
    gstEnabled       = gstEnabled,
    gstin            = gstin.orEmpty(),
    isTaxInclusive   = isTaxInclusive,
    gstPercentage    = gstPercentage,
    customTaxName    = customTaxName.orEmpty(),
    customTaxNumber  = customTaxNumber.orEmpty(),
    customTaxPercentage = customTaxPercentage,
    upiEnabled       = upiEnabled,
    upiQrPath        = upiQrPath,
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
    updatedAt        = updatedAt,
    isDeleted        = isDeleted,
    serverUpdatedAt  = serverUpdatedAt,
    kitchenPrinterEnabled   = kitchenPrinterEnabled,
    kitchenPrinterName      = kitchenPrinterName,
    kitchenPrinterMac       = kitchenPrinterMac,
    kitchenPrinterPaperSize = kitchenPrinterPaperSize,
)

fun UserEntity.toSyncDto() = UserSyncDto(
    id              = id,
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
