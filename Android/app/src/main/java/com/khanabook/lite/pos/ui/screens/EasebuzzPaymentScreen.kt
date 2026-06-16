package com.khanabook.lite.pos.ui.screens

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.khanabook.lite.pos.data.repository.EasebuzzSdkPaymentRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookGlassCard
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val MAX_RETRIES = 5
private const val TAG = "EasebuzzPaymentScreen"

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
    var currentTxnid by remember { mutableStateOf<String?>(null) }
    var payMode by remember { mutableStateOf("test") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var sdkAttempted by remember { mutableStateOf(false) }
    var sdkLaunched by remember { mutableStateOf(false) }
    var verificationStarted by remember { mutableStateOf(false) }

    // Scope that survives composition teardown — return verification must always run
    val sdkScope = remember { kotlinx.coroutines.MainScope() }

    suspend fun createOrderWithRetry(forceNewAttempt: Boolean = false) {
        if (forceNewAttempt) {
            Log.i(TAG, "Starting fresh Easebuzz payment attempt billId=$billId restaurantId=$restaurantId")
            paymentRepository.clearPaymentSession()
            accessToken = null
            paymentUrl = null
            currentTxnid = null
        }
        if (accessToken != null) {
            Log.i(TAG, "Existing access key present for billId=$billId txnid=$currentTxnid; not creating duplicate order")
            screenState = PaymentScreenState.READY
            return
        }
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                Log.i(TAG, "Creating Easebuzz order billId=$billId restaurantId=$restaurantId attempt=${attempt + 1}")
                val response = paymentRepository.createOrder(
                    restaurantId = restaurantId,
                    serverBillId = billId,
                    gatewayAmount = amount
                )
                accessToken = response.accessToken
                paymentUrl = response.paymentUrl
                currentTxnid = response.txnid
                payMode = response.payMode
                Log.i(
                    TAG,
                    "Created Easebuzz access key billId=$billId txnid=${response.txnid} payMode=${response.payMode} accessKeyLength=${response.accessToken.length}"
                )
                screenState = PaymentScreenState.READY
                retryCount = 0
                return
            } catch (e: Exception) {
                lastError = e
                retryCount = attempt + 1
                Log.w(TAG, "Create Easebuzz order failed billId=$billId attempt=${attempt + 1}: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    val backoffMs = (1000L shl attempt).coerceAtMost(16000L)
                    delay(backoffMs)
                }
            }
        }
        errorMessage = lastError?.message ?: "Failed to initiate payment"
        screenState = PaymentScreenState.FAILED
    }

    suspend fun verifyAndComplete(txnidFromSdk: String? = null) {
        screenState = PaymentScreenState.VERIFYING
        Log.i(TAG, "Verifying Easebuzz payment billId=$billId currentTxnid=$currentTxnid sdkTxnid=$txnidFromSdk")
        var pollAttempts = 0
        var paid = false
        while (pollAttempts < 10) {
            try {
                val status = paymentRepository.getStatus(billId, refresh = true)
                Log.i(TAG, "Payment status poll billId=$billId attempt=${pollAttempts + 1} status=${status.paymentStatus} gatewayTxnId=${status.gatewayTxnId}")
                if (status.paymentStatus == "paid" || status.paymentStatus == "success") {
                    paid = true
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Payment status poll failed billId=$billId attempt=${pollAttempts + 1}: ${e.message}")
            }
            pollAttempts++
            if (pollAttempts < 10) delay(3000)
        }
        try {
            val verifyResponse = paymentRepository.verify(billId)
            Log.i(TAG, "Payment verification result billId=$billId status=${verifyResponse.status} txnid=${verifyResponse.txnid} easebuzzId=${verifyResponse.easebuzzId}")
            if (verifyResponse.status == "success") {
                screenState = PaymentScreenState.SUCCESS
                KhanaToast.show("Payment successful!", ToastKind.Success)
                onPaymentComplete(verifyResponse.txnid ?: txnidFromSdk)
            } else {
                errorMessage = if (paid) "Payment taken but verification pending — check in orders" else "Payment verification failed"
                screenState = if (paid) PaymentScreenState.SUCCESS else PaymentScreenState.FAILED
                if (paid) onPaymentComplete(txnidFromSdk)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Payment verification failed billId=$billId currentTxnid=$currentTxnid: ${e.message}")
            errorMessage = if (paid) "Payment taken but verification pending — check in orders" else (e.message ?: "Verification failed")
            screenState = if (paid) PaymentScreenState.SUCCESS else PaymentScreenState.FAILED
            if (paid) onPaymentComplete(txnidFromSdk)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            val paymentResult = data.getStringExtra("result")
            val paymentResponse = data.getStringExtra("payment_response")
            Log.i(TAG, "Easebuzz SDK callback billId=$billId txnid=$currentTxnid result=$paymentResult response=$paymentResponse")
            if (paymentResult == "payment_successfull") {
                if (!verificationStarted) {
                    verificationStarted = true
                    sdkScope.launch {
                        verifyAndComplete(paymentResponse)
                    }
                }
            } else if (paymentResult == "user_cancelled" || paymentResult == "back_pressed") {
                if (!verificationStarted) {
                    verificationStarted = true
                    sdkScope.launch {
                        verifyAndComplete(paymentResponse ?: currentTxnid)
                        if (screenState != PaymentScreenState.SUCCESS) {
                            errorMessage = "Payment cancelled by user"
                            screenState = PaymentScreenState.FAILED
                        }
                    }
                }
            } else {
                if (!verificationStarted) {
                    verificationStarted = true
                    sdkScope.launch {
                        verifyAndComplete(paymentResponse ?: currentTxnid)
                        if (screenState != PaymentScreenState.SUCCESS) {
                            errorMessage = paymentResult ?: "Payment failed or was cancelled"
                            screenState = PaymentScreenState.FAILED
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "Easebuzz SDK callback missing data billId=$billId txnid=$currentTxnid")
            if (!verificationStarted) {
                verificationStarted = true
                sdkScope.launch {
                    verifyAndComplete(currentTxnid)
                    if (screenState != PaymentScreenState.SUCCESS) {
                        errorMessage = "No payment response received from SDK"
                        screenState = PaymentScreenState.FAILED
                    }
                }
            }
        }
    }

    fun launchSdk() {
        val token = accessToken ?: return
        screenState = PaymentScreenState.PROCESSING
        sdkAttempted = true
        try {
            Log.i(TAG, "Launching Easebuzz SDK billId=$billId txnid=$currentTxnid payMode=$payMode accessKeyLength=${token.length}")
            val intent = paymentRepository.createSdkIntent(token, payMode, context)
            sdkLaunched = true
            launcher.launch(intent)
        } catch (e: Exception) {
            val url = paymentUrl
            if (url != null) {
                Log.w(TAG, "Easebuzz SDK unavailable; launching fallback billId=$billId txnid=$currentTxnid: ${e.message}")
                sdkLaunched = true
                paymentRepository.launchFallback(context, url)
            } else {
                errorMessage = e.message ?: "SDK unavailable"
                screenState = PaymentScreenState.FAILED
            }
        }
    }

    LaunchedEffect(screenState, accessToken) {
        if (screenState == PaymentScreenState.READY && accessToken != null && !sdkLaunched) {
            launchSdk()
        }
    }

    suspend fun retryPayment() {
        screenState = PaymentScreenState.INITIALIZING
        errorMessage = null
        retryCount = 0
        sdkAttempted = false
        sdkLaunched = false
        verificationStarted = false
        createOrderWithRetry(forceNewAttempt = true)
    }

    suspend fun openFreshBrowserFlow() {
        screenState = PaymentScreenState.INITIALIZING
        errorMessage = null
        sdkAttempted = true
        sdkLaunched = false
        verificationStarted = false
        createOrderWithRetry(forceNewAttempt = true)
        val freshUrl = paymentUrl
        if (freshUrl != null) {
            Log.i(TAG, "Launching fresh browser payment billId=$billId txnid=$currentTxnid")
            sdkLaunched = true
            paymentRepository.launchFallback(context, freshUrl)
        } else {
            errorMessage = "Unable to create fresh browser payment session"
            screenState = PaymentScreenState.FAILED
        }
    }

    LaunchedEffect(Unit) {
        createOrderWithRetry()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sdkLaunched) {
                if (!verificationStarted) {
                    verificationStarted = true
                    sdkScope.launch {
                        verifyAndComplete()
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Midnight purple gradient background — matches Login/SignUp/Splash
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.kbHeaderGradient
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Purple gradient header bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.khanabook.lite.pos.ui.designsystem.KhanaBookBackButton(onClick = onBack)
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Column {
                        Text(
                            text = "Payment",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ORDER #KB-$billId",
                            color = KbLavender,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Body
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = layout.contentPadding, end = layout.contentPadding, bottom = layout.contentPadding)
            ) {

            // Responsive Layout Selection: Landscape/Tablet Split vs Portrait Column
            if (layout.isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: OneScore Circular Gauge Status Meter
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        EasebuzzStatusGauge(
                            state = screenState,
                            amount = amount,
                            modifier = Modifier.size(240.dp)
                        )
                    }

                    // Right Column: Amount + Actions
                    Column(
                        modifier = Modifier
                            .weight(1.8f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AmountDisplay(amount = amount, spacing = spacing)

                        Spacer(modifier = Modifier.height(spacing.medium))

                        ActionButtonsArea(
                            state = screenState,
                            amount = amount,
                            payMode = payMode,
                            sdkAttempted = sdkAttempted,
                            paymentUrl = paymentUrl,
                            retryCount = retryCount,
                            errorMessage = errorMessage,
                            spacing = spacing,
                            launchSdk = { launchSdk() },
                            retryPayment = { scope.launch { retryPayment() } },
                            openFreshBrowserFlow = { scope.launch { openFreshBrowserFlow() } },
                            onBack = onBack,
                            context = context,
                            paymentRepository = paymentRepository
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .widthIn(max = 480.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    // Centered circular gauge (auto-sizes down on compact devices like POS terminals)
                    val gaugeSize = if (layout.isCompactForm) 170.dp else 220.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        EasebuzzStatusGauge(
                            state = screenState,
                            amount = amount,
                            modifier = Modifier.size(gaugeSize)
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.small))

                    AmountDisplay(amount = amount, spacing = spacing)

                    Spacer(modifier = Modifier.height(spacing.medium))

                    ActionButtonsArea(
                        state = screenState,
                        amount = amount,
                        payMode = payMode,
                        sdkAttempted = sdkAttempted,
                        paymentUrl = paymentUrl,
                        retryCount = retryCount,
                        errorMessage = errorMessage,
                        spacing = spacing,
                        launchSdk = { launchSdk() },
                        retryPayment = { scope.launch { retryPayment() } },
                        openFreshBrowserFlow = { scope.launch { openFreshBrowserFlow() } },
                        onBack = onBack,
                        context = context,
                        paymentRepository = paymentRepository
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun AmountDisplay(
    amount: BigDecimal,
    spacing: com.khanabook.lite.pos.ui.theme.Spacing
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Total Amount Due",
            color = MaterialTheme.kbTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            text = "₹$amount",
            color = MaterialTheme.kbTextPrimary,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(KbSuccess.copy(alpha = 0.12f))
                .border(1.dp, KbSuccess.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = "SECURE TERMINAL ACTIVE",
                color = KbSuccess,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun EasebuzzDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.kbTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = MaterialTheme.kbTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EasebuzzDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = KbOpacity.Divider))
    )
}


@Composable
private fun ActionButtonsArea(
    state: PaymentScreenState,
    amount: BigDecimal,
    payMode: String,
    sdkAttempted: Boolean,
    paymentUrl: String?,
    retryCount: Int,
    errorMessage: String?,
    spacing: com.khanabook.lite.pos.ui.theme.Spacing,
    launchSdk: () -> Unit,
    retryPayment: () -> Unit,
    openFreshBrowserFlow: () -> Unit,
    onBack: () -> Unit,
    context: android.content.Context,
    paymentRepository: EasebuzzSdkPaymentRepository
) {
    when (state) {
        PaymentScreenState.INITIALIZING -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.kbSecondary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(
                    text = if (retryCount > 0) "Network issue, retrying ($retryCount/$MAX_RETRIES)..."
                    else "Initiating secure session...",
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PaymentScreenState.READY -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = launchSdk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KbButtonSize.HeightLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary),
                    shape = KhanaShapes.medium
                ) {
                    Text(
                        text = "Process Payment",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pay ₹$amount securely online via Easebuzz",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        PaymentScreenState.PROCESSING -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.kbSecondary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(
                    text = "Awaiting checkout response...",
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PaymentScreenState.VERIFYING -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.kbSecondary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(
                    text = "Confirming payment with bank...",
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PaymentScreenState.SUCCESS -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KbSuccess.copy(alpha = 0.1f))
                    .border(1.dp, KbSuccess.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(spacing.medium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Paid",
                        tint = KbSuccess,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "Transaction completed successfully!",
                        color = KbSuccess,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        PaymentScreenState.FAILED -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Payment Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = retryPayment,
                        modifier = Modifier.widthIn(min = 160.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KbBrandSaffron.copy(alpha = 0.15f),
                            contentColor = KbBrandSaffron
                        )
                    ) {
                        Text("Retry", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (paymentUrl != null) {
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text(
                        text = if (sdkAttempted) "Native payment failed — try paying via custom tabs browser" else "Alternative web payment flow available",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)
                    )
                    OutlinedButton(
                        onClick = {
                            openFreshBrowserFlow()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border = BorderStroke(1.dp, MaterialTheme.kbTertiary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbTertiary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Browser",
                            tint = MaterialTheme.kbTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Browser Flow", fontWeight = FontWeight.Bold, color = MaterialTheme.kbTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun EasebuzzStatusGauge(
    state: PaymentScreenState,
    amount: BigDecimal,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gauge_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isLoaderState = state == PaymentScreenState.INITIALIZING ||
            state == PaymentScreenState.PROCESSING ||
            state == PaymentScreenState.VERIFYING

    Box(
        modifier = modifier
            .graphicsLayer {
                if (isLoaderState) {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Immersive Glow Base
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.center
            val radius = size.minDimension / 2
            val glowColor = when (state) {
                PaymentScreenState.SUCCESS -> KbSuccess.copy(alpha = 0.2f)
                PaymentScreenState.FAILED -> KbBrandRed.copy(alpha = 0.2f)
                else -> KbBrandSaffron.copy(alpha = 0.15f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
        }

        // Circular OneScore-style visual meter
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer {
                    if (isLoaderState) {
                        rotationZ = rotation
                    }
                }
        ) {
            val strokeWidth = 8.dp.toPx()
            val center = size.center
            val radius = size.minDimension / 2

            // 1. Quantum Tick Marks
            val tickCount = 48
            val tickLength = 6.dp.toPx()
            val tickDistance = radius - 10.dp.toPx()
            for (i in 0 until tickCount) {
                val angleDegrees = (i * 360f / tickCount)
                val angleRadians = Math.toRadians(angleDegrees.toDouble())
                val startX = center.x + (tickDistance * Math.cos(angleRadians)).toFloat()
                val startY = center.y + (tickDistance * Math.sin(angleRadians)).toFloat()
                val endX = center.x + ((tickDistance - tickLength) * Math.cos(angleRadians)).toFloat()
                val endY = center.y + ((tickDistance - tickLength) * Math.sin(angleRadians)).toFloat()
                
                val tickColor = when (state) {
                    PaymentScreenState.SUCCESS -> KbSuccess.copy(alpha = 0.3f)
                    PaymentScreenState.FAILED -> KbBrandRed.copy(alpha = 0.4f)
                    else -> KbBrandSaffron.copy(alpha = 0.2f)
                }
                drawLine(
                    color = tickColor,
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 2. Track Line
            drawCircle(
                color = Color.White.copy(alpha = KbOpacity.Border),
                radius = radius - 24.dp.toPx(),
                style = Stroke(width = strokeWidth)
            )

            // 3. Glowing Progress Arc
            val arcBrush = when (state) {
                PaymentScreenState.SUCCESS -> Brush.sweepGradient(listOf(KbSuccess,                 KbSuccess, KbSuccess))
                PaymentScreenState.FAILED -> Brush.sweepGradient(listOf(KbError, KbError, KbError))
                else -> Brush.sweepGradient(listOf(KbBrandSaffron.copy(alpha = 0.4f), KbBrandSaffron, KbBrandSaffron.copy(alpha = 0.4f)))
            }

            if (isLoaderState) {
                drawArc(
                    brush = arcBrush,
                    startAngle = 0f,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(24.dp.toPx(), 24.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 48.dp.toPx(), size.height - 48.dp.toPx())
                )
            } else {
                drawCircle(
                    brush = arcBrush,
                    radius = radius - 24.dp.toPx(),
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // Inner Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            when (state) {
                PaymentScreenState.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = KbSuccess,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SECURE",
                        color = KbSuccess,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                PaymentScreenState.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = KbBrandRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "FAILED",
                        color = KbBrandRed,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secured",
                        tint = KbBrandSaffron.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₹$amount",
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (state) {
                            PaymentScreenState.INITIALIZING -> "TUNNELING"
                            PaymentScreenState.PROCESSING -> "PROCESSING"
                            PaymentScreenState.VERIFYING -> "VERIFYING"
                            else -> "READY"
                        },
                        color = KbBrandSaffron.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}
