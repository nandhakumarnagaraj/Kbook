package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.sync.domain.SyncNormalizer

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncNormalizerTest {

    // ── toSafeAmount: the sync-loop guard ──────────────────────────────────

    @Test
    fun `integers and short decimals canonicalize to scale 2`() {
        assertEquals("10.00", SyncNormalizer.toSafeAmount("10"))
        assertEquals("10.50", SyncNormalizer.toSafeAmount("10.5"))
        assertEquals("10.00", SyncNormalizer.toSafeAmount("10.00"))
        assertEquals("0.00", SyncNormalizer.toSafeAmount("0"))
    }

    @Test
    fun `trailing zeros are preserved not stripped (matches server BigDecimal)`() {
        // If this ever returned "10" the record would re-sync forever.
        assertEquals("100.00", SyncNormalizer.toSafeAmount("100.000"))
        assertEquals("100.00", SyncNormalizer.toSafeAmount("100"))
    }

    @Test
    fun `half up rounding at third decimal`() {
        assertEquals("10.56", SyncNormalizer.toSafeAmount("10.555"))
        assertEquals("10.55", SyncNormalizer.toSafeAmount("10.554"))
    }

    @Test
    fun `null blank and garbage fall back to zero`() {
        assertEquals("0.00", SyncNormalizer.toSafeAmount(null))
        assertEquals("0.00", SyncNormalizer.toSafeAmount(""))
        assertEquals("0.00", SyncNormalizer.toSafeAmount("   "))
        assertEquals("0.00", SyncNormalizer.toSafeAmount("abc"))
    }

    // ── normalizeUserRole ──────────────────────────────────────────────────

    @Test
    fun `known roles are upper-cased and preserved`() {
        assertEquals("OWNER", SyncNormalizer.normalizeUserRole("owner"))
        assertEquals("SHOP_STAFF", SyncNormalizer.normalizeUserRole("shop_staff"))
        assertEquals("KBOOK_ADMIN", SyncNormalizer.normalizeUserRole("KBOOK_ADMIN"))
    }

    @Test
    fun `legacy staff roles collapse to SHOP_STAFF`() {
        assertEquals("SHOP_STAFF", SyncNormalizer.normalizeUserRole("SHOP_ADMIN"))
        assertEquals("SHOP_STAFF", SyncNormalizer.normalizeUserRole("waiter"))
        assertEquals("SHOP_STAFF", SyncNormalizer.normalizeUserRole("cashier"))
        assertEquals("SHOP_STAFF", SyncNormalizer.normalizeUserRole("MANAGER"))
        assertEquals("SHOP_STAFF", SyncNormalizer.normalizeUserRole("OPERATIONS"))
    }

    @Test
    fun `kitchen collapses to owner matching server V12`() {
        assertEquals("OWNER", SyncNormalizer.normalizeUserRole("KITCHEN"))
    }

    @Test
    fun `unknown or null role collapses to OWNER`() {
        assertEquals("OWNER", SyncNormalizer.normalizeUserRole(null))
        assertEquals("OWNER", SyncNormalizer.normalizeUserRole(""))
        assertEquals("OWNER", SyncNormalizer.normalizeUserRole("staff"))
    }
}