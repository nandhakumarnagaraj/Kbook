package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.model.PrinterRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class KitchenPrintQueueManager @Inject constructor(
    private val queueRepository: KitchenPrintQueueRepository,
    private val billRepository: BillRepository,
    private val restaurantRepository: RestaurantRepository,
    private val printerProfileRepository: PrinterProfileRepository,
    private val printerManager: BluetoothPrinterManager
) {
    companion object {
        private const val TAG = "KitchenPrintQueue"
        var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
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
    }

    fun initialize() = Unit

    suspend fun enqueue(
        billId: Long,
        printerMac: String,
        error: String?,
        incrementAttempts: Boolean = false
    ) {
        queueRepository.enqueuePending(billId, printerMac, error, incrementAttempts)
    }

    suspend fun enqueueUnassigned(billId: Long, error: String?) {
        queueRepository.enqueuePending(
            billId,
            KitchenPrintQueueRepository.UNASSIGNED_PRINTER_MAC,
            error,
            incrementAttempts = false
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
        println("flushPendingForPrinter: printerMac=$printerMac")
        if (printerMac.isBlank()) {
            println("printerMac is blank")
            return
        }

        // Only flush if this MAC is actually a kitchen printer
        val printerProfile = resolveKitchenPrinter(printerMac)
        if (printerProfile?.role != PrinterRole.KITCHEN.name) {
            println("printerProfile role is not KITCHEN: role=${printerProfile?.role}")
            Log.d(TAG, "Connected device $printerMac is not configured as a kitchen printer. Skipping flush.")
            return
        }

        val queue = queueRepository.getPendingForPrinter(printerMac)
        println("queue size: ${queue.size}")
        if (queue.isEmpty()) return

        val restaurantProfile = restaurantRepository.getProfile() ?: run {
            println("restaurantProfile is null")
            return
        }

        for (job in queue) {
            println("processing job: id=${job.id}, billId=${job.billId}")
            if (!queueRepository.claimPendingForRetry(job.id)) {
                println("claimPendingForRetry failed for job.id=${job.id}")
                Log.d(TAG, "Skipping billId=${job.billId}: queue entry already claimed elsewhere")
                continue
            }

            val bill = billRepository.getBillWithItemsById(job.billId)
            if (bill == null) {
                println("bill is null for job.billId=${job.billId}")
                queueRepository.deleteById(job.id)
                continue
            }

            val isPrintable = bill.bill.orderStatus.equals("completed", ignoreCase = true) ||
                bill.bill.orderStatus.equals("paid", ignoreCase = true)
            if (!isPrintable) {
                println("isPrintable is false for orderStatus=${bill.bill.orderStatus}")
                queueRepository.deleteById(job.id)
                continue
            }

            try {
                println("connecting to printerMac=$printerMac")
                if (!printerManager.isConnectedTo(printerMac) && !printerManager.connect(printerMac)) {
                    println("connection failed to printerMac=$printerMac")
                    queueRepository.markPending(job.id, "connection failed")
                    break
                }

                val bytes = KitchenTicketFormatter.format(bill, restaurantProfile, printerProfile)
                if (printerManager.printBytes(printerMac, bytes)) {
                    queueRepository.markSent(job.id)
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
            it.role == PrinterRole.KITCHEN.name && it.enabled && it.macAddress == printerMac
        }
    }
}
