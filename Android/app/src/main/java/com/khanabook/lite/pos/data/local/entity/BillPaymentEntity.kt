package com.khanabook.lite.pos.data.local.entity


import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "bill_payments",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bill_id"])]
)
data class BillPaymentEntity(
    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @SerializedName("billId")
    @ColumnInfo(name = "bill_id")
    val billId: Long,
    @SerializedName("paymentMode")
    @ColumnInfo(name = "payment_mode")
    val paymentMode: String, 
    @SerializedName("amount")
    val amount: String,

    @SerializedName("createdAt")
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("restaurantId")
    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @SerializedName("deviceId")
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @SerializedName("isSynced")
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @SerializedName("updatedAt")
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
,
    @SerializedName("isDeleted")
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverBillId") @ColumnInfo(name = "server_bill_id") val serverBillId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L,

    // Gateway fields — populated only for Easebuzz-verified payments. Manual payments leave these null.
    @SerializedName("gatewayTxnId") @ColumnInfo(name = "gateway_txn_id") val gatewayTxnId: String? = null,
    @SerializedName("gatewayStatus") @ColumnInfo(name = "gateway_status") val gatewayStatus: String? = null,
    @SerializedName("verifiedBy") @ColumnInfo(name = "verified_by", defaultValue = "'manual'") val verifiedBy: String = "manual"
)


