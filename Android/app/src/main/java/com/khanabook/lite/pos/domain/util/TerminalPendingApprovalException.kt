package com.khanabook.lite.pos.domain.util

/**
 * Thrown when the server responds with 202 Accepted during terminal activation,
 * meaning the device registration is pending admin approval.
 * Carries the requestId for status polling and the challengeCode for number-matching.
 */
class TerminalPendingApprovalException(
    val requestId: Long?,
    val rejectionCooldown: Boolean = false,
    val challengeCode: String? = null,
    val challengeExpiresAt: Long? = null,
    message: String = "Device registration is pending admin approval"
) : Exception(message)
