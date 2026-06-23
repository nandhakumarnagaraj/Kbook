package com.khanabook.lite.pos.domain.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrintService : Service() {

    @Inject
    lateinit var printRouter: PrintRouter

    @Inject
    lateinit var billRepository: BillRepository

    @Inject
    lateinit var restaurantRepository: RestaurantRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "PrintService"
        private const val CHANNEL_ID = "print_service_channel"
        private const val NOTIFICATION_ID = 2002

        const val EXTRA_BILL_ID = "extra_bill_id"
        const val EXTRA_DISPATCH_MODE = "extra_dispatch_mode"

        fun startPrintJob(context: Context, billId: Long, mode: PrintDispatchMode) {
            val intent = Intent(context, PrintService::class.java).apply {
                putExtra(EXTRA_BILL_ID, billId)
                putExtra(EXTRA_DISPATCH_MODE, mode.name)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting PrintService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, getNotification(), type)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed starting PrintService in foreground with type connectedDevice", e)
            try {
                // Fallback: start foreground using framework method
                startForeground(NOTIFICATION_ID, getNotification())
            } catch (ex: Throwable) {
                Log.e(TAG, "Failed fallback startForeground", ex)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val billId = intent?.getLongExtra(EXTRA_BILL_ID, -1L) ?: -1L
        val modeStr = intent?.getStringExtra(EXTRA_DISPATCH_MODE) ?: PrintDispatchMode.AUTO.name
        val mode = try {
            PrintDispatchMode.valueOf(modeStr)
        } catch (e: Exception) {
            PrintDispatchMode.AUTO
        }

        if (billId != -1L) {
            serviceScope.launch {
                try {
                    val bill = billRepository.getBillWithItemsById(billId)
                    val profile = restaurantRepository.getProfile()
                    if (bill != null) {
                        Log.d(TAG, "PrintService dispatching billId=$billId mode=$mode")
                        printRouter.printBill(bill, profile, mode)
                    } else {
                        Log.e(TAG, "PrintService could not find billId=$billId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "PrintService error while printing billId=$billId", e)
                } finally {
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POS Printing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles thermal printer connections and data dispatch"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun getNotification(): Notification {
        val title = "POS Printing Engine"
        val text = "Sending order to thermal printers..."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Safe system fallback icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
