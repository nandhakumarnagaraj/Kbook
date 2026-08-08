package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.domain.manager.InvoicePDFGenerator
import com.khanabook.lite.pos.domain.manager.PrintDispatchMode
import com.khanabook.lite.pos.domain.manager.PrintDispatchResult
import com.khanabook.lite.pos.domain.manager.PrintRouter
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages print dispatch (receipt + kitchen) and print status messaging.
 * Extracted from BillingViewModel to isolate printing concerns.
 *
 * This class is NOT a ViewModel — it's a coordinator owned by BillingViewModel.
 */
class PrintCoordinator(
    private val appContext: Context,
    private val printRouter: PrintRouter,
    private val kitchenPrintQueueRepository: KitchenPrintQueueRepository
) {
    private val tag = "PrintCoordinator"
    private val kitchenPrintInFlight = AtomicBoolean(false)

    private val _printStatus = MutableStateFlow<String?>(null)
    val printStatus: StateFlow<String?> = _printStatus

    private val _receiptPrinting = MutableStateFlow(false)
    val receiptPrinting: StateFlow<Boolean> = _receiptPrinting

    private val _kitchenPrinting = MutableStateFlow(false)
    val kitchenPrinting: StateFlow<Boolean> = _kitchenPrinting

    fun clearStatus() {
        _printStatus.value = null
    }

    fun setStatus(message: String?) {
        _printStatus.value = message
    }

    /**
     * Processes a print result from auto-print (dispatched via PrintService).
     * Updates the status message based on what succeeded/failed/queued.
     */
    suspend fun handleAutoPrintResult(billId: Long, result: PrintDispatchResult) {
        val kitchenQueued = result.kitchenQueued ||
            kitchenPrintQueueRepository.hasPendingForBill(billId)

        withContext(Dispatchers.Main) {
            _printStatus.value = when {
                result.attempted == 0 -> {
                    if (kitchenQueued) {
                        when (result.kitchenQueueReason) {
                            "not_configured" -> "Kitchen printer not configured. KDS queued."
                            else -> "Kitchen printer offline. KDS queued."
                        }
                    } else {
                        "No auto-print target configured."
                    }
                }
                kitchenQueued && result.succeeded > 0 -> {
                    if (result.successTargets.contains(PrinterRole.KITCHEN.name)) {
                        "Printed Kitchen ticket"
                    } else {
                        "Printed customer receipt. KDS queued."
                    }
                }
                kitchenQueued -> {
                    when (result.kitchenQueueReason) {
                        "not_configured" -> "Kitchen printer not configured. KDS queued."
                        else -> "Kitchen printer offline. KDS queued."
                    }
                }
                result.failures.isEmpty() -> {
                    when {
                        result.successTargets.contains(PrinterRole.KITCHEN.name) ->
                            "Printed Kitchen ticket"
                        else -> buildPrintStatusMessage("Printed", result.successTargets)
                    }
                }
                result.succeeded > 0 -> buildPartialPrintStatus(result)
                else -> "Printing failed for all configured printers."
            }
        }
    }

    /**
     * Returns an error message if auto-print completely failed (for the caller to set on _error).
     */
    fun getAutoPrintError(result: PrintDispatchResult): String? {
        return if (result.attempted > 0 && result.succeeded == 0 && result.failures.isNotEmpty()) {
            "Auto-print failed. Bill saved."
        } else null
    }

    suspend fun printReceipt(
        bill: BillWithItems,
        profile: RestaurantProfileEntity?,
        onError: (String) -> Unit
    ) {
        if (_receiptPrinting.value) return
        if (bill.bill.orderStatus.equals(OrderStatus.CANCELLED.dbValue, ignoreCase = true)) {
            onError("Cannot print receipt for a cancelled order.")
            return
        }
        if (profile == null) {
            onError("Restaurant profile not loaded.")
            return
        }

        _receiptPrinting.value = true
        try {
            val result = printRouter.printBill(bill, profile, PrintDispatchMode.MANUAL_RECEIPT_ONLY)
            if (result.attempted == 0 && result.failures.isEmpty()) {
                openBillPdfFallback(bill, profile, appContext.getString(R.string.toast_printer_opening_pdf), onError)
            } else if (result.failures.isNotEmpty()) {
                if (result.succeeded > 0) {
                    withContext(Dispatchers.Main) {
                        _printStatus.value = "Receipt reprinted with some failures."
                    }
                } else {
                    openBillPdfFallback(bill, profile, appContext.getString(R.string.toast_printer_opening_pdf), onError)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _printStatus.value = "Receipt reprinted successfully."
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Manual receipt print failed", e)
            openBillPdfFallback(bill, profile, appContext.getString(R.string.toast_printer_opening_pdf), onError)
        } finally {
            _receiptPrinting.value = false
        }
    }

    suspend fun printKitchenTicket(
        bill: BillWithItems,
        profile: RestaurantProfileEntity?,
        onError: (String) -> Unit
    ) {
        if (bill.bill.orderStatus.equals(OrderStatus.CANCELLED.dbValue, ignoreCase = true)) {
            onError("Cannot print KDS for a cancelled order.")
            return
        }
        if (profile == null) {
            onError("Restaurant profile not loaded.")
            return
        }
        if (!kitchenPrintInFlight.compareAndSet(false, true)) return

        _kitchenPrinting.value = true
        try {
            val result = printRouter.printBill(bill, profile, PrintDispatchMode.MANUAL_KITCHEN_ONLY)
            withContext(Dispatchers.Main) {
                _printStatus.value = when {
                    result.attempted == 0 -> "No KDS printer configured."
                    result.failures.isNotEmpty() && result.succeeded > 0 -> "KDS reprinted with some failures."
                    result.failures.isNotEmpty() -> "KDS reprint failed."
                    else -> "KDS reprinted successfully."
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Manual kitchen print failed", e)
            withContext(Dispatchers.Main) {
                _printStatus.value = UserMessageSanitizer.sanitize(e, "Unable to print kitchen ticket.")
            }
        } finally {
            kitchenPrintInFlight.set(false)
            _kitchenPrinting.value = false
        }
    }

    private suspend fun openBillPdfFallback(
        bill: BillWithItems,
        profile: RestaurantProfileEntity?,
        statusMessage: String,
        onError: (String) -> Unit
    ) {
        val pdfIntent = try {
            withContext(Dispatchers.IO) {
                val pdfFile = InvoicePDFGenerator(appContext).generatePDF(bill, profile)
                val pdfUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.provider",
                    pdfFile
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(pdfUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(UserMessageSanitizer.sanitize(e, "Unable to prepare invoice."))
                _printStatus.value = null
            }
            return
        }

        withContext(Dispatchers.Main) {
            _printStatus.value = statusMessage
            try {
                appContext.startActivity(Intent.createChooser(pdfIntent, "Open PDF to Print").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                _printStatus.value = null
                onError(UserMessageSanitizer.sanitize(e, "Unable to open invoice."))
            }
        }
    }

    private fun buildPrintStatusMessage(prefix: String, targets: List<String>): String {
        val normalized = targets.distinct().map {
            when (it) {
                PrinterRole.CUSTOMER.name -> "customer receipt"
                PrinterRole.KITCHEN.name -> "kitchen ticket"
                else -> it.lowercase()
            }
        }
        return "$prefix ${normalized.joinToString(" and ")}."
    }

    private fun buildPartialPrintStatus(result: PrintDispatchResult): String {
        val success = buildPrintStatusMessage("Printed", result.successTargets)
        val failureCount = result.failures.size
        return "$success $failureCount printer task${if (failureCount == 1) "" else "s"} failed."
    }
}
