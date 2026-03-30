package com.khanabook.lite.pos.data.local.entity

import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(tableName = "restaurant_profile")
data class RestaurantProfileEntity(
    @SerializedName("localId") @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "shop_name")
    val shopName: String? = null,
    @ColumnInfo(name = "shop_address")
    val shopAddress: String? = null,
    @ColumnInfo(name = "whatsapp_number")
    val whatsappNumber: String? = null,
    val email: String? = null,
    @ColumnInfo(name = "logo_path")
    val logoPath: String? = null,
    @ColumnInfo(name = "fssai_number")
    val fssaiNumber: String? = null,
    @ColumnInfo(name = "email_invoice_consent", defaultValue = "0")
    val emailInvoiceConsent: Boolean = false,

    
    @ColumnInfo(defaultValue = "India")
    val country: String? = "India",
    @ColumnInfo(name = "gst_enabled", defaultValue = "0")
    val gstEnabled: Boolean = false,
    val gstin: String? = null,
    @ColumnInfo(name = "is_tax_inclusive", defaultValue = "0")
    val isTaxInclusive: Boolean = false,
    @ColumnInfo(name = "gst_percentage", defaultValue = "0.0")
    val gstPercentage: Double = 0.0,
    @ColumnInfo(name = "custom_tax_name")
    val customTaxName: String? = null,
    @ColumnInfo(name = "custom_tax_number")
    val customTaxNumber: String? = null,
    @ColumnInfo(name = "custom_tax_percentage", defaultValue = "0.0")
    val customTaxPercentage: Double = 0.0,

    
    @ColumnInfo(defaultValue = "INR")
    val currency: String? = "INR",
    @ColumnInfo(name = "upi_enabled", defaultValue = "0")
    val upiEnabled: Boolean = false,
    @ColumnInfo(name = "upi_qr_path")
    val upiQrPath: String? = null,
    @ColumnInfo(name = "upi_handle")
    val upiHandle: String? = null,
    @ColumnInfo(name = "upi_mobile")
    val upiMobile: String? = null,
    @ColumnInfo(name = "cash_enabled", defaultValue = "1")
    val cashEnabled: Boolean = true,
    @ColumnInfo(name = "pos_enabled", defaultValue = "0")
    val posEnabled: Boolean = false,
    @ColumnInfo(name = "zomato_enabled", defaultValue = "0")
    val zomatoEnabled: Boolean = false,
    @ColumnInfo(name = "swiggy_enabled", defaultValue = "0")
    val swiggyEnabled: Boolean = false,
    @ColumnInfo(name = "own_website_enabled", defaultValue = "0")
    val ownWebsiteEnabled: Boolean = false,

    
    @ColumnInfo(name = "printer_enabled", defaultValue = "0")
    val printerEnabled: Boolean = false,
    @ColumnInfo(name = "printer_name")
    val printerName: String? = null,
    @ColumnInfo(name = "printer_mac")
    val printerMac: String? = null,
    @ColumnInfo(name = "paper_size", defaultValue = "58mm")
    val paperSize: String = "58mm",
    @ColumnInfo(name = "auto_print_on_success", defaultValue = "0")
    val autoPrintOnSuccess: Boolean = false,
    @ColumnInfo(name = "include_logo_in_print", defaultValue = "1")
    val includeLogoInPrint: Boolean = true,
    @ColumnInfo(name = "print_customer_whatsapp", defaultValue = "1")
    val printCustomerWhatsapp: Boolean = true,

    
    @ColumnInfo(name = "daily_order_counter", defaultValue = "0")
    val dailyOrderCounter: Long = 0,
    @ColumnInfo(name = "lifetime_order_counter", defaultValue = "0")
    val lifetimeOrderCounter: Long = 0,
    @ColumnInfo(name = "last_reset_date")
    val lastResetDate: String? = null, 
    @ColumnInfo(name = "session_timeout_minutes", defaultValue = "30")
    val sessionTimeoutMinutes: Int = 30,

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
,
    @ColumnInfo(name = "timezone", defaultValue = "Asia/Kolkata")
    val timezone: String? = "Asia/Kolkata",

    @ColumnInfo(name = "review_url")
    val reviewUrl: String? = null,

    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "show_branding", defaultValue = "1")
    val showBranding: Boolean = true,
    @ColumnInfo(name = "mask_customer_phone", defaultValue = "1")
    val maskCustomerPhone: Boolean = true,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)


