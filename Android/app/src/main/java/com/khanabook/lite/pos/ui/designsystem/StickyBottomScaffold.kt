package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme

/**
 * A generic layout scaffold that positions content above a sticky bottom bar.
 *
 * The scaffold manages:
 * - Header/content/footer slot positioning
 * - Bottom insets (navigation bar + IME) on the bottom bar
 *
 * The scaffold does NOT manage:
 * - Scroll behavior — the screen decides Column+verticalScroll, LazyColumn, Grid, etc.
 * - Theme colors — all colors are parameters defaulting to MaterialTheme tokens
 *
 * Usage:
 * ```
 * StickyBottomScaffold(
 *     bottomBar = { KhanaPrimaryButton("Confirm", onClick = ...) }
 * ) {
 *     Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
 *         // scrollable content
 *     }
 * }
 * ```
 */
@Composable
fun StickyBottomScaffold(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    bottomBarContainerColor: Color = MaterialTheme.colorScheme.surface,
    bottomBarTonalElevation: Dp = 2.dp,
    bottomBarBorder: BorderStroke? = BorderStroke(
        1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    ),
    content: @Composable BoxScope.() -> Unit
) {
    val layout = KhanaBookTheme.layout
    val spacing = KhanaBookTheme.spacing

    Column(modifier = modifier.fillMaxSize()) {
        // Optional header — not scrolled, pinned at top
        header?.invoke()

        // Content area — screen decides its own scroll strategy
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }

        // Sticky bottom bar — handles IME + navigation bar insets
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = bottomBarContainerColor,
            tonalElevation = bottomBarTonalElevation,
            border = bottomBarBorder
        ) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        horizontal = layout.contentPadding,
                        vertical = spacing.smallMedium
                    )
            ) {
                bottomBar()
            }
        }
    }
}
