package com.khanabook.lite.pos.core.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.input.pointer.PointerEvent

/**
 * Draws a translucent dot for every active touch pointer and fades each dot out on release.
 *
 * ARCHITECTURAL BOUNDARY: this View never participates in touch dispatch. [dispatchTouchEvent]
 * always returns `false`, and touch data is pushed in explicitly via [onMotionEvent] (Activity
 * window) or [onComposePointerEvent] (Compose dialog window). That is what makes the overlay
 * strictly observational: it cannot delay, consume, or redirect a tap, scroll, swipe or gesture.
 *
 * ALLOCATION POLICY: motion updates only mutate pre-allocated [Dot] instances; no objects,
 * lambdas or collections are created inside the ACTION_MOVE path. `invalidate()` merely requests
 * the next vsync draw, it does not allocate.
 */
internal class TouchIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Phase { DOWN, MOVE, UP, CANCEL }

    private class Dot(var id: Long, var x: Float, var y: Float) {
        var releasedAt: Long = -1L
    }

    private val active = ArrayList<Dot>(MAX_POINTERS)
    private val fading = ArrayList<Dot>(MAX_POINTERS)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val density: Float = context.resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Never intercept: hit-testing reaches this View first, and returning false passes the
     *  event to the real interactive content behind it without any measurable delay. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    /** Drops all dots when a hosting composition or Activity window is torn down. */
    fun shutdown() {
        active.clear()
        fading.clear()
        invalidate()
    }

    /**
     * Feeds the Activity-window touch stream. Coordinates are MotionEvent coordinates relative
     * to the window decor, which is exactly this View's coordinate space because it is attached
     * to the decor with MATCH_PARENT at (0, 0).
     *
     * The caller must invoke this synchronously *before* the framework reuses the MotionEvent;
     * all values are read immediately and copied into [Dot] fields.
     */
    fun onMotionEvent(event: MotionEvent) {
        if (!TouchIndicatorConfig.isEnabled) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updatePointer(
                    event.getPointerId(0).toLong(), event.getX(0), event.getY(0), Phase.DOWN
                )
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                updatePointer(
                    event.getPointerId(i).toLong(), event.getX(i), event.getY(i), Phase.DOWN
                )
                refreshOtherPointers(event, skipIndex = i)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    updatePointer(
                        event.getPointerId(i).toLong(), event.getX(i), event.getY(i), Phase.MOVE
                    )
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                updatePointer(
                    event.getPointerId(i).toLong(), event.getX(i), event.getY(i), Phase.UP
                )
                refreshOtherPointers(event, skipIndex = i)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    updatePointer(
                        event.getPointerId(i).toLong(), event.getX(i), event.getY(i), Phase.CANCEL
                    )
                }
            }
        }
    }

    /**
     * Feeds a Compose-window touch stream (Dialog / ModalBottomSheet). Uses the immutable
     * [PointerEvent] snapshot instead of synthesizing MotionEvents, so no per-move allocation
     * is introduced.
     */
    fun onComposePointerEvent(event: PointerEvent) {
        if (!TouchIndicatorConfig.isEnabled) return
        for (change in event.changes) {
            val id = change.id.value
            val x = change.position.x
            val y = change.position.y
            when {
                change.pressed && !change.previousPressed -> updatePointer(id, x, y, Phase.DOWN)
                !change.pressed && change.previousPressed -> updatePointer(id, x, y, Phase.UP)
                change.pressed -> updatePointer(id, x, y, Phase.MOVE)
            }
        }
        invalidate()
    }

    private fun refreshOtherPointers(event: MotionEvent, skipIndex: Int) {
        for (i in 0 until event.pointerCount) {
            if (i == skipIndex) continue
            updatePointer(
                event.getPointerId(i).toLong(), event.getX(i), event.getY(i), Phase.MOVE
            )
        }
    }

    private fun updatePointer(id: Long, x: Float, y: Float, phase: Phase) {
        when (phase) {
            Phase.DOWN, Phase.MOVE -> {
                var found: Dot? = null
                for (dot in active) {
                    if (dot.id == id) {
                        found = dot
                        break
                    }
                }
                if (found == null) {
                    if (active.size < MAX_POINTERS) active.add(Dot(id, x, y))
                } else {
                    found.x = x
                    found.y = y
                }
            }
            Phase.UP, Phase.CANCEL -> {
                var index = -1
                for (i in active.indices) {
                    if (active[i].id == id) {
                        index = i
                        break
                    }
                }
                if (index >= 0) {
                    val dot = active.removeAt(index)
                    dot.releasedAt = SystemClock.uptimeMillis()
                    if (fading.size >= MAX_POINTERS) fading.removeAt(0)
                    fading.add(dot)
                }
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val style = TouchIndicatorConfig.style
        val now = SystemClock.uptimeMillis()
        val duration = style.fadeDurationMs.coerceAtLeast(1L)

        var i = fading.size - 1
        while (i >= 0) {
            if (now - fading[i].releasedAt >= duration) fading.removeAt(i)
            i--
        }

        val radius = style.radiusDp * density
        val borderWidth = style.borderWidthDp * density

        for (dot in active) drawDot(canvas, dot.x, dot.y, radius, 1f, borderWidth, style)
        for (dot in fading) {
            val t = ((now - dot.releasedAt).toFloat() / duration).coerceIn(0f, 1f)
            drawDot(
                canvas = canvas,
                x = dot.x,
                y = dot.y,
                radius = radius * (1f + GROWTH_ON_RELEASE * t),
                alpha = 1f - t,
                borderWidth = borderWidth,
                style = style
            )
        }

        if (fading.isNotEmpty()) invalidate()
    }

    private fun drawDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        alpha: Float,
        borderWidth: Float,
        style: TouchDotStyle
    ) {
        glowPaint.color = style.glowArgb
        glowPaint.alpha = (Color.alpha(style.glowArgb) * alpha).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, radius * style.glowScale, glowPaint)

        fillPaint.color = style.fillArgb
        fillPaint.alpha = (Color.alpha(style.fillArgb) * alpha).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, radius, fillPaint)

        if (borderWidth > 0f) {
            borderPaint.color = style.borderArgb
            borderPaint.alpha = (Color.alpha(style.borderArgb) * alpha).toInt().coerceIn(0, 255)
            borderPaint.strokeWidth = borderWidth
            canvas.drawCircle(x, y, radius - borderWidth / 2f, borderPaint)
        }
    }

    private companion object {
        const val MAX_POINTERS = 10
        const val GROWTH_ON_RELEASE = 0.35f
    }
}
