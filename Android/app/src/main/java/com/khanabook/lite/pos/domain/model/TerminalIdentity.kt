package com.khanabook.lite.pos.domain.model

data class TerminalIdentity(
    val restaurantId: Long,
    val terminalId: String,
    val deviceId: String,
    val terminalName: String?,
    val terminalSeries: String,
    val isActive: Boolean,
    val registeredAt: Long?,
    val lastVerifiedAt: Long?
)

