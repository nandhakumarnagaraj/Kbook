package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "terminal_daily_counter",
    primaryKeys = ["restaurant_id", "terminal_id", "date"]
)
data class TerminalDailyCounterEntity(
    @SerializedName("restaurantId") @ColumnInfo(name = "restaurant_id") val restaurantId: Long,
    @SerializedName("terminalId") @ColumnInfo(name = "terminal_id") val terminalId: String,
    @SerializedName("date") @ColumnInfo(name = "date") val date: String,
    @SerializedName("dailyOrderCounter") @ColumnInfo(name = "daily_order_counter", defaultValue = "0") val dailyOrderCounter: Long = 0,
    @SerializedName("isSynced") @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @SerializedName("updatedAt") @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)