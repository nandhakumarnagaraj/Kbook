package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme

/**
 * Shared responsive container for auth screens (Login, SignUp, ForgotPassword).
 *
 * Provides:
 * - Responsive width: [layout.dialogWidthFraction] with [layout.dialogMaxWidth] cap
 * - Scroll: always scrollable (never clips on compact devices or with keyboard)
 * - Insets: statusBar + navigationBar + IME
 * - Padding: horizontal [spacing.large], vertical [spacing.medium]
 * - Centering: horizontally centered content, top-aligned vertically
 *
 * All auth screens wrap their content in this container rather than independently
 * calculating width/padding/insets. This is the single source of truth for auth
 * layout geometry.
 *
 * Usage:
 * ```
 * AuthFormContainer {
 *     KhanaBookLogo(...)
 *     Text("Sign Up", ...)
 *     OutlinedTextField(...)
 *     Button(...)
 * }
 * ```
 */
@Composable
fun AuthFormContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    Column(
        modifier = modifier
            .fillMaxWidth(layout.dialogWidthFraction)
            .widthIn(max = layout.dialogMaxWidth)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        content = content
    )
}
