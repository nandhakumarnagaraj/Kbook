package com.khanabook.lite.pos.data.remote.dto

data class UpdateMobileRequest(
    val newMobileNumber: String,
    val otp: String
)
