package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.kbShimmer

/**
 * A single shimmer placeholder block.
 *
 * Uses [kbShimmer] from KbMotion for the animated pulsing background
 * so shimmer behaviour is consolidated in one place.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val widthModifier = if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width)
    Box(
        modifier = modifier
            .then(widthModifier)
            .height(height)
            .clip(shape)
            .kbShimmer(isLoading = true)
    )
}

/**
 * Skeleton card — mimics a list item with an icon circle + two text lines.
 */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(width = 40.dp, height = 40.dp, shape = CircleShape)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.7f))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.5f))
        }
    }
}

/**
 * Skeleton card — mimics a stat/summary card.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    KhanaBookCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.4f))
            Spacer(modifier = Modifier.height(12.dp))
            ShimmerBox(height = 24.dp, modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(height = 10.dp, modifier = Modifier.fillMaxWidth(0.3f))
        }
    }
}

/**
 * Skeleton for a table/report row.
 */
@Composable
fun SkeletonTableRow(modifier: Modifier = Modifier, columns: Int = 4) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(columns) {
            ShimmerBox(
                height   = 14.dp,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Full skeleton screen for Reports / Orders — a summary card + table rows.
 */
@Composable
fun SkeletonReportScreen(modifier: Modifier = Modifier, rowCount: Int = 8) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonCard(modifier = Modifier.weight(1f))
            SkeletonCard(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonCard()
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                ShimmerBox(width = 72.dp, height = 32.dp, shape = RoundedCornerShape(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        SkeletonTableRow()
        Spacer(modifier = Modifier.height(4.dp))

        repeat(rowCount) {
            SkeletonTableRow()
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

/**
 * Full skeleton screen for menu items — grid of item cards.
 */
@Composable
fun SkeletonMenuScreen(modifier: Modifier = Modifier, itemCount: Int = 6) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                ShimmerBox(width = 80.dp, height = 36.dp, shape = RoundedCornerShape(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        ShimmerBox(height = 48.dp, shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(16.dp))

        repeat(itemCount) {
            SkeletonListItem()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
