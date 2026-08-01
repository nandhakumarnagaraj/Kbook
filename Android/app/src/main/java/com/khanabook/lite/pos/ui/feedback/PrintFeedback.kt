package com.khanabook.lite.pos.ui.feedback

import com.khanabook.lite.pos.ui.designsystem.ToastKind

fun printFeedbackKind(message: String): ToastKind {
    val normalized = message.lowercase()
    return when {
        listOf("failed", "unable", "couldn't", "cannot").any(normalized::contains) ->
            ToastKind.Error
        listOf("not configured", "offline", "opening pdf", "some failures", "no printer").any(normalized::contains) ->
            ToastKind.Warning
        else -> ToastKind.Success
    }
}
