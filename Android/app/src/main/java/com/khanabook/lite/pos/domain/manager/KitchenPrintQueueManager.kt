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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()

    init {
        scope.launch {
            printerManager.connectedDeviceEvents.collectLatest { printerMac ->
                flushPendingForPrinter(printerMac)
            }
        }
    }

    fun initialize() = Unit

    suspend fun enqueue(billId: Long, printerMac: String, error: String?) {
        queueRepository.enqueueOrUpdate(billId, printerMac, error)
    }

    suspend fun enqueueUnassigned(billId: Long, error: String?) {
        queueRepository.enqueueOrUpdate(
            billId,
            KitchenPrintQueueRepository.UNASSIGNED_PRINTER_MAC,
            error
        )
    }

    suspend fun markPrinted(billId: Long, printerMac: String) {
        queueRepository.deleteByBillAndPrinter(billId, printerMac)
    }

    suspend fun clearForBill(billId: Long) {
        queueRepository.deleteByBillId(billId)
    }

    suspend fun flushPendingForPrinter(printerMac: String) = flushMutex.withLock {
        if (printerMac.isBlank()) return

        val queue = queueRepository.getPendingForPrinter(printerMac)
        if (queue.isEmpty()) return

        val restaurantProfile = restaurantRepository.getProfile() ?: return
        val printerProfile = resolveKitchenPrinter(printerMac) ?: return

        for (job in queue) {
            val bill = billRepository.getBillWithItemsById(job.billId)
            if (bill == null) {
                queueRepository.deleteByBillAndPrinter(job.billId, printerMac)
                continue
            }

            val isPrintable = bill.bill.orderStatus.equals("completed", ignoreCase = true) ||
                bill.bill.orderStatus.equals("paid", ignoreCase = true)
            if (!isPrintable) {
                queueRepository.deleteByBillAndPrinter(job.billId, printerMac)
                continue
            }

            try {
                printerManager.disconnect()
                if (!printerManager.connect(printerMac)) {
                    queueRepository.enqueueOrUpdate(job.billId, job.printerMac, "connection failed")
                    break
                }

                val bytes = KitchenTicketFormatter.format(bill, restaurantProfile, printerProfile)
                if (printerManager.printBytes(bytes)) {
                    queueRepository.deleteById(job.id)
                } else {
                    queueRepository.enqueueOrUpdate(job.billId, job.printerMac, "print failed")
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed retrying queued kitchen ticket for billId=${job.billId}", e)
                queueRepository.enqueueOrUpdate(
                    job.billId,
                    job.printerMac,
                    e.message ?: "unexpected error"
                )
                break
            } finally {
                printerManager.disconnect()
            }
        }
    }

    private suspend fun resolveKitchenPrinter(printerMac: String): PrinterProfileEntity? {
        val stored = printerProfileRepository.getProfiles()
        return stored.firstOrNull {
            it.role == PrinterRole.KITCHEN.name && it.enabled && it.macAddress == printerMac
        } ?: stored.firstOrNull { it.macAddress == printerMac }?.copy(role = PrinterRole.KITCHEN.name)
    }
}
