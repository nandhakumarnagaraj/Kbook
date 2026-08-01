package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.model.connectionTargetKey
import com.khanabook.lite.pos.domain.model.isConnectionConfigured
import com.khanabook.lite.pos.data.local.dao.KotEventDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
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
                val bytes = KitchenTicketFormatter.format(bill, restaurantProfile, printerProfile)
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
