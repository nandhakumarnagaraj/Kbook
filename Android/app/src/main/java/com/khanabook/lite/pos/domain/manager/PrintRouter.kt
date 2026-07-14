package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.KotEventDao
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.util.InvoiceFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asSharedFlow
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
    private val kitchenPrintQueueManager: KitchenPrintQueueManager,
    private val billDao: BillDao,
    private val kotEventDao: KotEventDao,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "PrintRouter"
    }

    private val _printResults = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Long, PrintDispatchResult>>(extraBufferCapacity = 16)
    val printResults = _printResults.asSharedFlow()

    suspend fun printBill(
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        mode: PrintDispatchMode
    ): PrintDispatchResult = coroutineScope {
        val resolvedTargets = resolveTargets(restaurantProfile, mode)
        val immediateTargets = resolvedTargets.toMutableList()
        var kitchenQueued = false
        var kitchenQueueReason: String? = null

        if (mode == PrintDispatchMode.AUTO) {
            val kitchenProfile = printerProfileRepository.getByRole(PrinterRole.KITCHEN.name)
            if (kitchenProfile?.enabled == true) {
                if (kitchenProfile.macAddress.isBlank()) {
                    kitchenPrintQueueManager.enqueueUnassigned(
                        bill.bill.id,
                        "kitchen printer not configured during billing",
                        publicToken = latestKotEventPublicToken(bill),
                        kotRevision = latestKotEventRevision(bill)
                    )
                    kitchenQueued = true
                    kitchenQueueReason = "not_configured"
                    immediateTargets.removeAll { it.role == PrinterRole.KITCHEN.name }
                }
            }
        }

        if (immediateTargets.isEmpty()) {
            return@coroutineScope PrintDispatchResult(
                attempted = 0,
                succeeded = 0,
                successTargets = emptyList(),
                failures = emptyList(),
                kitchenQueued = kitchenQueued,
                kitchenQueueReason = kitchenQueueReason
            )
        }

        val printJobs = immediateTargets.map { target ->
            async(Dispatchers.IO) {
                val isKitchenTarget = target.role == PrinterRole.KITCHEN.name
                val itemsToPrint = if (isKitchenTarget) {
                    if (mode == PrintDispatchMode.AUTO) {
                        bill.items.filter { !it.sentToKot }
                    } else {
                        bill.items
                    }
                } else {
                    emptyList()
                }

                if (isKitchenTarget && itemsToPrint.isEmpty()) {
                    return@async Triple(target.role, true, "")
                }

                // ── KOT Device Ownership Guard ──────────────────────────────────────────
                // Bills pulled from another terminal must NOT print KOT on this device,
                // whether the KOT is fired automatically (on billing) or reprinted manually
                // from the order detail screen. Each terminal only fires KOT events for bills
                // it originated; a pulled bill is read-only history.
                if (isKitchenTarget && (mode == PrintDispatchMode.AUTO || mode == PrintDispatchMode.MANUAL_KITCHEN_ONLY)) {
                    val printBill = bill.bill
                    val locallyOwned =
                        printBill.recordScope == "terminal_operational" && printBill.recordOrigin == "local_created"
                    val localDeviceId = sessionManager.getDeviceId()
                    // Reject KOT for read-only server/marketplace history, and for bills that
                    // originated on a different physical device. A terminal may only fire KOT
                    // for its own locally-owned operational bills.
                    if (!locallyOwned || (printBill.deviceId != null && printBill.deviceId != localDeviceId)) {
                        Log.d(
                            TAG,
                            "Skipping KOT: not locally owned (scope=${printBill.recordScope}, " +
                                "origin=${printBill.recordOrigin}, device=${printBill.deviceId}, local=$localDeviceId)"
                        )
                        return@async Triple(target.role, true, "")
                    }
                }
                // ────────────────────────────────────────────────────────────────────────

                if (!isKitchenTarget && mode == PrintDispatchMode.AUTO && bill.bill.orderStatus.equals("draft", ignoreCase = true)) {
                    // Do not auto-print customer receipts for draft orders
                    return@async Triple(target.role, true, "")
                }

                var claimedQueuedJob = false
                var success = false
                var errorMsg = ""

                repeat(target.copies.coerceAtLeast(1)) {
                    if (!claimedQueuedJob && isKitchenTarget && mode == PrintDispatchMode.AUTO) {
                        claimedQueuedJob = kitchenPrintQueueManager.claimPendingForDirectPrint(
                            bill.bill.id,
                            target.macAddress
                        )
                    }
                    try {
                        if (!printerManager.connect(target.macAddress)) {
                            errorMsg = "connection failed"
                            return@repeat
                        }
                        val printProfile = restaurantProfile?.copy(
                            paperSize = target.paperSize,
                            includeLogoInPrint = target.includeLogo
                        ) ?: return@repeat
                        val bytes = when (PrinterRole.fromValue(target.role)) {
                            PrinterRole.CUSTOMER -> InvoiceFormatter.formatForThermalPrinter(bill, printProfile, context)
                            PrinterRole.KITCHEN -> KitchenTicketFormatter.format(bill, restaurantProfile, target, itemsToPrint)
                        }
                        if (printerManager.printBytesTo(target.macAddress, bytes)) {
                            success = true
                        } else {
                            errorMsg = "print failed"
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed printing to ${target.name}", e)
                        errorMsg = e.message ?: "unexpected error"
                    }
                }

                if (success) {
                    if (isKitchenTarget && mode == PrintDispatchMode.AUTO && itemsToPrint.isNotEmpty()) {
                        maybeClearKitchenQueue(bill.bill.id, target)
                        bill.bill.publicToken?.let { kotEventDao.markUnprintedEventsPrinted(it) }
                        billDao.markItemsSentToKot(itemsToPrint.map { it.id }, bill.bill.restaurantId)
                    } else {
                        maybeClearKitchenQueue(bill.bill.id, target)
                    }
                } else {
                    maybeQueueKitchenRetry(
                        bill,
                        target,
                        mode,
                        errorMsg,
                        incrementAttempts = !claimedQueuedJob
                    )
                }
                Triple(target.role, success, errorMsg)
            }
        }

        val completedJobs = printJobs.awaitAll()
        val successTargets = completedJobs.filter { it.second }.map { it.first }
        val successCount = successTargets.size
        val failures = completedJobs.filter { !it.second }.map { "${it.first}: ${it.third}" }

        if (failures.any { it.startsWith(PrinterRole.KITCHEN.name) } && mode == PrintDispatchMode.AUTO) {
            kitchenQueued = true
            kitchenQueueReason = kitchenQueueReason ?: "print_failed"
        }

        val result = PrintDispatchResult(
            attempted = immediateTargets.sumOf { it.copies.coerceAtLeast(1) },
            succeeded = successCount,
            successTargets = successTargets,
            failures = failures,
            kitchenQueued = kitchenQueued,
            kitchenQueueReason = kitchenQueueReason
        )
        _printResults.emit(Pair(bill.bill.id, result))
        result
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
        bill: BillWithItems,
        target: PrinterProfileEntity,
        mode: PrintDispatchMode,
        error: String,
        incrementAttempts: Boolean
    ): Boolean {
        if (mode == PrintDispatchMode.AUTO && target.role == PrinterRole.KITCHEN.name) {
            kitchenPrintQueueManager.enqueue(
                billId = bill.bill.id,
                printerMac = target.macAddress,
                error = error,
                incrementAttempts = incrementAttempts,
                publicToken = latestKotEventPublicToken(bill),
                kotRevision = latestKotEventRevision(bill)
            )
            return true
        }
        return false
    }

    private suspend fun latestKotEventPublicToken(bill: BillWithItems): String? =
        bill.bill.publicToken?.takeIf { it.isNotBlank() }

    private suspend fun latestKotEventRevision(bill: BillWithItems): String? {
        val publicToken = latestKotEventPublicToken(bill) ?: return null
        return kotEventDao.getLatestUnprintedEvent(publicToken)?.kotRevision
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
