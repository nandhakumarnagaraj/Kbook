package com.khanabook.lite.pos.domain.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helps users disable battery optimization and OEM background-kill behaviour.
 *
 * Chinese OEM ROMs (MIUI, ColorOS, FuntouchOS, RealmeUI, EMUI) aggressively kill
 * background apps regardless of WorkManager scheduling. Without user-granted
 * auto-start permission, background bill sync stops within 5-10 minutes.
 *
 * These OEMs dominate the Indian market (~60% share), so this matters a lot for a POS app.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimization"

    /** True when the app is exempt from Doze / App Standby battery restrictions. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system dialog asking the user to exempt this app from battery optimization.
     * Falls back to the general battery-optimization settings list if the direct
     * request intent is unavailable.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Direct battery-optimization request unavailable, opening settings list", e)
            openBatteryOptimizationSettings(context)
        }
    }

    private fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.onFailure { Log.w(TAG, "Battery optimization settings unavailable", it) }
    }

    /** True when the current device runs an OEM ROM known to kill background work. */
    fun requiresOemAutoStart(): Boolean = oemAutoStartIntents().isNotEmpty()

    /** Human-readable manufacturer label for UI copy. */
    fun manufacturerLabel(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi / Redmi / POCO"
        "oppo" -> "OPPO"
        "vivo", "iqoo" -> "Vivo / iQOO"
        "realme" -> "Realme"
        "huawei", "honor" -> "Huawei / Honor"
        "oneplus" -> "OnePlus"
        "samsung" -> "Samsung"
        "asus" -> "ASUS"
        "letv" -> "LeEco"
        else -> Build.MANUFACTURER
    }

    /**
     * Opens the OEM-specific auto-start / background-app manager screen.
     * Returns true when a settings screen was launched.
     */
    fun openOemAutoStartSettings(context: Context): Boolean {
        for (intent in oemAutoStartIntents()) {
            val launched = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        // Nothing OEM-specific worked; fall back to the app's own settings page.
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Candidate auto-start settings activities per manufacturer, ordered
     * most-specific first. Package/class names change between ROM versions,
     * so several variants are attempted.
     */
    private fun oemAutoStartIntents(): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val components: List<Pair<String, String>> = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings"
            )
            manufacturer.contains("oppo") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            manufacturer.contains("realme") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            manufacturer.contains("oneplus") -> listOf(
                "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
            manufacturer.contains("asus") -> listOf(
                "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
                "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity"
            )
            manufacturer.contains("letv") -> listOf(
                "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
            )
            else -> emptyList()
        }
        return components.map { (pkg, cls) ->
            Intent().apply { setClassName(pkg, cls) }
        }
    }
}
