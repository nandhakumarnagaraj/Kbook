package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * A vertical arrangement that distributes residual space evenly between children —
 * but caps how much a single gap may grow. Content-driven children keep their
 * natural heights; excess space beyond the cap becomes bottom breathing room
 * instead of amplifying every inter-section gap.
 *
 * - If children fit within the cap, behavior equals SpaceBetween at [baseSpacing]+ε.
 * - If slack exceeds the cap, gap = baseSpacing + [maxExtraPerGap] and the
 *   remainder sits after the last child.
 * - If children overflow the available height, gaps compress proportionally
 *   (down to 0) so everything stays on screen — non-scrollable screens like
 *   Home never clip their last section.
 */
class BoundedVerticalSpaceBetween(
    private val baseSpacing: Dp,
    private val maxExtraPerGap: Dp
) : Arrangement.Vertical {

    override val spacing: Dp = baseSpacing

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val count = sizes.size
        if (count == 0) return
        if (count == 1) {
            outPositions[0] = 0
            return
        }
        val base = baseSpacing.roundToPx()
        val cap = maxExtraPerGap.roundToPx()
        val slack = totalSize - sizes.sum() - base * (count - 1)
        val gap = if (slack >= 0) {
            base + (slack / (count - 1)).coerceAtMost(cap)
        } else {
            // Overflow: compress gaps proportionally so no child clips off-screen.
            ((totalSize - sizes.sum()) / (count - 1)).coerceAtLeast(0)
        }
        var next = 0
        for (i in 0 until count) {
            outPositions[i] = next
            next += sizes[i] + gap
        }
    }
}