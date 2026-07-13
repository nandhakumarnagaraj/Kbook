package com.khanabook.lite.pos.data.local.entity

import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
        tableName = "bills",
        foreignKeys =
                [
                        ForeignKey(
                                entity = UserEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["created_by"],
                                onDelete = ForeignKey.SET_NULL
                        )],
        indices = [
            Index(value = ["created_by"]),
            Index(value = ["order_status"]),
            Index(value = ["created_at"]),
            Index(value = ["daily_order_id"]),
            Index(
                value = ["restaurant_id", "public_token"],
                name = "index_bills_restaurant_public_token",
                unique = true
            ),
            Index(value = ["restaurant_id", "terminal_id", "created_at"]),
            Index(value = ["restaurant_id", "financial_year", "invoice_series", "invoice_sequence"])
        ]
)
data class BillEntity(
        @SerializedName("localId") @PrimaryKey(autoGenerate = true) val id: Long = 0,
        @SerializedName("restaurantId")
        @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
        @SerializedName("deviceId")
        @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
        @SerializedName("terminalId")
        @ColumnInfo(name = "terminal_id", defaultValue = "NULL") val terminalId: String? = null,
        @SerializedName("createdTerminalId")
        @ColumnInfo(name = "created_terminal_id", defaultValue = "NULL") val createdTerminalId: String? = null,
        @SerializedName("createdDeviceId")
        @ColumnInfo(name = "created_device_id", defaultValue = "NULL") val createdDeviceId: String? = null,
        @SerializedName("dailyOrderId")
        @ColumnInfo(name = "daily_order_id") val dailyOrderId: Long,
        @SerializedName("dailyOrderDisplay")
        @ColumnInfo(name = "daily_order_display") val dailyOrderDisplay: String,
        @SerializedName("lifetimeOrderId")
        @ColumnInfo(name = "lifetime_order_id") val lifetimeOrderId: Long?,
        @SerializedName("orderType")
        @ColumnInfo(name = "order_type", defaultValue = "order")
        val orderType: String = "order",
        @SerializedName("customerName")
        @ColumnInfo(name = "customer_name") val customerName: String? = null,
        @SerializedName("customerWhatsapp")
        @ColumnInfo(name = "customer_whatsapp") val customerWhatsapp: String? = null,
        @SerializedName("subtotal")
        val subtotal: String,
        @SerializedName("gstPercentage")
        @ColumnInfo(name = "gst_percentage", defaultValue = "'0.0'") val gstPercentage: String = "0.0",
        @SerializedName("cgstAmount")
        @ColumnInfo(name = "cgst_amount", defaultValue = "'0.0'") val cgstAmount: String = "0.0",
        @SerializedName("sgstAmount")
        @ColumnInfo(name = "sgst_amount", defaultValue = "'0.0'") val sgstAmount: String = "0.0",
        @SerializedName("customTaxAmount")
        @ColumnInfo(name = "custom_tax_amount", defaultValue = "'0.0'")
        val customTaxAmount: String = "0.0",
        @SerializedName("totalAmount")
        @ColumnInfo(name = "total_amount") val totalAmount: String,
        @SerializedName("paymentMode")
        @ColumnInfo(name = "payment_mode")
        val paymentMode: String,
        @SerializedName("sourceChannel")
        @ColumnInfo(name = "source_channel", defaultValue = "''")
        val sourceChannel: String = "",
        @SerializedName("partAmount1")
        @ColumnInfo(name = "part_amount_1", defaultValue = "'0.0'") val partAmount1: String = "0.0",
        @SerializedName("partAmount2")
        @ColumnInfo(name = "part_amount_2", defaultValue = "'0.0'") val partAmount2: String = "0.0",
        @SerializedName("paymentStatus")
        @ColumnInfo(name = "payment_status") val paymentStatus: String,
        @SerializedName("orderStatus")
        @ColumnInfo(name = "order_status")
        val orderStatus: String,
        @SerializedName("createdBy")
        @ColumnInfo(name = "created_by") val createdBy: Long? = null,
        @SerializedName("createdAt")
        @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
        @SerializedName("paidAt")
        @ColumnInfo(name = "paid_at") val paidAt: Long? = null,
        @SerializedName("lastResetDate")
        @ColumnInfo(name = "last_reset_date", defaultValue = "''") val lastResetDate: String = "",

        @SerializedName("isSynced")
        @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
        @SerializedName("updatedAt")
        @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerializedName("isDeleted")
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L,
    @SerializedName("cancelReason")
    @ColumnInfo(name = "cancel_reason", defaultValue = "") val cancelReason: String = "",
    @SerializedName("publicToken")
    @ColumnInfo(name = "public_token") val publicToken: String? = null,
    @SerializedName("ownerUserId")
    @ColumnInfo(name = "owner_user_id", defaultValue = "NULL") val ownerUserId: Long? = null,
    @SerializedName("ownerRestaurantId")
    @ColumnInfo(name = "owner_restaurant_id", defaultValue = "NULL") val ownerRestaurantId: Long? = null,
    @ColumnInfo(name = "sync_status", defaultValue = "'pending'") val syncStatus: String = "pending",
    @ColumnInfo(name = "sync_failure_reason") val syncFailureReason: String? = null,
    @ColumnInfo(name = "sync_failed_at") val syncFailedAt: Long? = null,
    @SerializedName("terminalSeries")
    @ColumnInfo(name = "terminal_series", defaultValue = "NULL") val terminalSeries: String? = null,
    @SerializedName("financialYear")
    @ColumnInfo(name = "financial_year", defaultValue = "NULL") val financialYear: String? = null,
    @SerializedName("invoiceSeries")
    @ColumnInfo(name = "invoice_series", defaultValue = "NULL") val invoiceSeries: String? = null,
    @SerializedName("invoiceSequence")
    @ColumnInfo(name = "invoice_sequence", defaultValue = "NULL") val invoiceSequence: Long? = null,
    @SerializedName("invoiceNumber")
    @ColumnInfo(name = "invoice_number", defaultValue = "NULL") val invoiceNumber: String? = null,
    // Server-owned. Null means no refund has been recorded. Never written by Android push.
    @SerializedName("refundAmount")
    @ColumnInfo(name = "refund_amount", defaultValue = "NULL") val refundAmount: String? = null,
    @SerializedName("currentOwnerTerminalId")
    @ColumnInfo(name = "current_owner_terminal_id", defaultValue = "NULL") val currentOwnerTerminalId: String? = null,
    @SerializedName("version")
    @ColumnInfo(name = "version", defaultValue = "0") val version: Long = 0L,
    @SerializedName("lockStatus")
    @ColumnInfo(name = "lock_status", defaultValue = "'unlocked'") val lockStatus: String = "unlocked",
    @SerializedName("operationId")
    @ColumnInfo(name = "operation_id", defaultValue = "NULL") val operationId: String? = null
)

fun BillEntity.getInvoiceNumberDisplay(): String {
    if (!invoiceNumber.isNullOrBlank()) return invoiceNumber
    if (!terminalSeries.isNullOrBlank() && invoiceSequence != null) {
        val displaySeries = terminalSeries.first().uppercaseChar()
        return "$displaySeries${invoiceSequence.toString().padStart(2, '0')}"
    }
    if (lifetimeOrderId != null && lifetimeOrderId > 0) {
        return "INV$lifetimeOrderId"
    }
    val prefix = terminalSeries ?: "DRAFT"
    return "$prefix-LOC-$id"
}
