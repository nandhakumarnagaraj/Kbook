package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.util.InvoiceFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class PrintDispatchMode {
    AUTO,
    MANUAL_RECEIPT_ONLY
}

data class PrintDispatchResult(
    val attempted: Int,
    val succeeded: Int,
    val successTargets: List<String>,
    val failures: List<String>
)

@Singleton
class PrintRouter @Inject constructor(
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
        val targets = resolveTargets(restaurantProfile, mode)
        ensureKitchenQueueFallback(bill, mode, targets)
        if (targets.isEmpty()) return PrintDispatchResult(0, 0, emptyList(), emptyList())

        var successCount = 0
        val successTargets = mutableListOf<String>()
        val failures = mutableListOf<String>()

        for (target in targets) {
            repeat(target.copies.coerceAtLeast(1)) {
                try {
                    printerManager.disconnect()
                    if (!printerManager.connect(target.macAddress)) {
                        maybeQueueKitchenRetry(bill.bill.id, target, mode, "connection failed")
                        failures += "${target.name}: connection failed"
                        return@repeat
                    }
                    val printProfile = restaurantProfile?.copy(
                        paperSize = target.paperSize,
                        includeLogoInPrint = target.includeLogo
                    ) ?: return@repeat
                    val bytes = when (PrinterRole.fromValue(target.role)) {
                        PrinterRole.CUSTOMER -> InvoiceFormatter.formatForThermalPrinter(bill, printProfile)
                        PrinterRole.KITCHEN -> KitchenTicketFormatter.format(bill, restaurantProfile, target)
                    }
                    if (printerManager.printBytes(bytes)) {
                        maybeClearKitchenQueue(bill.bill.id, target)
                        successCount += 1
                        successTargets += target.role
                    } else {
                        maybeQueueKitchenRetry(bill.bill.id, target, mode, "print failed")
                        failures += "${target.name}: print failed"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed printing to ${target.name}", e)
                    maybeQueueKitchenRetry(
                        bill.bill.id,
                        target,
                        mode,
                        e.message ?: "unexpected error"
                    )
                    failures += "${target.name}: ${e.message ?: "unexpected error"}"
                } finally {
                    printerManager.disconnect()
                }
            }
        }

        return PrintDispatchResult(
            attempted = targets.sumOf { it.copies.coerceAtLeast(1) },
            succeeded = successCount,
            successTargets = successTargets,
            failures = failures
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
        error: String
    ) {
        if (mode == PrintDispatchMode.AUTO && target.role == PrinterRole.KITCHEN.name) {
            kitchenPrintQueueManager.enqueue(billId, target.macAddress, error)
        }
    }

    private suspend fun maybeClearKitchenQueue(
        billId: Long,
        target: PrinterProfileEntity
    ) {
        if (target.role == PrinterRole.KITCHEN.name) {
            kitchenPrintQueueManager.markPrinted(billId, target.macAddress)
        }
    }

    private suspend fun ensureKitchenQueueFallback(
        bill: BillWithItems,
        mode: PrintDispatchMode,
        targets: List<PrinterProfileEntity>
    ) {
        if (mode != PrintDispatchMode.AUTO) return
        if (targets.any { it.role == PrinterRole.KITCHEN.name }) return

        val kitchenProfile = printerProfileRepository.getByRole(PrinterRole.KITCHEN.name)
        if (kitchenProfile?.enabled == true && kitchenProfile.macAddress.isNotBlank()) {
            kitchenPrintQueueManager.enqueue(
                bill.bill.id,
                kitchenProfile.macAddress,
                "kitchen printer unavailable during billing"
            )
        } else {
            kitchenPrintQueueManager.enqueueUnassigned(
                bill.bill.id,
                "kitchen printer not configured during billing"
            )
        }
    }
}
