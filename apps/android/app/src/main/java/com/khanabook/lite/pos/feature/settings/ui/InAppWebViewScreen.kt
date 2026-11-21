package com.khanabook.lite.pos.feature.settings.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.RichEspresso

private const val TUTORIAL_BASE_URL = "https://kbook.iadv.cloud/cdn/tutorials/"
private val SAFE_TUTORIAL_FILE = Regex("[A-Za-z0-9._-]+")

/**
 * Full-screen, in-app WebView used by "Watch Tutorials".
 *
 * The previous implementation called `startActivity(Intent.ACTION_VIEW, ...)`, which handed the
 * tutorial to an external browser. Here the selected CDN video URL is loaded directly by a
 * [WebView], so staff never leave the POS app and Chromium's native video controls handle playback.
 * Only a plain file name from the tutorial catalogue is accepted; paths and traversal are rejected.
 *
 * The Activity-level touch indicator still sees touches here: `AndroidView` hosts the WebView in
 * the Activity's own window, not a separate dialog window.
 */
@Composable
fun InAppWebViewScreen(
    fileName: String,
    title: String = "Watch Tutorials",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val isSafeFile = fileName.length <= 120 && SAFE_TUTORIAL_FILE.matches(fileName) && !fileName.contains("..")
    val videoUrl = TUTORIAL_BASE_URL + fileName
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(if (isSafeFile) null else "Invalid tutorial file") }
    var reloadKey by remember { mutableIntStateOf(0) }

    fun navigateBack() {
        val current = webView
        Log.d("KHANABOOK_WEB", "navigateBack: canGoBack=${current?.canGoBack()}")
        if (current != null && current.canGoBack()) current.goBack() else onBack()
    }

    LaunchedEffect(Unit) {
        Log.d("KHANABOOK_WEB", "screen entered")
    }
    DisposableEffect(Unit) {
        onDispose { Log.d("KHANABOOK_WEB", "screen disposed") }
    }

    // Hardware back steps through WebView history first, then pops this screen.
    BackHandler {
        Log.d("KHANABOOK_WEB", "BackHandler invoked")
        navigateBack()
    }

    // WebView native state is released by AndroidView.onRelease, not by a DisposableEffect keyed
    // on the mutable webView state: that key changes from null to the real view as soon as
    // AndroidView's update lambda runs, so its onDispose would destroy the live view immediately.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        KhanaBookScreenScaffold(
            title = title,
            onBack = { navigateBack() },
            // Nav destination outside MainScreen: apply the top inset here so the
            // header and player never sit behind the status bar.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (progress in 1..99 && loadError == null) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = PrimaryGold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    key(reloadKey) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mediaPlaybackRequiresUserGesture = false

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                        Log.d("KHANABOOK_WEB", "progress=$newProgress")
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url?.toString().orEmpty()
                                        if (target.startsWith("http://") || target.startsWith("https://")) {
                                            return false // keep in-app links inside the WebView
                                        }
                                        return try {
                                            view?.context?.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                            )
                                            true
                                        } catch (_: Exception) {
                                            true
                                        }
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        loadError = null
                                        progress = 0
                                        Log.d("KHANABOOK_WEB", "onPageStarted: $url")
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        progress = 100
                                        Log.d("KHANABOOK_WEB", "onPageFinished: $url")
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        Log.e(
                                            "KHANABOOK_WEB",
                                            "onReceivedError main=${request?.isForMainFrame} " +
                                                "url=${request?.url} error=${error?.description}"
                                        )
                                        if (request?.isForMainFrame == true) {
                                            loadError = error?.description?.toString()
                                                ?: "Unable to load the tutorial"
                                        }
                                    }
                                }

                                if (isSafeFile) {
                                    loadUrl(videoUrl)
                                }
                            }
                        },
                            update = { webView = it },
                            onRelease = { released ->
                                Log.d("KHANABOOK_WEB", "WebView released")
                                webView = null
                                released.destroy()
                            }
                        )
                    }

                    if (loadError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .padding(spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Unable to load tutorial",
                                color = Color(0xFF333333),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = loadError.orEmpty(),
                                color = Color(0xFF777777),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = spacing.medium)
                            )
                            Button(onClick = {
                                loadError = null
                                reloadKey += 1
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
