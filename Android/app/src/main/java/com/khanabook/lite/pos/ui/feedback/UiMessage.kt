package com.khanabook.lite.pos.ui.feedback

import com.khanabook.lite.pos.ui.designsystem.ToastKind

data class UiMessage(
    val message: String,
    val kind: ToastKind
)
