package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName


data class PushSyncResponse(
    @SerializedName("successfulLocalIds")
    val successfulLocalIds: List<Long>,
    @SerializedName("failedLocalIds")
    val failedLocalIds: List<Long>
)
