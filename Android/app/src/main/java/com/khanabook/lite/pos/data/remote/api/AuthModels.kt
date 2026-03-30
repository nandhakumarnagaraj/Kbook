package com.khanabook.lite.pos.data.remote.api

data class LoginRequest(
    val phoneNumber: String,
    val password: String,
    val deviceId: String
)

data class GoogleLoginRequest(
    val idToken: String,
    val deviceId: String
)

data class SignupRequest(
    val phoneNumber: String,
    val name: String,
    val password: String,
    val otp: String,
    val deviceId: String
)

data class SignupOtpRequest(
    val phoneNumber: String
)

data class AuthResponse(
    val token: String,
    val restaurantId: Long,
    val userName: String,
    val loginId: String? = null,
    val whatsappNumber: String? = null,
    val userEmail: String? = null,
    val role: String? = null
)
