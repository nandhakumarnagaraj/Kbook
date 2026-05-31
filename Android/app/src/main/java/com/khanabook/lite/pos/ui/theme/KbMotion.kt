package com.khanabook.lite.pos.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════
// KHANBOOK DLS — MOTION SYSTEM
//
// Modeled on Material 3 motion spec + Slice DLS pattern.
// Use these tokens instead of hardcoded milliseconds anywhere
// you write animations.
// ═══════════════════════════════════════════════════════════════

object KbMotion {

    // ── Duration tokens ──────────────────────────────────────────
    // Short: micro-interactions — icon swaps, toggle, ripple
    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200

    // Medium: component-level — card expand, bottom sheet, dialog
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400

    // Long: screen-level — page enter/exit, shared element
    const val DurationLong1 = 450
    const val DurationLong2 = 500
    const val DurationLong3 = 550
    const val DurationLong4 = 600

    // Extra Long: onboarding, splash
    const val DurationExtraLong = 1000

    // ── Easing curves ─────────────────────────────────────────────
    // Standard: general purpose
    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // Emphasized: snappy start, smooth deceleration (M3 default for screen transitions)
    val EasingEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    // Decelerate: elements entering the screen (slide in from edge)
    val EasingDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    // Accelerate: elements leaving the screen (slide out toward edge)
    val EasingAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // Linear: progress bars, spinners
    val EasingLinear = LinearEasing

    // ── Spring specs ──────────────────────────────────────────────
    // Default: slightly underdamped — used for press scale, card lift
    val SpringDefault = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

    // Bouncy: noticeably springy — used for success states, FAB
    val SpringBouncy = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)

    // Stiff: very tight — used for nav icon transitions
    val SpringStiff = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)
}

// ═══════════════════════════════════════════════════════════════
// KHANBOOK DLS — COMPOSE ANIMATION COMPONENTS
// ═══════════════════════════════════════════════════════════════

/**
 * Standard enter/exit animation for inline content visibility.
 * Slides up 25% and fades in on enter; fades out on exit.
 * Use for: bottom panels, inline error messages, expanded sections.
 */
@Composable
fun KbFadeSlideIn(
    visible: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(KbMotion.DurationMedium2, easing = KbMotion.EasingDecelerate)) +
                slideInVertically(tween(KbMotion.DurationMedium2, easing = KbMotion.EasingDecelerate)) { it / 4 },
        exit  = fadeOut(tween(KbMotion.DurationShort4, easing = KbMotion.EasingAccelerate)) +
                slideOutVertically(tween(KbMotion.DurationShort4, easing = KbMotion.EasingAccelerate)) { it / 4 },
        content = content
    )
}

/**
 * Press-scale feedback modifier for cards, buttons, and list items.
 * Scales down to [scaleDown] on press with a spring bounce on release.
 * Usage: Modifier.kbPressScale()
 */
fun Modifier.kbPressScale(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "kbPressScale"
    )
    this
        .scale(scale)
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    if (enabled) {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    }
                }
            )
        }
}

/**
 * Shimmer loading skeleton placeholder.
 * Shows an animated pulsing background while content is loading.
 *
 * Light mode: warm pair #F0EAE4 / #E8E0D8 — visible on #FAF7F4 page and white cards.
 * Dark mode:  surfaceVariant pulse — adapts to dark bg.
 *
 * Usage: Box(Modifier.kbShimmer(isLoading = true))
 */
fun Modifier.kbShimmer(isLoading: Boolean): Modifier = composed {
    if (!isLoading) return@composed this
    val isDark = globalIsDark
    val transition = rememberInfiniteTransition(label = "kbShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "kbShimmerAlpha"
    )
    if (isDark) {
        this.background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
            shape = RoundedCornerShape(8.dp)
        )
    } else {
        // Light: interpolate between two warm off-white tones
        val shimmerColor = androidx.compose.ui.graphics.lerp(
            androidx.compose.ui.graphics.Color(0xFFF0EAE4),
            androidx.compose.ui.graphics.Color(0xFFE8E0D8),
            alpha
        )
        this.background(color = shimmerColor, shape = RoundedCornerShape(8.dp))
    }
}

/**
 * Animated number counter for bill totals, order counts.
 * Slides up on increment, slides down on decrement.
 * Usage: KbAnimatedCounter(count = billTotal, style = MaterialTheme.typography.displaySmall)
 */
@Composable
fun KbAnimatedCounter(count: Int, style: TextStyle) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(KbMotion.DurationShort4)) { if (up) -it else it } +
             fadeIn(tween(KbMotion.DurationShort4))) togetherWith
            (slideOutVertically(tween(KbMotion.DurationShort4)) { if (up) it else -it } +
             fadeOut(tween(KbMotion.DurationShort4)))
        }, label = "kbCounter"
    ) { targetCount ->
        Text(text = targetCount.toString(), style = style)
    }
}

/**
 * Pulsing dot loading indicator (3 bouncing dots).
 * Use for: network calls, order submission, payment processing.
 */
@Composable
fun KbLoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "kbLoadingDots")
    val dots = (0..2).map { index ->
        val alpha by transition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = index * 150, easing = KbMotion.EasingStandard),
                repeatMode = RepeatMode.Reverse
            ), label = "dot$index"
        )
        alpha
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}
