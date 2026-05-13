package com.khanabook.lite.pos.data.remote.api

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("loginId") val loginId: String,
    @SerializedName("password") val password: String,
    @SerializedName("deviceId") val deviceId: String
)

data class GoogleLoginRequest(
    @SerializedName("idToken") val idToken: String,
    @SerializedName("deviceId") val deviceId: String
)

data class SignupRequest(
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("name") val name: String,
    @SerializedName("password") val password: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("deviceId") val deviceId: String
)

data class SignupOtpRequest(
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("restaurantId") val restaurantId: Long,
    @SerializedName("userName") val userName: String,
    @SerializedName("loginId") val loginId: String? = null,
    @SerializedName("whatsappNumber") val whatsappNumber: String? = null,
    @SerializedName("userEmail") val userEmail: String? = null,
    @SerializedName("role") val role: String? = null
)
