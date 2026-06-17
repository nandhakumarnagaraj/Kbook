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
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

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

    // Unified duration object categories
    object MotionDuration {
        const val Fast = 120
        const val Medium = 250
        const val Slow = 500
    }

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

    // Linear Out Slow In
    val EasingLinearOutSlowIn = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    // ── Spring specs ──────────────────────────────────────────────
    // Default: slightly underdamped — used for press scale, card lift
    val SpringDefault = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

    // Bouncy: noticeably springy — used for success states, FAB
    val SpringBouncy = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)

    // Stiff: very tight — used for nav icon transitions
    val SpringStiff = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)

    // Low Stiffness: gentle bounce — used for adaptive layout transitions
    val SpringLowStiffness = spring<Float>(dampingRatio = 0.85f, stiffness = 200f)

    /**
     * Performance check helper to identify low-end POS terminals or battery-saver states.
     */
    fun isLowEndDevice(context: android.content.Context): Boolean {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (activityManager != null) {
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalMemGb = memoryInfo.totalMem / (1024 * 1024 * 1024)
            if (totalMemGb < 4) return true
        }
        val cores = Runtime.getRuntime().availableProcessors()
        if (cores < 6) return true

        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager != null && powerManager.isPowerSaveMode) return true

        return false
    }
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
    scaleDown: Float = 0.98f
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
 * Self-drawing checkmark animation for payment/operation success states.
 */
@Composable
fun KbSuccessCheckmark(
    modifier: Modifier = Modifier,
    color: Color = KbSuccess,
    strokeWidth: Dp = 4.dp
) {
    val animateVal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animateVal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = KbMotion.EasingStandard)
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val progress = animateVal.value
        val p1 = 0.4f
        val startX = width * 0.28f
        val startY = height * 0.5f
        val midX = width * 0.44f
        val midY = height * 0.66f
        val endX = width * 0.72f
        val endY = height * 0.34f

        if (progress > 0f) {
            if (progress <= p1) {
                val currentProgress = progress / p1
                val curX = startX + (midX - startX) * currentProgress
                val curY = startY + (midY - startY) * currentProgress
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(curX, curY),
                    strokeWidth = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(midX, midY),
                    strokeWidth = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
                val currentProgress = (progress - p1) / (1f - p1)
                val curX = midX + (endX - midX) * currentProgress
                val curY = midY + (endY - midY) * currentProgress
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(midX, midY),
                    end = androidx.compose.ui.geometry.Offset(curX, curY),
                    strokeWidth = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
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

/**
 * Horizontal shake animation modifier.
 * Animates translationX horizontally to indicate error states.
 */
fun Modifier.shake(trigger: Any?): Modifier = composed {
    var animOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(trigger) {
        if (trigger != null && trigger != false && trigger != "") {
            val shakeRange = listOf(-12f, 10f, -8f, 6f, -4f, 2f, 0f)
            for (offset in shakeRange) {
                animOffset = offset
                delay(30)
            }
        }
    }
    this.graphicsLayer { translationX = animOffset }
}

/**
 * Focus-aware glow modifier for inputs, cards, and text fields.
 * Animates shadow elevation and applies a saffron border highlight on focus.
 */
fun Modifier.kbFocusGlow(
    isFocused: Boolean,
    focusedColor: Color = Color(0xFFF97316), // Saffron brand color
    unfocusedColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(14.dp)
): Modifier = composed {
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) 6f else 0f,
        animationSpec = tween(durationMillis = 200, easing = KbMotion.EasingStandard),
        label = "shadowGlowElevation"
    )

    this
        .graphicsLayer {
            this.shadowElevation = shadowElevation
            this.clip = true
            this.shape = shape
        }
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) focusedColor else unfocusedColor,
            shape = shape
        )
}

/**
 * Staggered entrance animation modifier for lists, form elements, and screen content.
 * Animates opacity and vertical translation sequentially based on stagger step index.
 */
fun Modifier.staggeredEntrance(stepIndex: Int, activeStep: Int): Modifier = composed {
    val visible = activeStep >= stepIndex
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = KbMotion.EasingStandard),
        label = "staggerAlpha$stepIndex"
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(durationMillis = 300, easing = KbMotion.EasingStandard),
        label = "staggerTranslation$stepIndex"
    )
    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY
    }
}
