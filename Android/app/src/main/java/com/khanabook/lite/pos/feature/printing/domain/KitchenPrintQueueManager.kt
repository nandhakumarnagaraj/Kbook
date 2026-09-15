package com.khanabook.lite.pos.feature.printing.domain

import android.util.Log
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueRepository
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileRepository
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import com.khanabook.lite.pos.feature.printing.domain.connectionTargetKey
import com.khanabook.lite.pos.feature.printing.domain.isConnectionConfigured
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class KitchenPrintQueueManager @Inject constructor(
    private val queueRepository: KitchenPrintQueueRepository,
    private val billRepository: BillRepository,
    private val restaurantRepository: RestaurantRepository,
    private val printerProfileRepository: PrinterProfileRepository,
    private val printerManager: BluetoothPrinterManager,
    private val printerTransport: PrinterTransportDispatcher,
    private val kotEventDao: KotEventDao
) {
    internal constructor(
        queueRepository: KitchenPrintQueueRepository,
        billRepository: BillRepository,
        restaurantRepository: RestaurantRepository,
        printerProfileRepository: PrinterProfileRepository,
        printerManager: BluetoothPrinterManager,
        kotEventDao: KotEventDao
    ) : this(
        queueRepository,
        billRepository,
        restaurantRepository,
        printerProfileRepository,
        printerManager,
        PrinterTransportDispatcher(
            BluetoothPrinterTransport(printerManager),
            WifiPrinterTransport()
        ),
        kotEventDao
    )

    companion object {
        private const val TAG = "KitchenPrintQueue"

        /** Failed kitchen tickets are retried on this cadence until the printer is reachable. */
        private const val RETRY_INTERVAL_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()

    init {
        scope.launch {
            printerManager.connectedDeviceEvents.collectLatest { printerMac ->
                flushPendingForPrinter(printerMac)
            }
        }

        scope.launch {
            kotlinx.coroutines.delay(2000)
            val mac = printerManager.connectedDeviceMac.value ?: return@launch
            if (mac.isNotBlank()) flushPendingForPrinter(mac)
        }

        scope.launch {
            kotlinx.coroutines.delay(2000)
            val kitchen = printerProfileRepository.getByRole(PrinterRole.KITCHEN.name)
            if (kitchen?.enabled == true && kitchen.isConnectionConfigured()) {
                flushPendingForPrinter(kitchen.connectionTargetKey())
            }
        }

        // Periodic safety net: if a ticket was queued while the kitchen printer
        // was offline it would otherwise sit until someone manually reconnects.
        // The retry is guarded by mutex + claim checks so it cannot race with a
        // concurrent direct print.
        scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(RETRY_INTERVAL_MS)
                flushAllPending()
            }
        }
    }

    fun initialize() = Unit

    fun destroy() {
        try {
            scope.cancel()
        } catch (_: Exception) {}
    }

    suspend fun enqueue(
        billId: Long,
        printerMac: String,
        error: String?,
        incrementAttempts: Boolean = false,
        publicToken: String? = null,
        kotRevision: String? = null
    ) {
        queueRepository.enqueuePending(billId, printerMac, error, incrementAttempts, publicToken, kotRevision)
    }

    suspend fun enqueueUnassigned(
        billId: Long,
        error: String?,
        publicToken: String? = null,
        kotRevision: String? = null
    ) {
        queueRepository.enqueuePending(
            billId,
            KitchenPrintQueueRepository.UNASSIGNED_PRINTER_MAC,
            error,
            incrementAttempts = false,
            publicToken = publicToken,
            kotRevision = kotRevision
        )
    }

    suspend fun claimPendingForDirectPrint(billId: Long, printerMac: String): Boolean {
        return queueRepository.claimPendingForDirectPrint(billId, printerMac)
    }

    suspend fun markPrinted(billId: Long, printerMac: String) {
        queueRepository.markSentIfPresent(billId, printerMac)
    }

    suspend fun clearForBill(billId: Long) {
        queueRepository.deleteByBillId(billId)
    }

    suspend fun flushAllPending() {
        val kitchen = printerProfileRepository.getByRole(PrinterRole.KITCHEN.name)
        if (kitchen?.enabled == true && kitchen.isConnectionConfigured()) {
            flushPendingForPrinter(kitchen.connectionTargetKey())
        } else {
            val mac = printerManager.connectedDeviceMac.value
            if (!mac.isNullOrBlank()) {
                flushPendingForPrinter(mac)
            }
        }
    }

    suspend fun flushPendingForPrinter(printerMac: String) = flushMutex.withLock {
        if (printerMac.isBlank()) return

        // Only flush if this MAC is actually a kitchen printer
        val printerProfile = resolveKitchenPrinter(printerMac)
        if (printerProfile?.role != PrinterRole.KITCHEN.name) {
            Log.d(TAG, "Connected device $printerMac is not configured as a kitchen printer. Skipping flush.")
            return
        }

        val queue = queueRepository.getPendingForPrinter(printerMac)
        if (queue.isEmpty()) return

        val restaurantProfile = restaurantRepository.getProfile() ?: return

        for (job in queue) {
            if (!queueRepository.claimPendingForRetry(job.id)) {
                Log.d(TAG, "Skipping billId=${job.billId}: queue entry already claimed elsewhere")
                continue
            }

            val bill = billRepository.getBillWithItemsById(job.billId)
            if (bill == null) {
                queueRepository.deleteById(job.id)
                continue
            }

            val isPrintable = bill.bill.orderStatus.equals("completed", ignoreCase = true) ||
                bill.bill.orderStatus.equals("paid", ignoreCase = true) ||
                bill.bill.orderStatus.equals("draft", ignoreCase = true)
            if (!isPrintable) {
                queueRepository.deleteById(job.id)
                continue
            }

            try {
                // ── Void tickets: print only the voided-item snapshot, never the whole bill ──
                val voidEvent = job.publicToken?.let { token ->
                    job.kotRevision?.let { revision -> kotEventDao.getEvent(token, revision) }
                }
                if (voidEvent != null && voidEvent.eventType == KotEventType.VOID) {
                    val bytes = KitchenTicketFormatter.formatVoidTicket(
                        bill,
                        restaurantProfile,
                        printerProfile,
                        voidEvent.itemSnapshotJson
                    )
                    if (printerTransport.print(printerProfile, bytes)) {
                        queueRepository.markSent(job.id)
                        kotEventDao.markPrinted(voidEvent.publicToken, voidEvent.kotRevision)
                    } else {
                        queueRepository.markPending(job.id, "print failed")
                        break
                    }
                    continue
                }

                // ── NEW/ADD retries: only re-print items the kitchen has NOT seen yet.
                // If a direct-print path already delivered them, the retry must not
                // print a duplicate ticket. ──
                val unsentItems = bill.items.filter { !it.sentToKot }
                if (unsentItems.isEmpty()) {
                    // Already delivered by a concurrent direct print — nothing to retry.
                    queueRepository.markSent(job.id)
                    if (job.publicToken != null && job.kotRevision != null) {
                        kotEventDao.markPrinted(job.publicToken, job.kotRevision)
                    }
                    continue
                }
                val bytes = KitchenTicketFormatter.format(bill, restaurantProfile, printerProfile, unsentItems)
                if (printerTransport.print(printerProfile, bytes)) {
                    queueRepository.markSent(job.id)
                    if (job.publicToken != null && job.kotRevision != null) {
                        kotEventDao.markPrinted(job.publicToken, job.kotRevision)
                    }
                } else {
                    queueRepository.markPending(job.id, "print failed")
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed retrying queued kitchen ticket for billId=${job.billId}", e)
                queueRepository.markPending(job.id, e.message ?: "unexpected error")
                break
            }
        }
    }

    private suspend fun resolveKitchenPrinter(printerMac: String): PrinterProfileEntity? {
        val stored = printerProfileRepository.getProfiles()
        return stored.firstOrNull {
            it.role == PrinterRole.KITCHEN.name &&
                it.enabled &&
                it.isConnectionConfigured() &&
                it.connectionTargetKey() == printerMac
        }
    }
}
