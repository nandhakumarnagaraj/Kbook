package com.khanabook.lite.pos.feature.sync.data

import com.google.gson.annotations.SerializedName


data class PushSyncResponse(
    @SerializedName("successfulLocalIds")
    val successfulLocalIds: List<Long>,
    @SerializedName("failedLocalIds")
    val failedLocalIds: List<Long>,
    @SerializedName("localToServerIdMap")
    val localToServerIdMap: Map<Long, Long>? = null,
    @SerializedName("failedReasons")
    val failedReasons: Map<Long, String>? = null
)
