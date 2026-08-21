package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
 * The logo asset (khanabook_logo.png) is square with a baked-in white background.
 * The circular container preserves the logo's natural 1:1 aspect ratio while
 * providing a clean circular boundary consistent with modern Indian fintech/POS
 * app conventions (PhonePe, CRED, Khatabook).
 *
 * Responsive sizing is controlled by [KhanaBookTheme.layout.logoSize] which
 * resolves per height tier:
 *   - Compact (<640dp height): 110dp
 *   - Normal: 140dp
 *   - Tall (>800dp height): 160dp
 *
 * Usage:
 * ```
 * KhanaBookLogo()                         // default responsive size
 * KhanaBookLogo(size = 160.dp)            // override size
 * ```
 *
 * Used on: Login, SignUp (via AuthFormContainer header)
 */
@Composable
fun KhanaBookLogo(
    modifier: Modifier = Modifier,
    size: Dp = KhanaBookTheme.layout.logoSize,
    borderWidth: Dp = 2.dp,
    borderColor: Color = PrimaryGold.copy(alpha = 0.4f),
    backgroundColor: Color = Color.White
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
                .size(size * 0.82f)
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    }
}
