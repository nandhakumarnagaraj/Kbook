package com.khanabook.lite.pos.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

object OemHardeningHelper {

    private const val TAG = "OemHardeningHelper"

    /**
     * Checks if the app is currently whitelisted from battery optimizations.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launch request to ignore battery optimizations for high-reliability background pushes.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context) {
        if (isBatteryOptimizationIgnored(context)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch battery optimization ignore intent", e)
            // Fallback to general battery saver settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback to battery optimization settings failed", ex)
            }
        }
    }

    /**
     * Detects if the device brand is known for aggressive background process termination.
     */
    fun isAggressiveOem(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return manufacturer.contains("xiaomi") || 
               manufacturer.contains("redmi") || 
               manufacturer.contains("poco") || 
               manufacturer.contains("realme") || 
                manufacturer.contains("oppo") || 
               manufacturer.contains("vivo")
    }

    /**
     * Launches the system/OEM auto-start configuration screen if available.
     * Supports MIUI/HyperOS (Xiaomi/Redmi), Realme, Oppo, and Vivo.
     */
    fun launchAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val intents = mutableListOf<Intent>()

        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            // Xiaomi/MIUI Autostart Settings
            intents.add(Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            })
        } else if (manufacturer.contains("realme") || manufacturer.contains("oppo")) {
            // Realme / Oppo Startup Manager
            intents.add(Intent().apply {
                component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            })
            intents.add(Intent().apply {
                component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            })
            intents.add(Intent().apply {
                component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            })
        } else if (manufacturer.contains("vivo")) {
            // Vivo Autostart settings
            intents.add(Intent().apply {
                component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            })
            intents.add(Intent().apply {
                component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            })
        }

        // Generic fallback to application settings
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Failed launching component: ${intent.component?.className ?: "fallback"}")
            }
        }
        return false
    }
}
