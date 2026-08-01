package com.khanabook.lite.pos.ui.feedback

import com.khanabook.lite.pos.ui.designsystem.ToastKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PrintFeedbackTest {
    @Test
    fun `successful print uses success feedback`() {
        assertEquals(ToastKind.Success, printFeedbackKind("Receipt reprinted successfully."))
    }

    @Test
    fun `printer fallback and partial results use warning feedback`() {
        assertEquals(ToastKind.Warning, printFeedbackKind("Printer offline. Opening PDF viewer."))
        assertEquals(ToastKind.Warning, printFeedbackKind("Receipt reprinted with some failures."))
    }

    @Test
    fun `terminal print failure uses error feedback`() {
        assertEquals(ToastKind.Error, printFeedbackKind("KDS reprint failed."))
        assertEquals(ToastKind.Error, printFeedbackKind("Unable to open invoice."))
    }
}
