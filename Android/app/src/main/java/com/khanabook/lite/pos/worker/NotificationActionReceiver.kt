package com.khanabook.lite.pos.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", -1)
        val fssaiNumber = intent.getStringExtra("fssai_number")

        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        if (action == "ACTION_REMIND_LATER") {
            Toast.makeText(context, "FSSAI Renewal reminder snoozed", Toast.LENGTH_SHORT).show()
        }
    }
}
