package com.khanabook.lite.pos.feature.auth.domain

data class TerminalIdentity(
    val restaurantId: Long,
    val terminalId: String,
    val deviceId: String,
    val terminalName: String?,
    val terminalSeries: String,
    val isActive: Boolean,
    val registeredAt: Long?,
    val lastVerifiedAt: Long?,
    val terminalToken: String? = null
)

