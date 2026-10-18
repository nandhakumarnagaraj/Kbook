package com.khanabook.lite.pos.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a link outside the app.
 *
 * Returns false when nothing on the device can handle it. POS terminals are often provisioned
 * without a browser, and `startActivity` throws there — on the very screen that exists to send
 * the merchant to Easebuzz, the failure mode was a crash instead of a readable link.
 */
object BrowserLauncher {

    fun open(context: Context, url: String?): Boolean {
        val target = url?.trim().orEmpty()
        if (target.isEmpty()) return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target))
                    // The context may be an application context on some call paths; without this
                    // flag that throws instead of opening the browser.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
