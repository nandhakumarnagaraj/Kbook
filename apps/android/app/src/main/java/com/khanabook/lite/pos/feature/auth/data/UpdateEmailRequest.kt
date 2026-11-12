package com.khanabook.lite.pos.feature.auth.data

data class UpdateEmailRequest(
    val newEmail: String,
    val otp: String
)
