package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.util.InvoiceFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PrintDispatchMode {
    AUTO,
    MANUAL_RECEIPT_ONLY,
    MANUAL_KITCHEN_ONLY
}

data class PrintDispatchResult(
    val attempted: Int,
    val succeeded: Int,
    val successTargets: List<String>,
    val failures: List<String>,
    val kitchenQueued: Boolean = false,
    val kitchenQueueReason: String? = null
)

@Singleton
class PrintRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val printerProfileRepository: PrinterProfileRepository,
    private val printerManager: BluetoothPrinterManager,
    private val kitchenPrintQueueManager: KitchenPrintQueueManager
) {
    companion object {
        private const val TAG = "PrintRouter"
    }

    suspend fun printBill(
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        mode: PrintDispatchMode
    ): PrintDispatchResult {
        val resolvedTargets = resolveTargets(restaurantProfile, mode)
        val immediateTargets = resolvedTargets.toMutableList()
        var kitchenQueued = false
        var kitchenQueueReason: String? = null

        if (mode == PrintDispatchMode.AUTO) {
            val kitchenProfile = printerProfileRepository.getByRole(PrinterRole.KITCHEN.name)
            if (kitchenProfile?.enabled == true) {
                when {
                    kitchenProfile.macAddress.isBlank() -> {
                        kitchenPrintQueueManager.enqueueUnassigned(
                            bill.bill.id,
                            "kitchen printer not configured during billing"
                        )
                        kitchenQueued = true
                        kitchenQueueReason = "not_configured"
                        immediateTargets.removeAll { it.role == PrinterRole.KITCHEN.name }
                    }
                    !printerManager.isConnectedTo(kitchenProfile.macAddress) -> {
                        kitchenPrintQueueManager.enqueue(
                            bill.bill.id,
                            kitchenProfile.macAddress,
                            "kitchen printer offline during billing"
                        )
                        kitchenQueued = true
                        kitchenQueueReason = "offline"
                        immediateTargets.removeAll {
                            it.role == PrinterRole.KITCHEN.name &&
                                it.macAddress == kitchenProfile.macAddress
                        }
                    }
                }
            }
        }

        if (immediateTargets.isEmpty()) {
            return PrintDispatchResult(
                attempted = 0,
                succeeded = 0,
                successTargets = emptyList(),
                failures = emptyList(),
                kitchenQueued = kitchenQueued,
                kitchenQueueReason = kitchenQueueReason
            )
        }

        var successCount = 0
        val successTargets = mutableListOf<String>()
        val failures = mutableListOf<String>()

        for (target in immediateTargets) {
            val isKitchenTarget = target.role == PrinterRole.KITCHEN.name
            var claimedQueuedJob = false
            repeat(target.copies.coerceAtLeast(1)) {
                if (!claimedQueuedJob && isKitchenTarget && mode == PrintDispatchMode.AUTO) {
                    claimedQueuedJob = kitchenPrintQueueManager.claimPendingForDirectPrint(
                        bill.bill.id,
                        target.macAddress
                    )
                }
                try {
                    if (!printerManager.connect(target.macAddress)) {
                        val queued = maybeQueueKitchenRetry(
                            bill.bill.id,
                            target,
                            mode,
                            "connection failed",
                            incrementAttempts = !claimedQueuedJob
                        )
                        if (queued) {
                            kitchenQueued = true
                            kitchenQueueReason = kitchenQueueReason ?: "offline"
                        }
                        failures += "${target.name}: connection failed"
                        return@repeat
                    }
                    val printProfile = restaurantProfile?.copy(
                        paperSize = target.paperSize,
                        includeLogoInPrint = target.includeLogo
                    ) ?: return@repeat
                    val bytes = when (PrinterRole.fromValue(target.role)) {
                        PrinterRole.CUSTOMER -> InvoiceFormatter.formatForThermalPrinter(bill, printProfile, context)
                        PrinterRole.KITCHEN -> KitchenTicketFormatter.format(bill, restaurantProfile, target)
                    }
                    if (printerManager.printBytes(bytes)) {
                        maybeClearKitchenQueue(bill.bill.id, target)
                        successCount += 1
                        successTargets += target.role
                    } else {
                        val queued = maybeQueueKitchenRetry(
                            bill.bill.id,
                            target,
                            mode,
                            "print failed",
                            incrementAttempts = !claimedQueuedJob
                        )
                        if (queued) {
                            kitchenQueued = true
                            kitchenQueueReason = kitchenQueueReason ?: "print_failed"
                        }
                        failures += "${target.name}: print failed"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed printing to ${target.name}", e)
                    val queued = maybeQueueKitchenRetry(
                        bill.bill.id,
                        target,
                        mode,
                        e.message ?: "unexpected error",
                        incrementAttempts = !claimedQueuedJob
                    )
                    if (queued) {
                        kitchenQueued = true
                        kitchenQueueReason = kitchenQueueReason ?: "print_failed"
                    }
                    failures += "${target.name}: ${e.message ?: "unexpected error"}"
                }
            }
        }

        return PrintDispatchResult(
            attempted = immediateTargets.sumOf { it.copies.coerceAtLeast(1) },
            succeeded = successCount,
            successTargets = successTargets,
            failures = failures,
            kitchenQueued = kitchenQueued,
            kitchenQueueReason = kitchenQueueReason
        )
    }

    private suspend fun resolveTargets(
        restaurantProfile: RestaurantProfileEntity?,
        mode: PrintDispatchMode
    ): List<PrinterProfileEntity> {
        val stored = printerProfileRepository.getProfiles()
            .filter { it.enabled && it.macAddress.isNotBlank() }
            .filter { profile ->
                when (mode) {
                    PrintDispatchMode.AUTO ->
                        profile.role == PrinterRole.KITCHEN.name || profile.autoPrint
                    PrintDispatchMode.MANUAL_RECEIPT_ONLY -> profile.role == PrinterRole.CUSTOMER.name
                    PrintDispatchMode.MANUAL_KITCHEN_ONLY -> profile.role == PrinterRole.KITCHEN.name
                }
            }
            .sortedBy { if (it.role == PrinterRole.KITCHEN.name) 0 else 1 }

        if (stored.isNotEmpty()) return stored

        if (restaurantProfile?.printerEnabled == true &&
            !restaurantProfile.printerMac.isNullOrBlank() &&
            (mode == PrintDispatchMode.MANUAL_RECEIPT_ONLY || restaurantProfile.autoPrintOnSuccess)
        ) {
            return listOf(
                PrinterProfileEntity(
                    role = PrinterRole.CUSTOMER.name,
                    name = restaurantProfile.printerName ?: "Default Printer",
                    macAddress = restaurantProfile.printerMac ?: "",
                    enabled = true,
                    autoPrint = restaurantProfile.autoPrintOnSuccess,
                    paperSize = restaurantProfile.paperSize,
                    includeLogo = restaurantProfile.includeLogoInPrint
                )
            )
        }

        return emptyList()
    }

    private suspend fun maybeQueueKitchenRetry(
        billId: Long,
        target: PrinterProfileEntity,
        mode: PrintDispatchMode,
        error: String,
        incrementAttempts: Boolean
    ): Boolean {
        if (mode == PrintDispatchMode.AUTO && target.role == PrinterRole.KITCHEN.name) {
            kitchenPrintQueueManager.enqueue(
                billId = billId,
                printerMac = target.macAddress,
                error = error,
                incrementAttempts = incrementAttempts
            )
            return true
        }
        return false
    }

    private suspend fun maybeClearKitchenQueue(
        billId: Long,
        target: PrinterProfileEntity
    ) {
        if (target.role == PrinterRole.KITCHEN.name) {
            kitchenPrintQueueManager.markPrinted(billId, target.macAddress)
        }
    }
}
