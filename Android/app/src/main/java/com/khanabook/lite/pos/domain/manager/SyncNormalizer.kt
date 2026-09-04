package com.khanabook.lite.pos.domain.manager

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure, dependency-free normalization used by [MasterSyncProcessor] when building
 * push payloads. Extracted so the sync-loop-critical rules can be unit-tested
 * without the processor's DAO/session dependencies.
 *
 * WHY THIS MATTERS: the server serializes money as BigDecimal with scale 2
 * ("10.00", not "10"). If the client sends a differently-formatted string the
 * server sees a "change" on every pull/push and the record re-syncs forever.
 * [toSafeAmount] guarantees the client string matches the server's canonical form.
 */
object SyncNormalizer {

    /** Roles the server accepts; anything unknown/null collapses to OWNER. */
    fun normalizeUserRole(role: String?): String = when (role?.uppercase()) {
        "OWNER", "SHOP_ADMIN", "KBOOK_ADMIN", "OPERATIONS" -> role.uppercase()
        else -> "OWNER"
    }

    /**
     * Canonical money string: scale 2, HALF_UP, no stripped trailing zeros.
     * Null/blank/garbage → "0.00". Must match server BigDecimal serialization.
     */
    fun toSafeAmount(value: String?): String {
        if (value.isNullOrBlank()) return "0.00"
        return try {
            BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString()
        } catch (e: Exception) {
            "0.00"
        }
    }
}
