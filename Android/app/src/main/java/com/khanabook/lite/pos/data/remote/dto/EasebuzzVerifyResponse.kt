package com.khanabook.lite.pos.data.remote.dto

/**
 * Server reconciliation response for an Easebuzz transaction.
 * `status` is canonical — "success" / "failed" / "pending".
 * `gatewayStatus` is the raw Easebuzz status (kept for diagnostics).
 */
data class EasebuzzVerifyResponse(
    val txnId: String,
    val status: String,
    val gatewayStatus: String? = null,
    val found: Boolean = false,
    val amount: String? = null,
    val receivedAt: Long? = null
)
