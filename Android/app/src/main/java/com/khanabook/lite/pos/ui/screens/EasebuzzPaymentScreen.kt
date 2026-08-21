package com.khanabook.lite.pos.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.RichEspresso
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.EasebuzzPaymentState
import com.khanabook.lite.pos.ui.viewmodel.EasebuzzPaymentViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasebuzzPaymentScreen(
    onBack: () -> Unit,
    onPaymentComplete: (gatewayTxnId: String?) -> Unit,
    viewModel: EasebuzzPaymentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var sdkLaunched by remember { mutableStateOf(false) }
    var verificationStarted by remember { mutableStateOf(false) }

    // Scope that survives composition teardown — return verification must always run
    val sdkScope = remember { MainScope() }

    val sdkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val resultData = data?.getStringExtra("result")
        val paymentResponse = data?.getStringExtra("payment_response")

        if (!verificationStarted) {
            verificationStarted = true
            when (resultData) {
                "payment_successfull" -> {
                    sdkScope.launch { viewModel.verifyAndComplete(viewModel.currentTxnId) }
                }
                "payment_failed" -> {
                    sdkScope.launch { viewModel.verifyAndComplete() }
                }
                "user_cancelled" -> {
                    sdkScope.launch { viewModel.verifyAndComplete() }
                }
                else -> {
                    sdkScope.launch { viewModel.verifyAndComplete() }
                }
            }
        }
    }

    // When payment is ready, launch SDK
    LaunchedEffect(state) {
        when (val currentState = state) {
            is EasebuzzPaymentState.PaymentReady -> {
                if (activity != null && !sdkLaunched) {
                    sdkLaunched = true
                    try {
                        val intent = Intent(
                            activity,
                            Class.forName("com.easebuzz.payment.kit.PWECheckoutActivity")
                        )
                        intent.putExtra("access_key", currentState.accessToken)
                        intent.putExtra("pay_mode", if (BuildConfig.DEBUG) "test" else "production")
                        sdkLauncher.launch(intent)
                    } catch (e: ClassNotFoundException) {
                        // SDK unavailable — fall back to browser payment flow
                        val url = currentState.paymentUrl
                        if (url != null) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            )
                        } else {
                            viewModel.onSdkUnavailable(
                                "Easebuzz SDK not available. Please update the app."
                            )
                        }
                    }
                }
            }
            else -> { /* handled by UI below */ }
        }
    }

    // Auto-start order creation (only once)
    LaunchedEffect(Unit) {
        if (state is EasebuzzPaymentState.Idle) {
            viewModel.createOrder()
        }
    }

    // Auto-complete on success
    LaunchedEffect(state) {
        if (state is EasebuzzPaymentState.PaymentSuccess) {
            val txnId = (state as EasebuzzPaymentState.PaymentSuccess).txnId
            delay(2000)
            onPaymentComplete(txnId)
        }
    }

    // Activity recreation during SDK checkout: verify on return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sdkLaunched && !verificationStarted) {
                verificationStarted = true
                sdkScope.launch { viewModel.verifyAndComplete() }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose { sdkScope.cancel() }
    }

    val isPaymentActive = state is EasebuzzPaymentState.CreatingOrder ||
        state is EasebuzzPaymentState.PaymentReady ||
        state is EasebuzzPaymentState.Verifying

    Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            TopAppBar(
                title = { Text("Online Payment", color = TextLight) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isPaymentActive) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isPaymentActive) PrimaryGold.copy(alpha = 0.3f) else PrimaryGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))
                )
        ) {
            when (val currentState = state) {
                is EasebuzzPaymentState.Idle,
                is EasebuzzPaymentState.CreatingOrder -> {
                    LoadingContent("Creating payment order...")
                }

                is EasebuzzPaymentState.PaymentReady -> {
                    LoadingContent("Opening payment... ${formatSeconds(secondsLeft)}s left")
                }

                is EasebuzzPaymentState.Verifying -> {
                    var showRetry by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(30_000L)
                        showRetry = true
                    }
                    if (showRetry) {
                        PaymentResultContent(
                            success = false,
                            message = "Verification taking too long",
                            details = "Payment may have succeeded. Tap below to check status.",
                            onDone = onBack,
                            onRetry = { viewModel.verifyPayment() }
                        )
                    } else {
                        LoadingContent("Verifying payment...")
                    }
                }

                is EasebuzzPaymentState.PaymentSuccess -> {
                    PaymentResultContent(
                        success = true,
                        message = "Payment Successful!",
                        details = "Transaction ID: ${currentState.txnId}",
                        onDone = { onPaymentComplete(currentState.txnId) }
                    )
                }

                is EasebuzzPaymentState.PaymentFailed -> {
                    PaymentResultContent(
                        success = false,
                        message = "Payment Failed",
                        details = currentState.message,
                        onDone = onBack,
                        onRetry = { viewModel.retry() },
                        onBrowserFlow = viewModel.lastPaymentUrl?.let { url ->
                            { scope.launch { openBrowserFlow(context, url) } }
                        }
                    )
                }

                is EasebuzzPaymentState.Error -> {
                    PaymentResultContent(
                        success = false,
                        message = "Error",
                        details = currentState.message,
                        onDone = onBack,
                        onRetry = { viewModel.retry() },
                        onBrowserFlow = viewModel.lastPaymentUrl?.let { url ->
                            { scope.launch { openBrowserFlow(context, url) } }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun openBrowserFlow(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        KhanaToast.show("Unable to open browser flow", ToastKind.Error)
    }
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun LoadingContent(message: String) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = PrimaryGold)
        Spacer(modifier = Modifier.height(spacing.medium))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextLight,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PaymentResultContent(
    success: Boolean,
    message: String,
    details: String,
    onDone: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onBrowserFlow: (() -> Unit)? = null
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Close,
            contentDescription = null,
            tint = if (success) SuccessGreen else DangerRed,
            modifier = Modifier.size(KhanaBookTheme.iconSize.large)
        )

        Spacer(modifier = Modifier.height(spacing.medium))

        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            color = TextLight,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLight.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.large))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (success) SuccessGreen else PrimaryGold
            )
        ) {
            Text(if (success) "Done" else "Go Back")
        }

        if (onRetry != null && !success) {
            Spacer(modifier = Modifier.height(spacing.small))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkBrown2)
            ) {
                Text("Retry Payment", color = PrimaryGold)
            }
        }

        if (onBrowserFlow != null && !success) {
            Spacer(modifier = Modifier.height(spacing.small))
            OutlinedButton(
                onClick = onBrowserFlow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open in Browser Flow", color = PrimaryGold)
            }
        }
    }
}