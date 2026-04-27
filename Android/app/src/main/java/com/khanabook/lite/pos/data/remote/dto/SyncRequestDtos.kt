package com.khanabook.lite.pos.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────────────────────
// FIX #1 — Sync Push DTOs
// Previously KhanaBookApi sent raw Room Entity objects directly as HTTP body.
// This created invisible coupling: a Room annotation change or column rename
// would silently mutate the JSON sent to the server, breaking the sync contract.
//
// Each DTO below is the STABLE network contract for a sync push or pull.
// MasterSyncProcessor maps Entity → DTO before calling any push endpoint.
// Room schema and network schema are now independently versioned.
//
// FIX #3 — Pull Response versioning
// These same classes are used as pull response targets. Moshi's lenient config
// (set in NetworkModule) ignores unknown JSON fields, so older app versions
// survive new server fields being added without crashing.
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class BillSyncDto(
    @Json(name = "id")           val id: Long,
    @Json(name = "restaurantId") val restaurantId: Long,
    @Json(name = "deviceId")     val deviceId: String,
    @Json(name = "localId")      val localId: Long,
    @Json(name = "serverId")     val serverId: Long?,
    @Json(name = "dailyOrderId")       val dailyOrderId: Long,
    @Json(name = "dailyOrderDisplay")  val dailyOrderDisplay: String,
    @Json(name = "lifetimeOrderId")    val lifetimeOrderId: Long,
    @Json(name = "orderType")          val orderType: String,
    @Json(name = "customerName")       val customerName: String?,
    @Json(name = "customerWhatsapp")   val customerWhatsapp: String?,
    @Json(name = "subtotal")           val subtotal: String,
    @Json(name = "gstPercentage")      val gstPercentage: String,
    @Json(name = "cgstAmount")         val cgstAmount: String,
    @Json(name = "sgstAmount")         val sgstAmount: String,
    @Json(name = "customTaxAmount")    val customTaxAmount: String,
    @Json(name = "totalAmount")        val totalAmount: String,
    @Json(name = "paymentMode")        val paymentMode: String,
    @Json(name = "partAmount1")        val partAmount1: String,
    @Json(name = "partAmount2")        val partAmount2: String,
    @Json(name = "paymentStatus")      val paymentStatus: String,
    @Json(name = "orderStatus")        val orderStatus: String,
    @Json(name = "cancelReason")       val cancelReason: String,
    @Json(name = "createdBy")          val createdBy: Long?,
    @Json(name = "createdAt")          val createdAt: Long,
    @Json(name = "paidAt")             val paidAt: Long?,
    @Json(name = "updatedAt")          val updatedAt: Long,
    @Json(name = "isDeleted")          val isDeleted: Boolean,
    @Json(name = "lastResetDate")      val lastResetDate: String,
    @Json(name = "serverUpdatedAt")    val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class BillItemSyncDto(
    @Json(name = "id")           val id: Long,
    @Json(name = "restaurantId") val restaurantId: Long,
    @Json(name = "deviceId")     val deviceId: String,
    @Json(name = "localId")      val localId: Long,
    @Json(name = "serverId")     val serverId: Long?,
    @Json(name = "billId")           val billId: Long,
    @Json(name = "serverBillId")     val serverBillId: Long?,
    @Json(name = "menuItemId")       val menuItemId: Long?,
    @Json(name = "serverMenuItemId") val serverMenuItemId: Long?,
    @Json(name = "itemName")         val itemName: String,
    @Json(name = "variantId")        val variantId: Long?,
    @Json(name = "serverVariantId")  val serverVariantId: Long?,
    @Json(name = "variantName")      val variantName: String?,
    @Json(name = "price")            val price: String,
    @Json(name = "quantity")         val quantity: Int,
    @Json(name = "itemTotal")        val itemTotal: String,
    @Json(name = "specialInstruction") val specialInstruction: String?,
    @Json(name = "updatedAt")        val updatedAt: Long,
    @Json(name = "isDeleted")        val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt")  val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class BillPaymentSyncDto(
    @Json(name = "id")           val id: Long,
    @Json(name = "restaurantId") val restaurantId: Long,
    @Json(name = "deviceId")     val deviceId: String,
    @Json(name = "localId")      val localId: Long,
    @Json(name = "serverId")     val serverId: Long?,
    @Json(name = "billId")       val billId: Long,
    @Json(name = "serverBillId") val serverBillId: Long?,
    @Json(name = "paymentMode")  val paymentMode: String,
    @Json(name = "amount")       val amount: String,
    @Json(name = "createdAt")    val createdAt: Long,
    @Json(name = "updatedAt")    val updatedAt: Long,
    @Json(name = "isDeleted")    val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt") val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class CategorySyncDto(
    @Json(name = "id")           val id: Long,
    @Json(name = "restaurantId") val restaurantId: Long,
    @Json(name = "deviceId")     val deviceId: String,
    @Json(name = "localId")      val localId: Long,
    @Json(name = "serverId")     val serverId: Long?,
    @Json(name = "name")         val name: String,
    @Json(name = "isVeg")        val isVeg: Boolean?,
    @Json(name = "sortOrder")    val sortOrder: Int,
    @Json(name = "createdAt")    val createdAt: Long,
    @Json(name = "updatedAt")    val updatedAt: Long,
    @Json(name = "isDeleted")    val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt") val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class MenuItemSyncDto(
    @Json(name = "id")               val id: Long,
    @Json(name = "restaurantId")     val restaurantId: Long,
    @Json(name = "deviceId")         val deviceId: String,
    @Json(name = "localId")          val localId: Long,
    @Json(name = "serverId")         val serverId: Long?,
    @Json(name = "categoryId")       val categoryId: Long,
    @Json(name = "serverCategoryId") val serverCategoryId: Long?,
    @Json(name = "name")             val name: String,
    @Json(name = "basePrice")        val basePrice: String,
    @Json(name = "foodType")         val foodType: String,
    @Json(name = "description")      val description: String?,
    @Json(name = "isAvailable")      val isAvailable: Boolean,
    @Json(name = "currentStock")     val currentStock: Int?,
    @Json(name = "lowStockThreshold") val lowStockThreshold: Int?,
    @Json(name = "barcode")          val barcode: String?,
    @Json(name = "createdAt")        val createdAt: Long,
    @Json(name = "updatedAt")        val updatedAt: Long,
    @Json(name = "isDeleted")        val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt")  val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class ItemVariantSyncDto(
    @Json(name = "id")               val id: Long,
    @Json(name = "restaurantId")     val restaurantId: Long,
    @Json(name = "deviceId")         val deviceId: String,
    @Json(name = "localId")          val localId: Long,
    @Json(name = "serverId")         val serverId: Long?,
    @Json(name = "menuItemId")       val menuItemId: Long,
    @Json(name = "serverMenuItemId") val serverMenuItemId: Long?,
    @Json(name = "variantName")      val variantName: String,
    @Json(name = "price")            val price: String,
    @Json(name = "isAvailable")      val isAvailable: Boolean,
    @Json(name = "sortOrder")        val sortOrder: Int,
    @Json(name = "currentStock")     val currentStock: Int?,
    @Json(name = "lowStockThreshold") val lowStockThreshold: Int?,
    @Json(name = "updatedAt")        val updatedAt: Long,
    @Json(name = "isDeleted")        val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt")  val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class StockLogSyncDto(
    @Json(name = "id")               val id: Long,
    @Json(name = "restaurantId")     val restaurantId: Long,
    @Json(name = "deviceId")         val deviceId: String,
    @Json(name = "localId")          val localId: Long,
    @Json(name = "serverId")         val serverId: Long?,
    @Json(name = "menuItemId")       val menuItemId: Long,
    @Json(name = "serverMenuItemId") val serverMenuItemId: Long?,
    @Json(name = "variantId")        val variantId: Long?,
    @Json(name = "serverVariantId")  val serverVariantId: Long?,
    @Json(name = "delta")            val delta: Int,
    @Json(name = "reason")           val reason: String,
    @Json(name = "createdAt")        val createdAt: Long,
    @Json(name = "updatedAt")        val updatedAt: Long,
    @Json(name = "isDeleted")        val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt")  val serverUpdatedAt: Long,
)

@JsonClass(generateAdapter = true)
data class RestaurantProfileSyncDto(
    @Json(name = "id")           val id: Long,
    @Json(name = "restaurantId") val restaurantId: Long,
    @Json(name = "deviceId")     val deviceId: String,
    @Json(name = "localId")      val localId: Long,
    @Json(name = "serverId")     val serverId: Long?,
    @Json(name = "shopName")     val shopName: String,
    @Json(name = "shopAddress")  val shopAddress: String,
    @Json(name = "whatsappNumber") val whatsappNumber: String,
    @Json(name = "email")        val email: String,
    @Json(name = "logoPath")     val logoPath: String?,
    @Json(name = "fssaiNumber")  val fssaiNumber: String,
    @Json(name = "country")      val country: String,
    @Json(name = "currency")     val currency: String,
    @Json(name = "timezone")     val timezone: String?,
    @Json(name = "gstEnabled")        val gstEnabled: Boolean,
    @Json(name = "gstin")             val gstin: String,
    @Json(name = "isTaxInclusive")    val isTaxInclusive: Boolean,
    @Json(name = "gstPercentage")     val gstPercentage: Double,
    @Json(name = "customTaxName")     val customTaxName: String,
    @Json(name = "customTaxNumber")   val customTaxNumber: String,
    @Json(name = "customTaxPercentage") val customTaxPercentage: Double,
    @Json(name = "upiEnabled")        val upiEnabled: Boolean,
    @Json(name = "upiQrPath")         val upiQrPath: String?,
    @Json(name = "upiHandle")         val upiHandle: String,
    @Json(name = "upiMobile")         val upiMobile: String,
    @Json(name = "cashEnabled")       val cashEnabled: Boolean,
    @Json(name = "posEnabled")        val posEnabled: Boolean,
    @Json(name = "printerEnabled")    val printerEnabled: Boolean,
    @Json(name = "printerName")       val printerName: String,
    @Json(name = "printerMac")        val printerMac: String,
    @Json(name = "paperSize")         val paperSize: String,
    @Json(name = "autoPrintOnSuccess") val autoPrintOnSuccess: Boolean,
    @Json(name = "includeLogoInPrint") val includeLogoInPrint: Boolean,
    @Json(name = "dailyOrderCounter")   val dailyOrderCounter: Long,
    @Json(name = "lifetimeOrderCounter") val lifetimeOrderCounter: Long,
    @Json(name = "lastResetDate")       val lastResetDate: String,
    @Json(name = "sessionTimeoutMinutes") val sessionTimeoutMinutes: Int,
    @Json(name = "updatedAt")          val updatedAt: Long,
    @Json(name = "isDeleted")          val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt")    val serverUpdatedAt: Long,
    @Json(name = "kitchenPrinterEnabled")   val kitchenPrinterEnabled: Boolean?,
    @Json(name = "kitchenPrinterName")      val kitchenPrinterName: String?,
    @Json(name = "kitchenPrinterMac")       val kitchenPrinterMac: String?,
    @Json(name = "kitchenPrinterPaperSize") val kitchenPrinterPaperSize: String?,
)

@JsonClass(generateAdapter = true)
data class UserSyncDto(
    @Json(name = "id")           val id: Long,
    @Json(name = "restaurantId") val restaurantId: Long,
    @Json(name = "deviceId")     val deviceId: String,
    @Json(name = "localId")      val localId: Long,
    @Json(name = "serverId")     val serverId: Long?,
    @Json(name = "name")         val name: String,
    @Json(name = "email")        val email: String,
    @Json(name = "loginId")      val loginId: String?,
    @Json(name = "phoneNumber")  val phoneNumber: String?,
    @Json(name = "googleEmail")  val googleEmail: String?,
    @Json(name = "authProvider") val authProvider: String,
    @Json(name = "whatsappNumber") val whatsappNumber: String,
    @Json(name = "role")         val role: String,
    @Json(name = "isActive")     val isActive: Boolean,
    @Json(name = "createdAt")    val createdAt: Long,
    @Json(name = "updatedAt")    val updatedAt: Long,
    @Json(name = "isDeleted")    val isDeleted: Boolean,
    @Json(name = "serverUpdatedAt") val serverUpdatedAt: Long,
)
