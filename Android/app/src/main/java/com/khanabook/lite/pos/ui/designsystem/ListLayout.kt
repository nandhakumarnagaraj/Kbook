package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A layout primitive for screens showing filterable lists of items.
 *
 * The scaffold manages:
 * - Filter bar positioning (pinned above list)
 * - Empty/content state switching with crossfade animation
 *
 * The scaffold does NOT manage:
 * - Scroll behavior — the screen provides its own LazyColumn/LazyGrid as [content]
 * - Bottom insets — handled by parent Scaffold or individual list padding
 *
 * Usage:
 * ```
 * ListLayout(
 *     filterBar = { FilterChipsRow(...) },
 *     isEmpty = orders.isEmpty(),
 *     emptyState = { KhanaEmptyState("No orders") }
 * ) {
 *     LazyColumn { items(orders) { ... } }
 * }
 * ```
 */
@Composable
fun ListLayout(
    modifier: Modifier = Modifier,
    filterBar: (@Composable () -> Unit)? = null,
    isEmpty: Boolean = false,
    emptyState: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        filterBar?.invoke()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = isEmpty, label = "list_empty") { empty ->
                if (empty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        emptyState()
                    }
                } else {
                    content()
                }
            }
        }
    }
}
