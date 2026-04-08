package com.khanabook.lite.pos.ui.designsystem

import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

/**
 * Predefined loading types that map to specific animations and default messages.
 */
enum class LoadingType(@RawRes val animRes: Int, val defaultMessage: String) {
    GENERAL(R.raw.anim_loading, "Loading..."),
    LOGIN(R.raw.anim_loading, "Logging in..."),
    GOOGLE_LOGIN(R.raw.anim_loading, "Connecting to Google..."),
    SIGNUP(R.raw.anim_loading, "Creating Account..."),
    SYNC(R.raw.anim_sync, "Setting up your workspace..."),
    SAVING(R.raw.anim_loading, "Saving Bill..."),
    PROCESSING(R.raw.anim_scanning, "Processing..."),
    SCANNING(R.raw.anim_scanning, "Scanning..."),
    LOGOUT(R.raw.anim_loading, "Logging out...")
}

/**
 * Universal branded loading overlay for KhanaBook.
 *
 * Displays a Lottie animation with a message and optional progress indicator.
 * Use this instead of raw CircularProgressIndicator overlays throughout the app.
 *
 * @param visible Controls visibility with animated fade in/out.
 * @param type Determines the animation and default message.
 * @param message Override the default message for the loading type.
 * @param subtitle Optional secondary text (e.g., "Please wait. Do not close the app.").
 * @param progress Optional determinate progress (0f..1f). Shows LinearProgressIndicator when set.
 */
@Composable
fun KhanaBookLoadingOverlay(
    visible: Boolean,
    type: LoadingType = LoadingType.GENERAL,
    message: String = type.defaultMessage,
    subtitle: String? = null,
    progress: Float? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { /* block touches */ },
            contentAlignment = Alignment.Center
        ) {
            val spacing = KhanaBookTheme.spacing

            KhanaBookCard(
                modifier = Modifier
                    .padding(horizontal = spacing.extraLarge)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large, vertical = spacing.extraLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Lottie animation
                    val composition by rememberLottieComposition(
                        LottieCompositionSpec.RawRes(type.animRes)
                    )
                    val lottieProgress by animateLottieCompositionAsState(
                        composition = composition,
                        iterations = LottieConstants.IterateForever
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { lottieProgress },
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(spacing.medium))

                    // Primary message
                    Text(
                        text = message,
                        color = TextLight,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )

                    // Subtitle
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            text = subtitle,
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Progress bar
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = PrimaryGold,
                            trackColor = PrimaryGold.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
