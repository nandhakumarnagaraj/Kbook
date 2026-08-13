package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme

/**
 * A layout primitive for centered message states (success, error, empty, sync)
 * that guarantees vertical scroll so content never clips.
 *
 * This scaffold DOES include verticalScroll because centered message screens
 * always have static content (never LazyColumn). This is intentionally
 * different from StickyBottomScaffold which is scroll-agnostic.
 *
 * Usage:
 * ```
 * ScrollableCenteredLayout(
 *     bottomBar = { KhanaPrimaryButton("Back to Home", onClick = ...) }
 * ) {
 *     Icon(...)
 *     Text("Success!")
 * }
 * ```
 */
@Composable
fun ScrollableCenteredLayout(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val layout = KhanaBookTheme.layout
    val spacing = KhanaBookTheme.spacing

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.contentPadding, vertical = spacing.large),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }

        if (bottomBar != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
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
}
