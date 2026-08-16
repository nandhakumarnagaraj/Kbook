package com.khanabook.lite.pos.ui.screens

import android.app.Activity
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.domain.manager.PaymentReturnManager
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasebuzzPaymentScreen(
    onBack: () -> Unit,
    onPaymentComplete: () -> Unit,
    viewModel: EasebuzzPaymentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val activity = context as? Activity

    // SDK activity result launcher
    val sdkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val resultData = data?.getStringExtra("result")
        val paymentResponse = data?.getStringExtra("payment_response")

        when (resultData) {
            "payment_successfull" -> {
                // Publish to PaymentReturnManager for bill finalization
                PaymentReturnManager.handleIntent(
                    android.content.Intent().setData(
                        android.net.Uri.parse("khanabook://payment/success?txnid=${viewModel.currentTxnId}")
                    )
                )
                viewModel.onPaymentReturn(true)
            }
            "payment_failed" -> {
                PaymentReturnManager.handleIntent(
                    android.content.Intent().setData(
                        android.net.Uri.parse("khanabook://payment/failure")
                    )
                )
                viewModel.onPaymentReturn(false)
            }
            "user_cancelled" -> {
                viewModel.onPaymentReturn(false)
            }
            else -> {
                viewModel.onPaymentReturn(false)
            }
        }
    }

    // Track if SDK was already launched to prevent double-launch on recomposition
    var sdkLaunched by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // When payment is ready, launch SDK
    LaunchedEffect(state) {
        when (val currentState = state) {
            is EasebuzzPaymentState.PaymentReady -> {
                if (activity != null && !sdkLaunched) {
                    sdkLaunched = true
                    try {
                        val intent = android.content.Intent(activity, Class.forName("com.easebuzz.payment.kit.PWECouponsActivity"))
                        intent.putExtra("access_key", currentState.accessToken)
                        intent.putExtra("pay_mode", "test") // Change to "production" for live
                        sdkLauncher.launch(intent)
                    } catch (e: ClassNotFoundException) {
                        sdkLaunched = false
                        viewModel.onSdkUnavailable("Easebuzz SDK not available. Please update the app.")
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

    Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            TopAppBar(
                title = { Text("Online Payment", color = TextLight) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
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
                    LoadingContent("Opening payment...")
                }

                is EasebuzzPaymentState.Verifying -> {
                    LoadingContent("Verifying payment...")
                }

                is EasebuzzPaymentState.PaymentSuccess -> {
                    PaymentResultContent(
                        success = true,
                        message = "Payment Successful!",
                        details = "Transaction ID: ${currentState.txnId}",
                        onDone = onPaymentComplete
                    )
                }

                is EasebuzzPaymentState.PaymentFailed -> {
                    PaymentResultContent(
                        success = false,
                        message = "Payment Failed",
                        details = currentState.message,
                        onDone = onBack,
                        onRetry = { viewModel.reset(); viewModel.createOrder() }
                    )
                }

                is EasebuzzPaymentState.Error -> {
                    PaymentResultContent(
                        success = false,
                        message = "Error",
                        details = currentState.message,
                        onDone = onBack,
                        onRetry = { viewModel.reset(); viewModel.createOrder() }
                    )
                }
            }
        }
    }
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
            color = TextLight
        )
    }
}

@Composable
private fun PaymentResultContent(
    success: Boolean,
    message: String,
    details: String,
    onDone: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
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
    }
}
