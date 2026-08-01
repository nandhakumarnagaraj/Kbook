package com.khanabook.lite.pos.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test
    fun `tablet width and compact landscape height are resolved independently`() {
        val layout = ResponsiveLayout(
            screenWidthDp = 900,
            widthTier = WindowWidthTier.Expanded,
            screenHeightDp = 420
        )

        assertTrue(layout.isWideListDetail)
        assertTrue(layout.isCompactHeight)
        assertTrue(layout.isLandscape)
    }

    @Test
    fun `phone portrait remains compact and non compact height`() {
        val layout = ResponsiveLayout(
            screenWidthDp = 390,
            widthTier = WindowWidthTier.Compact,
            screenHeightDp = 844
        )

        assertTrue(layout.isCompact)
        assertFalse(layout.isWideListDetail)
        assertFalse(layout.isCompactHeight)
        assertFalse(layout.isLandscape)
    }
}
