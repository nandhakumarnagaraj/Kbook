@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.domain.manager.EasebuzzClient
import com.khanabook.lite.pos.domain.manager.TrustedExternalAppReturn

/**
 * In-app Easebuzz hosted checkout. Loads the access_key URL inside a WebView
 * so the customer never leaves the app. The webhook on our server remains the
 * trust anchor — this dialog only signals "user is done with the gateway UI";
 * the bill is still gated on the verify-poll loop in NewBillScreen.
 *
 * surl/furl point at our backend (BuildConfig.BACKEND_URL) — we intercept on
 * path match before the network request fires. The server also serves a tiny
 * page at those paths as a safety net in case the WebView load races us.
 *
 * Notes for keeping the page from rendering blank:
 *  - WebChromeClient is required (Easebuzz opens popups for OTP/UPI app
 *    handoff and shows JS dialogs).
 *  - setSupportMultipleWindows + JS-can-open-windows lets popups proceed.
 *  - MIXED_CONTENT_COMPATIBILITY_MODE because the hosted page pulls in some
 *    cross-origin assets (analytics, bank logos) that NEVER_ALLOW would block.
 */
@Composable
fun EasebuzzCheckoutDialog(
    checkoutUrl: String,
    onClose: () -> Unit,
    onSurl: () -> Unit = {},
    onFurl: () -> Unit = {}
) {
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // safeDrawingPadding() keeps the WebView clear of the status bar,
            // gesture nav bar, and the soft keyboard (so the bottom "Pay" CTA
            // and OTP field stay reachable across phones).
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                TopAppBar(
                    title = { Text("Secure Payment") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                configureForCheckout()
                                webChromeClient = object : WebChromeClient() {
                                    // Easebuzz opens a popup for some bank/UPI flows.
                                    // Route popup loads through the same WebView.
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?
                                    ): Boolean {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                            ?: return false
                                        transport.webView = view
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        // shouldOverrideUrlLoading is not called for server-side
                                        // 302 redirects on many Android versions. Check here too
                                        // so we always catch the surl/furl before the page loads.
                                        if (url != null && handleNavigation(
                                                url, view?.context, onSurl, onFurl, onClose
                                            ) { errorText = it }) {
                                            view?.stopLoading()
                                            return
                                        }
                                        loading = true
                                        errorText = null
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                        // Reliable fallback: if stopLoading() in onPageStarted
                                        // didn't prevent the page from rendering, close the
                                        // dialog as soon as it finishes loading.
                                        if (url != null) {
                                            handleNavigation(
                                                url, view?.context, onSurl, onFurl, onClose
                                            ) { errorText = it }
                                        }
                                    }
                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        // Only surface main-frame errors; sub-resource
                                        // failures (analytics, fonts) are noise.
                                        if (request?.isForMainFrame == true) {
                                            errorText = "Failed to load: ${error?.description ?: "unknown error"}"
                                            loading = false
                                        }
                                    }
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return handleNavigation(url, view?.context, onSurl, onFurl, onClose) {
                                            errorText = it
                                        }
                                    }

                                    @Deprecated("for older API levels")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        return handleNavigation(url ?: return false, view?.context, onSurl, onFurl, onClose) {
                                            errorText = it
                                        }
                                    }
                                }
                                loadUrl(checkoutUrl)
                            }
                        }
                    )
                    if (loading && errorText == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    errorText?.let { msg ->
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onClose) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Routes a URL the WebView is about to load:
 *  - Our backend's surl/furl paths → close the dialog with the matching result.
 *    Path-based match (host-agnostic) so it works in both test and prod backends.
 *  - upi://, intent://, tel:, sms:, mailto: and any non-http(s) → hand off to
 *    the OS so the appropriate app (UPI app, dialer, etc.) opens. Without this
 *    the WebView errors with ERR_UNKNOWN_URL_SCHEME on UPI handoff.
 *  - http(s) → let the WebView load it (return false).
 */
private fun handleNavigation(
    url: String,
    context: Context?,
    onSurl: () -> Unit,
    onFurl: () -> Unit,
    onClose: () -> Unit,
    setError: (String) -> Unit
): Boolean {
    val lower = url.lowercase()
    // Intercept the deep-link the server's return page redirects to AFTER processing
    // the payment params. Never intercept the surl/furl directly — the WebView must
    // load them so the server can verify the hash and update payment status first.
    if (lower.startsWith("khanabook://payment/success")) { onSurl(); onClose(); return true }
    if (lower.startsWith("khanabook://payment/fail")) { onFurl(); onClose(); return true }
    if (lower.startsWith("http://") || lower.startsWith("https://")) return false

    // Non-http scheme — dispatch to the OS.
    val ctx = context ?: return true
    val intent = if (lower.startsWith("intent://")) {
        // Android intent: URI carries fallback URL + package hints.
        try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            setError("Unsupported link: $url")
            return true
        }
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        TrustedExternalAppReturn.mark(ctx)
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        setError("No app installed to handle this payment method.")
        true
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForCheckout() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        loadWithOverviewMode = true
        useWideViewPort = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
    }
}
