package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.repository.EasebuzzSdkPaymentRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val MAX_RETRIES = 5

enum class PaymentScreenState {
    INITIALIZING,
    READY,
    PROCESSING,
    VERIFYING,
    SUCCESS,
    FAILED
}

@Composable
fun EasebuzzPaymentScreen(
    restaurantId: Long,
    billId: Long,
    amount: BigDecimal,
    paymentRepository: EasebuzzSdkPaymentRepository,
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onPaymentComplete: (gatewayTxnId: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    var screenState by remember { mutableStateOf(PaymentScreenState.INITIALIZING) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var paymentUrl by remember { mutableStateOf<String?>(null) }
    var payMode by remember { mutableStateOf("test") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var sdkAttempted by remember { mutableStateOf(false) }
    var sdkLaunched by remember { mutableStateOf(false) }

    // Scope that survives composition teardown — return verification must always run
    val sdkScope = remember { kotlinx.coroutines.MainScope() }

    suspend fun createOrderWithRetry() {
        // Idempotency guard: if already have an access token, skip
        if (accessToken != null) {
            screenState = PaymentScreenState.READY
            return
        }
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                val response = paymentRepository.createOrder(
                    restaurantId = restaurantId,
                    serverBillId = billId,
                    gatewayAmount = amount
                )
                accessToken = response.accessToken
                paymentUrl = response.paymentUrl
                payMode = response.payMode
                screenState = PaymentScreenState.READY
                retryCount = 0
                return
            } catch (e: Exception) {
                lastError = e
                retryCount = attempt + 1
                if (attempt < MAX_RETRIES) {
                    val backoffMs = (1000L shl attempt).coerceAtMost(16000L)
                    delay(backoffMs)
                }
            }
        }
        errorMessage = lastError?.message ?: "Failed to initiate payment"
        screenState = PaymentScreenState.FAILED
    }

    fun launchSdk() {
        val token = accessToken ?: return
        screenState = PaymentScreenState.PROCESSING
        sdkAttempted = true
        try {
            val intent = paymentRepository.createSdkIntent(token, payMode, context)
            sdkLaunched = true
            context.startActivity(intent)
        } catch (e: Exception) {
            // Native SDK unavailable — fall back to Custom Tabs
            val url = paymentUrl
            if (url != null) {
                sdkLaunched = true
                paymentRepository.launchFallback(context, url)
            } else {
                errorMessage = e.message ?: "SDK unavailable"
                screenState = PaymentScreenState.FAILED
            }
        }
    }

    suspend fun retryPayment() {
        screenState = PaymentScreenState.INITIALIZING
        errorMessage = null
        retryCount = 0
        sdkAttempted = false
        sdkLaunched = false
        createOrderWithRetry()
    }

    LaunchedEffect(Unit) {
        createOrderWithRetry()
    }

    // Lifecycle observer: always registered at composition time (not keyed on sdkLaunched)
    // to guarantee it fires ON_RESUME when returning from the SDK/browser activity,
    // even if recomposition is deferred. Checks sdkLaunched flag inside the callback.
    // Uses poll-verify strategy: polls getStatus() up to 10 times at 3s intervals,
    // then calls verify() once. If getStatus returns paid but verify fails, still
    // treats as SUCCESS (payment was taken — server webhook may be delayed).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sdkLaunched) {
                sdkScope.launch {
                    screenState = PaymentScreenState.VERIFYING
                    var pollAttempts = 0
                    var paid = false
                    while (pollAttempts < 10) {
                        try {
                            val status = paymentRepository.getStatus(billId)
                            if (status.paymentStatus == "paid" || status.paymentStatus == "success") {
                                paid = true
                                break
                            }
                        } catch (_: Exception) { }
                        pollAttempts++
                        if (pollAttempts < 10) delay(3000)
                    }
                    try {
                        val verifyResponse = paymentRepository.verify(billId)
                        if (verifyResponse.status == "success") {
                            screenState = PaymentScreenState.SUCCESS
                            KhanaToast.show("Payment successful!", ToastKind.Success)
                            onPaymentComplete(verifyResponse.txnid)
                        } else {
                            errorMessage = if (paid) "Payment taken but verification pending — check in orders" else "Payment verification failed"
                            screenState = if (paid) PaymentScreenState.SUCCESS else PaymentScreenState.FAILED
                            if (paid) onPaymentComplete(null)
                        }
                    } catch (e: Exception) {
                        errorMessage = if (paid) "Payment taken but verification pending — check in orders" else (e.message ?: "Verification failed")
                        screenState = if (paid) PaymentScreenState.SUCCESS else PaymentScreenState.FAILED
                        if (paid) onPaymentComplete(null)
                    }
                }
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(layout.contentPadding)
    ) {
        Spacer(modifier = Modifier.height(spacing.medium))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text(
                    text = "Easebuzz Payment",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                Text(
                    text = "Bill #$billId",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Amount: ₹$amount",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(spacing.large))

                when (screenState) {
                    PaymentScreenState.INITIALIZING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = PrimaryGold)
                            Spacer(modifier = Modifier.padding(start = spacing.medium))
                            Text(
                                text = if (retryCount > 0) "Retrying (${retryCount}/$MAX_RETRIES)..."
                                else "Initializing payment...",
                                color = TextGold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    PaymentScreenState.READY -> {
                        Text(
                            text = "Select payment method to continue",
                            color = TextGold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(spacing.large))

                        Button(
                            onClick = { launchSdk() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(
                                "Pay ₹$amount",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    PaymentScreenState.PROCESSING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = PrimaryGold)
                            Spacer(modifier = Modifier.padding(start = spacing.medium))
                            Text(
                                text = "Processing payment...",
                                color = TextGold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    PaymentScreenState.VERIFYING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = PrimaryGold)
                            Spacer(modifier = Modifier.padding(start = spacing.medium))
                            Text(
                                text = "Verifying payment...",
                                color = TextGold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    PaymentScreenState.SUCCESS -> {
                        Text(
                            text = "Payment Successful!",
                            color = SuccessGreen,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PaymentScreenState.FAILED -> {
                        Text(
                            text = "Payment Failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(spacing.small))
                            Text(
                                text = errorMessage!!,
                                color = TextGold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.large))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch { retryPayment() }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("Retry", color = Color.White)
                            }

                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier.weight(1f).height(56.dp),
                                border = BorderStroke(1.dp, TextGold),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("Cancel", color = TextGold)
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.small))
                            Text(
                                text = if (sdkAttempted) "Native payment failed — try via browser" else "You can also try paying via browser",
                                color = TextGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )

                            if (paymentUrl != null) {
                                OutlinedButton(
                                    onClick = {
                                        paymentRepository.launchFallback(context, paymentUrl!!)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    border = BorderStroke(1.dp, PrimaryGold),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text("Open in Browser", color = PrimaryGold)
                                }
                            }
                    }
                }
            }
        }
    }
}
