package com.khanabook.lite.pos.core.touch

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Drop-in replacement for the Compose-UI [Dialog] that also draws the touch indicator inside the
 * dialog's own window.
 *
 * WHY: a Compose Dialog creates a separate native window, so touches inside it never reach
 * `Activity.dispatchTouchEvent` and the Activity-level overlay cannot see them. Wrapping the
 * dialog content here keeps the fix in one place - every shared KhanaBook dialog wrapper can
 * switch from `Dialog(...)` to `TouchAwareDialog(...)` with no other change.
 *
 * The overlay is the last child of a [Box], so it paints above the dialog content. Its
 * `pointerInput` observes at the Initial pass and never consumes, and its View returns `false`
 * from `dispatchTouchEvent`, so dialog buttons, scrolling and dismiss-on-outside are unaffected.
 */
@Composable
fun TouchAwareDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box {
            content()
            TouchIndicatorOverlay(Modifier.matchParentSize())
        }
    }
}
