package com.khanabook.lite.pos.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spacing and control heights are resolved once in the theme provider from the
 * window's type tier, which makes gaps and buttons responsive app-wide.
 *
 * These tests pin the two invariants that make that safe:
 *  1. MediumPhone is byte-identical to the historical fixed Spacing, so the most
 *     common handset is visually unchanged.
 *  2. No tier ever drops a control below the 48dp Material touch target, and
 *     both gaps and control heights grow monotonically with the tier.
 */
class ResponsiveSpacingTest {

    private val tiers = listOf(
        TypeScaleTier.CompactPhone,
        TypeScaleTier.MediumPhone,
        TypeScaleTier.LargePhone,
        TypeScaleTier.Tablet
    )

    @Test
    fun `medium phone tier matches the historical fixed spacing exactly`() {
        val s = spacingForTier(TypeScaleTier.MediumPhone)
        assertEquals(Spacing(), s)
    }

    @Test
    fun `no tier puts a control height below the 48dp touch target`() {
        tiers.forEach { tier ->
            val s = spacingForTier(tier)
            val controls = listOf(
                "buttonHeightCompact" to s.buttonHeightCompact,
                "buttonHeight" to s.buttonHeight,
                "buttonHeightLarge" to s.buttonHeightLarge,
                "inputHeight" to s.inputHeight
            )
            controls.forEach { (name, value) ->
                assertTrue(
                    "$tier $name = $value is under the 48dp touch target",
                    value.value >= 48f
                )
            }
        }
    }

    @Test
    fun `gaps grow monotonically from compact phone to tablet`() {
        val scales = tiers.map { spacingForTier(it).medium.value }
        scales.zipWithNext { smaller, larger ->
            assertTrue(
                "gap scale must not shrink as the window grows: $scales",
                larger >= smaller
            )
        }
        assertTrue("compact phone should be tighter than tablet", scales.first() < scales.last())
    }

    @Test
    fun `control heights grow monotonically from compact phone to tablet`() {
        tiers.map { spacingForTier(it).buttonHeightLarge.value }
            .zipWithNext { smaller, larger ->
                assertTrue(
                    "control heights must not shrink as the window grows",
                    larger >= smaller
                )
            }
    }

    @Test
    fun `gaps stay within a restrained band so layouts cannot overflow`() {
        // A blunt multiplier would reflow every screen; this bound keeps the
        // change visually meaningful but structurally safe.
        tiers.forEach { tier ->
            val s = spacingForTier(tier)
            assertTrue("$tier medium gap ${s.medium} must stay <= 20dp", s.medium.value <= 20f)
            assertTrue("$tier medium gap ${s.medium} must stay >= 12dp", s.medium.value >= 12f)
        }
    }

    @Test
    fun `action button height never drops below the touch target`() {
        listOf(320, 360, 411, 600, 800, 1280).forEach { widthDp ->
            listOf(480, 640, 800, 1208).forEach { heightDp ->
                val layout = responsiveLayoutForWindowSizeClass(
                    screenWidthDp = widthDp,
                    screenHeightDp = heightDp,
                    widthSizeClass = androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
                )
                assertTrue(
                    "${widthDp}x$heightDp button ${layout.actionButtonHeight} under 48dp",
                    layout.actionButtonHeight.value >= 48f
                )
            }
        }
    }

    @Test
    fun `order table columns stay clamped at both ends of the size range`() {
        val narrow = ResponsiveLayout(screenWidthDp = 320, widthTier = WindowWidthTier.Compact, screenHeightDp = 480)
        val wide = ResponsiveLayout(screenWidthDp = 1280, widthTier = WindowWidthTier.Expanded, screenHeightDp = 800)

        assertTrue(narrow.orderTableIndexWidth.value >= 24f)
        assertTrue(wide.orderTableIndexWidth.value <= 40f)
        assertTrue(narrow.orderTableCountWidth.value >= 40f)
        assertTrue(wide.orderTableCountWidth.value <= 64f)
        assertTrue(narrow.orderTablePriceWidth.value >= 76f)
        assertTrue(wide.orderTablePriceWidth.value <= 140f)

        // The item-name column keeps a usable share even on a 320dp budget phone.
        val fixedOnNarrow = narrow.orderTableIndexWidth.value +
            narrow.orderTableCountWidth.value +
            narrow.orderTablePriceWidth.value
        assertTrue("fixed columns must leave room for names on 320dp", fixedOnNarrow < 320f * 0.6f)
    }
}
