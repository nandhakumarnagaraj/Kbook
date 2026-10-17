package com.khanabook.lite.pos.core.feedback

import com.khanabook.lite.pos.core.designsystem.ToastKind

data class UiMessage(
    val message: String,
    val kind: ToastKind
)
