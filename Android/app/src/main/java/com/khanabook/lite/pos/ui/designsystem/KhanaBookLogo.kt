package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold

/**
 * KhanaBook brand logo — single reusable component for all brand screens.
 *
 * Provides:
 * - Circular container with white background
 * - Subtle gold border
 * - Responsive sizing via [KhanaBookTheme.layout.logoSize]
 * - Logo image clipped to circle (no rectangular white corners)
 *
 * Usage:
 * ```
 * KhanaBookLogo()                         // default responsive size
 * KhanaBookLogo(size = 140.dp)            // override size
 * KhanaBookLogo(borderWidth = 3.dp)       // thicker border
 * ```
 *
 * Used on: Login, SignUp, Splash, BrandedStartFrame, and any future brand screens.
 */
@Composable
fun KhanaBookLogo(
    modifier: Modifier = Modifier,
    size: Dp = KhanaBookTheme.layout.logoSize,
    borderWidth: Dp = 2.dp,
    borderColor: Color = PrimaryGold.copy(alpha = 0.4f),
    backgroundColor: Color = Color.White,
    innerPadding: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.khanabook_logo),
            contentDescription = "KhanaBook logo",
            modifier = Modifier
                .size(size - innerPadding * 2)
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    }
}
