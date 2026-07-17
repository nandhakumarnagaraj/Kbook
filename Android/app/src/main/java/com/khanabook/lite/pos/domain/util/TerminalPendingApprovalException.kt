package com.khanabook.lite.pos.domain.util

/**
 * Thrown when the server responds with 202 Accepted during terminal activation,
 * meaning the device registration is pending admin approval.
 * Carries the requestId for status polling.
 */
class TerminalPendingApprovalException(
    val requestId: Long?,
    val rejectionCooldown: Boolean = false,
    message: String = "Device registration is pending admin approval"
) : Exception(message)
