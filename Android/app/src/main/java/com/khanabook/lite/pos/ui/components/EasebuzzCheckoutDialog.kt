@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * In-app Easebuzz hosted checkout. Loads the access_key URL inside a WebView
 * so the customer never leaves the app. The webhook on our server remains the
 * trust anchor — this dialog only signals "user is done with the gateway UI";
 * the bill is still gated on the verify-poll loop in NewBillScreen.
 *
 * surl/furl are sentinel URLs (example.com/success|failure) that we intercept
 * before the network request fires, so they don't need to actually exist.
 */
@Composable
fun EasebuzzCheckoutDialog(
    checkoutUrl: String,
    onClose: () -> Unit,
    onSurl: () -> Unit = {},
    onFurl: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Secure Payment") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            configureForCheckout()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return handleCallback(url, onSurl, onFurl, onClose)
                                }

                                @Deprecated("for older API levels")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    return handleCallback(url ?: return false, onSurl, onFurl, onClose)
                                }
                            }
                            loadUrl(checkoutUrl)
                        }
                    }
                )
            }
        }
    }
}

private fun handleCallback(
    url: String,
    onSurl: () -> Unit,
    onFurl: () -> Unit,
    onClose: () -> Unit
): Boolean {
    val lower = url.lowercase()
    return when {
        lower.contains("example.com/success") -> { onSurl(); onClose(); true }
        lower.contains("example.com/failure") -> { onFurl(); onClose(); true }
        else -> false
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForCheckout() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_DEFAULT
    }
}
