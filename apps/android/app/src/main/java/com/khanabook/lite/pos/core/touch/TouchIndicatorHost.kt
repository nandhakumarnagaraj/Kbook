package com.khanabook.lite.pos.core.touch

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Single attachment point for the Activity-window touch stream.
 *
 * WHY: KhanaBook has exactly one Activity (MainActivity) and no BaseActivity, so attaching one
 * transparent View to the window decor and forwarding [MotionEvent]s from
 * [Activity.dispatchTouchEvent] covers every in-window screen (NavHost destinations, splash,
 * snackbar, banner) with no changes to individual composables.
 *
 * WINDOW SCOPE: a Compose `Dialog` / `AlertDialog` / `ModalBottomSheet` creates its own native
 * window with its own ViewRootImpl; those touch events never reach the Activity callback. For
 * those surfaces use [TouchIndicatorOverlay] inside the dialog content instead.
 */
object TouchIndicatorHost {

    private var view: TouchIndicatorView? = null

    /**
     * Adds the draw-only overlay to the Activity decor. Must be called after
     * `enableEdgeToEdge()` so the overlay covers the full window, including inset areas.
     */
    fun attach(activity: Activity) {
        TouchIndicatorConfig.initialize(activity.applicationContext)
        detach()

        val decor = activity.window.decorView as? ViewGroup ?: return
        val indicator = TouchIndicatorView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Last child plus explicit elevation keeps the dot above Compose content in the
            // same window. This does NOT lift it above a dialog window - separate windows are
            // always stacked above the Activity window regardless of child elevation.
            elevation = 1_000f
        }
        decor.addView(indicator)
        view = indicator
    }

    /** Reads and mirrors the touch into the overlay before the framework consumes the event.
     *  No event is consumed here and no MotionEvent copy is allocated. */
    fun onTouch(event: MotionEvent) {
        val indicator = view ?: return
        val enabled = TouchIndicatorConfig.isEnabled
        val wantedVisibility = if (enabled) View.VISIBLE else View.GONE
        if (indicator.visibility != wantedVisibility) indicator.visibility = wantedVisibility
        if (enabled) indicator.onMotionEvent(event)
    }

    fun detach() {
        view?.let { indicator ->
            (indicator.parent as? ViewGroup)?.removeView(indicator)
        }
        view = null
    }
}
