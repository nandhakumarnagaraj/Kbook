package com.khanabook.lite.pos.feature.auth.data

import com.google.gson.annotations.SerializedName

data class ResetPasswordRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    @SerializedName("otp")
    val otp: String,
    @SerializedName("newPassword")
    val newPassword: String
)

data class PasswordResetOtpRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String
)
