package com.khanabook.lite.pos.feature.auth.data

data class UpdateMobileRequest(
    val newMobileNumber: String,
    val otp: String
)
