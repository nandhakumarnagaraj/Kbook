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
    MANUAL
}

data class PrintDispatchResult(
    val attempted: Int,
    val succeeded: Int,
    val failures: List<String>
)

@Singleton
class PrintRouter @Inject constructor(
    private val printerProfileRepository: PrinterProfileRepository,
    private val printerManager: BluetoothPrinterManager
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
        if (targets.isEmpty()) return PrintDispatchResult(0, 0, emptyList())

        var successCount = 0
        val failures = mutableListOf<String>()

        for (target in targets) {
            repeat(target.copies.coerceAtLeast(1)) {
                try {
                    printerManager.disconnect()
                    if (!printerManager.connect(target.macAddress)) {
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
                        successCount += 1
                    } else {
                        failures += "${target.name}: print failed"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed printing to ${target.name}", e)
                    failures += "${target.name}: ${e.message ?: "unexpected error"}"
                } finally {
                    printerManager.disconnect()
                }
            }
        }

        return PrintDispatchResult(
            attempted = targets.sumOf { it.copies.coerceAtLeast(1) },
            succeeded = successCount,
            failures = failures
        )
    }

    private suspend fun resolveTargets(
        restaurantProfile: RestaurantProfileEntity?,
        mode: PrintDispatchMode
    ): List<PrinterProfileEntity> {
        val stored = printerProfileRepository.getProfiles()
            .filter { it.enabled && it.macAddress.isNotBlank() }
            .filter { mode == PrintDispatchMode.MANUAL || it.autoPrint }
            .sortedBy { if (it.role == PrinterRole.KITCHEN.name) 0 else 1 }

        if (stored.isNotEmpty()) return stored

        if (restaurantProfile?.printerEnabled == true && !restaurantProfile.printerMac.isNullOrBlank()) {
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
            ).filter { mode == PrintDispatchMode.MANUAL || it.autoPrint }
        }

        return emptyList()
    }
}
