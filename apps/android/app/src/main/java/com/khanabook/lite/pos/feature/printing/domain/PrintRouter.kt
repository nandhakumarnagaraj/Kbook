package com.khanabook.lite.pos.feature.printing.domain

import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileRepository
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import com.khanabook.lite.pos.feature.printing.data.KotEventEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import com.khanabook.lite.pos.feature.printing.domain.connectionTargetKey
import com.khanabook.lite.pos.feature.printing.domain.isConnectionConfigured
import com.khanabook.lite.pos.feature.billing.domain.InvoiceFormatter
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
    private val printerTransport: PrinterTransportDispatcher,
    private val kitchenPrintQueueManager: KitchenPrintQueueManager,
    private val billDao: BillDao,
    private val kotEventDao: KotEventDao,
    private val sessionManager: SessionManager
) {
    internal constructor(
        context: Context,
        printerProfileRepository: PrinterProfileRepository,
        printerManager: BluetoothPrinterManager,
        kitchenPrintQueueManager: KitchenPrintQueueManager,
        billDao: BillDao,
        kotEventDao: KotEventDao,
        sessionManager: SessionManager
    ) : this(
        context,
        printerProfileRepository,
        PrinterTransportDispatcher(
            BluetoothPrinterTransport(printerManager),
            WifiPrinterTransport(NetworkPrinterScanner(appContext = null)),
            UsbPrinterTransport(context)
        ),
        kitchenPrintQueueManager,
        billDao,
        kotEventDao,
        sessionManager
    )

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
                if (!kitchenProfile.isConnectionConfigured()) {
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
                // Unprinted KOT events for this bill (AUTO mode only). When every item
                // is already flagged sentToKot but events are still unprinted, the
                // pending events are the real work — they hold the exact snapshot the
                // kitchen has not seen yet (e.g. an ADD saved by a crash-prone path).
                val unprintedKotEvents = if (isKitchenTarget && mode == PrintDispatchMode.AUTO) {
                    // REPRINT events are recorded pre-printed; filtering them keeps the
                    // combined-selection logic from treating audit rows as work to deliver.
                    bill.bill.publicToken?.takeIf { it.isNotBlank() }
                        ?.let { kotEventDao.getUnprintedEventsForBill(it) }
                        ?.filter { it.eventType != KotEventType.REPRINT }
                        ?: emptyList()
                } else {
                    emptyList()
                }
                val itemsToPrint = if (isKitchenTarget) {
                    if (mode == PrintDispatchMode.AUTO) {
                        val unsent = bill.items.filter { !it.sentToKot }
                        if (unsent.isNotEmpty() || unprintedKotEvents.isEmpty()) unsent else emptyList()
                    } else {
                        bill.items
                    }
                } else {
                    emptyList()
                }

                if (isKitchenTarget && itemsToPrint.isEmpty() && unprintedKotEvents.isEmpty()) {
                    return@async Triple(target.role, true, "")
                }

                // ── KOT Terminal Ownership Guard ─────────────────────────────────────────
                // Bills pulled from another terminal must NOT print KOT on this device,
                // whether the KOT is fired automatically (on billing) or reprinted manually
                // from the order detail screen. Each terminal only fires KOT events for bills
                // it owns; a pulled bill is read-only history.
                //
                // Ownership is keyed on the owning TERMINAL (currentOwnerTerminalId), not the
                // physical deviceId. A recovered/replaced device keeps the same terminal id,
                // so its own in-progress bills remain KOT-printable after a device migration.
                if (isKitchenTarget && (mode == PrintDispatchMode.AUTO || mode == PrintDispatchMode.MANUAL_KITCHEN_ONLY)) {
                    val printBill = bill.bill
                    val locallyOwned =
                        printBill.recordScope == "terminal_operational" && printBill.recordOrigin == "local_created"
                    val localTerminalId = sessionManager.getTerminalId()
                    val billOwnerTerminal = printBill.currentOwnerTerminalId
                        ?.takeIf { it.isNotBlank() }
                        ?: printBill.createdTerminalId?.takeIf { it.isNotBlank() }
                        ?: printBill.terminalId?.takeIf { it.isNotBlank() }
                    // Only block when we can positively establish that a DIFFERENT terminal
                    // owns this bill. Legacy records with no terminal identity stay printable
                    // (they were created before terminal provisioning existed).
                    val foreignTerminal = localTerminalId != null &&
                        billOwnerTerminal != null &&
                        billOwnerTerminal != localTerminalId
                    if (!locallyOwned || foreignTerminal) {
                        Log.d(
                            TAG,
                            "Skipping KOT: not locally owned (scope=${printBill.recordScope}, " +
                                "origin=${printBill.recordOrigin}, ownerTerminal=$billOwnerTerminal, localTerminal=$localTerminalId)"
                        )
                        return@async Triple(target.role, true, "")
                    }
                }
                // ────────────────────────────────────────────────────────────────────────

                // Manual kitchen reprint = deliberate full-copy delivery to the kitchen.
                // Record it as a printed REPRINT event so the audit trail and the
                // "kitchen already received this order" check (used by CANCEL notices)
                // reflect the re-delivery.
                if (isKitchenTarget && mode == PrintDispatchMode.MANUAL_KITCHEN_ONLY) {
                    recordReprintAudit(bill)
                }

                if (!isKitchenTarget && mode == PrintDispatchMode.AUTO && bill.bill.orderStatus.equals("draft", ignoreCase = true)) {
                    // Do not auto-print customer receipts for draft orders
                    return@async Triple(target.role, true, "")
                }

                var claimedQueuedJob = false
                var success = false
                var errorMsg = ""
                // Events rendered on the combined ticket (if a batch group was dispatched).
                // Set inside the repeat loop, consumed by the success bookkeeping below.
                var dispatchedGroup: List<KotEventEntity>? = null
                // Pure-VOID batch (cart save that only removed items): no unsent bill rows,
                // but unprinted events still need the combined ticket.
                val batchWorkRemaining = isKitchenTarget &&
                    mode == PrintDispatchMode.AUTO &&
                    itemsToPrint.isEmpty() &&
                    unprintedKotEvents.isNotEmpty()

                repeat(target.copies.coerceAtLeast(1)) {
                    if (!claimedQueuedJob && isKitchenTarget && mode == PrintDispatchMode.AUTO) {
                        claimedQueuedJob = kitchenPrintQueueManager.claimPendingForDirectPrint(
                            bill.bill.id,
                            target.connectionTargetKey()
                        )
                    }
                    try {
                        val printProfile = restaurantProfile?.copy(
                            paperSize = target.paperSize,
                            includeLogoInPrint = target.includeLogo
                        ) ?: return@repeat
                        val bytes = when (PrinterRole.fromValue(target.role)) {
                            PrinterRole.CUSTOMER -> InvoiceFormatter.formatForThermalPrinter(bill, printProfile, context)
                            PrinterRole.KITCHEN -> {
                                // AUTO dispatch renders the immutable EVENT snapshot(s) (NEW/ADD/
                                // VOID as recorded) so a late ticket can never leak items added
                                // after the event. Manual reprint keeps the live full-order copy.
                                // Batched cart saves produce several events sharing an eventToken;
                                // they render as ONE combined ticket.
                                val batchEvents = if (mode == PrintDispatchMode.AUTO) {
                                    resolveEventsForBatch(itemsToPrint, unprintedKotEvents)
                                } else emptyList()
                                if (batchEvents.isNotEmpty()) {
                                    dispatchedGroup = batchEvents
                                    KitchenTicketFormatter.formatCombinedTicket(
                                        bill,
                                        restaurantProfile,
                                        target,
                                        batchEvents.map { ev ->
                                            KotTicketSection(
                                                eventType = ev.eventType,
                                                itemSnapshotJson = ev.itemSnapshotJson,
                                                eventTimeMs = ev.createdAt,
                                                kotRevision = ev.kotRevision
                                            )
                                        }
                                    )
                                } else {
                                    KitchenTicketFormatter.format(bill, restaurantProfile, target, itemsToPrint)
                                }
                            }
                        }
                        if (printerTransport.print(target, bytes)) {
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
                    if (isKitchenTarget && mode == PrintDispatchMode.AUTO && (itemsToPrint.isNotEmpty() || batchWorkRemaining)) {
                        maybeClearKitchenQueue(bill.bill.id, target)
                        // Mark ONLY the events this ticket actually rendered — never the
                        // whole backlog. When a batch group was dispatched, every event in
                        // the group is on the paper, so the whole group is marked printed.
                        // Otherwise fall back to the exact item-id match for single events.
                        val dispatched = dispatchedGroup
                        if (dispatched != null) {
                            dispatched.forEach { ev ->
                                kotEventDao.markPrinted(ev.publicToken, ev.kotRevision)
                            }
                            // A direct print here covered everything the bill had pending,
                            // including UNASSIGNED fallback jobs (e.g. batched VOIDs) whose
                            // stored MAC can never match this printer — ack them wholesale
                            // so the 30s flush cannot re-print the same ticket.
                            kitchenPrintQueueManager.ackAllPendingForBill(bill.bill.id)
                        } else {
                            bill.bill.publicToken?.let { token ->
                                val printedItemIds = itemsToPrint.mapNotNull { it.id }.toSet()
                                kotEventDao.getUnprintedEventsForBill(token)
                                    .filter { ev -> parseSnapshotItemIds(ev.itemSnapshotJson) == printedItemIds }
                                    .forEach { ev -> kotEventDao.markPrinted(ev.publicToken, ev.kotRevision) }
                            }
                        }
                        if (itemsToPrint.isNotEmpty()) {
                            billDao.markItemsSentToKot(itemsToPrint.map { it.id }, bill.bill.restaurantId)
                        }
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
            .filter { it.enabled && it.isConnectionConfigured() }
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
                printerMac = target.connectionTargetKey(),
                error = error,
                incrementAttempts = incrementAttempts,
                publicToken = latestKotEventPublicToken(bill),
                kotRevision = latestKotEventRevision(bill)
            )
            return true
        }
        return false
    }

    /** Appends an already-printed REPRINT audit event for a manual kitchen reprint. */
    private suspend fun recordReprintAudit(bill: BillWithItems) {
        val token = bill.bill.publicToken?.takeIf { it.isNotBlank() } ?: return
        val items = bill.items.filter { !it.isDeleted }
        if (items.isEmpty()) return
        val revision = (kotEventDao.getMaxRevisionForBill(token) + 1L).toString()
        kotEventDao.insert(
            KotEventEntity(
                publicToken = token,
                kotRevision = revision,
                eventType = KotEventType.REPRINT,
                itemSnapshotJson = serializeItemSnapshot(items),
                originatingDeviceId = sessionManager.getDeviceId(),
                isPrinted = true,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun serializeItemSnapshot(items: List<BillItemEntity>): String =
        org.json.JSONArray().apply {
            items.forEach { item ->
                put(
                    org.json.JSONObject().apply {
                        put("id", item.id)
                        put("menuItemId", item.menuItemId ?: org.json.JSONObject.NULL)
                        put("itemName", item.itemName)
                        put("variantId", item.variantId ?: org.json.JSONObject.NULL)
                        put("variantName", item.variantName ?: org.json.JSONObject.NULL)
                        put("price", item.price)
                        put("quantity", item.quantity)
                        put("itemTotal", item.itemTotal)
                        put("specialInstruction", item.specialInstruction ?: org.json.JSONObject.NULL)
                    }
                )
            }
        }.toString()

    /**
     * Picks the KOT event(s) to render for this dispatch. Events recorded by one user
     * save share a non-blank eventToken (the batch token) and render together as ONE
     * combined ticket. Legacy single events (no batch token) keep the previous rule:
     * exact item-id match over the newest unprinted event.
     */
    private fun resolveEventsForBatch(
        items: List<BillItemEntity>,
        unprinted: List<KotEventEntity>
    ): List<KotEventEntity> {
        if (unprinted.isEmpty()) return emptyList()
        val newest = unprinted.last()
        val batchToken = newest.eventToken?.takeIf { it.isNotBlank() }
        if (batchToken == null) {
            val itemIds = items.mapNotNull { it.id }.toSet()
            val matched = unprinted.lastOrNull { ev -> parseSnapshotItemIds(ev.itemSnapshotJson) == itemIds }
            return listOfNotNull(matched ?: newest)
        }
        return unprinted.filter { it.eventToken == batchToken }.ifEmpty { listOf(newest) }
    }

    /** Gson-based (org.json is stubbed in JVM unit tests). */
    private fun parseSnapshotItemIds(snapshotJson: String): Set<Long> =
        try {
            val root = com.google.gson.JsonParser.parseString(snapshotJson).asJsonArray
            root.mapNotNull { el ->
                val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: return@mapNotNull null
                id.takeIf { it > 0 }
            }.toSet()
        } catch (_: Exception) {
            emptySet()
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
            kitchenPrintQueueManager.markPrinted(billId, target.connectionTargetKey())
        }
    }
}
