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
            Index(value = ["daily_order_id"])
        ]
)
data class BillEntity(
        @SerializedName("localId") @PrimaryKey(autoGenerate = true) val id: Long = 0,
        @SerializedName("restaurantId")
        @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
        @SerializedName("deviceId")
        @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
        @SerializedName("dailyOrderId")
        @ColumnInfo(name = "daily_order_id") val dailyOrderId: Long,
        @SerializedName("dailyOrderDisplay")
        @ColumnInfo(name = "daily_order_display") val dailyOrderDisplay: String, 
        @SerializedName("lifetimeOrderId")
        @ColumnInfo(name = "lifetime_order_id") val lifetimeOrderId: Long, 
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
        val paymentMode:
                String, 
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
        @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
,
    @SerializedName("isDeleted")
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)
