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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.BrandPurple
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaShapes
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.RichEspresso
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.TextMuted
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

    // Main dark theme viewport with horizontal & vertical gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBrown1) // Background (#0F0E1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(layout.contentPadding)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x11FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(spacing.medium))
                Column {
                    Text(
                        text = "Easebuzz Checkout",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Secured session tunnel",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

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

                    // Right Column: Details list & actions
                    Column(
                        modifier = Modifier
                            .weight(1.8f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        TransactionDetailsCard(
                            restaurantId = restaurantId,
                            billId = billId,
                            amount = amount,
                            payMode = payMode,
                            spacing = spacing
                        )

                        Spacer(modifier = Modifier.height(spacing.large))

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

                    TransactionDetailsCard(
                        restaurantId = restaurantId,
                        billId = billId,
                        amount = amount,
                        payMode = payMode,
                        spacing = spacing
                    )

                    Spacer(modifier = Modifier.height(spacing.large))

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

@Composable
private fun TransactionDetailsCard(
    restaurantId: Long,
    billId: Long,
    amount: BigDecimal,
    payMode: String,
    spacing: com.khanabook.lite.pos.ui.theme.Spacing
) {
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium)
        ) {
            Text(
                text = "Transaction Details",
                color = TextGold,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = spacing.small)
            )

            EasebuzzDetailRow(label = "Bill number reference", value = "#$billId")
            EasebuzzDivider()
            EasebuzzDetailRow(label = "Subtotal payable", value = "₹$amount")
            EasebuzzDivider()
            EasebuzzDetailRow(label = "Terminal transaction", value = "ONLINE")
            EasebuzzDivider()
            EasebuzzDetailRow(label = "Restaurant ID", value = "$restaurantId")

            val modeLabel = if ("test" == payMode) "Sandbox Tunnel" else "Live Gateway"
            val modeColor = if ("test" == payMode) PrimaryGold else SuccessGreen
            EasebuzzDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gateway context",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(modeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = modeLabel,
                        color = modeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
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
                    color = PrimaryGold,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(
                    text = if (retryCount > 0) "Network issue, retrying ($retryCount/$MAX_RETRIES)..."
                    else "Initiating secure session...",
                    color = TextGold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PaymentScreenState.READY -> {
            Button(
                onClick = launchSdk,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp), // Height = 52.dp
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold), // Background = AccentPrimary
                shape = KhanaShapes.medium // Shape = Button shape (12.dp)
            ) {
                Text(
                    text = "Pay ₹$amount",
                    color = Color.White, // Text = White
                    fontSize = 14.sp, // 14sp
                    fontWeight = FontWeight.Medium // FontWeight.Medium
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
                    color = PrimaryGold,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(
                    text = "Awaiting checkout response...",
                    color = TextGold,
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
                    color = PrimaryGold,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Text(
                    text = "Confirming payment with bank...",
                    color = TextGold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PaymentScreenState.SUCCESS -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SuccessGreen.copy(alpha = 0.1f))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
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
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "Transaction completed successfully!",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        PaymentScreenState.FAILED -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1AFF5252))
                        .border(1.dp, Color(0x33FF5252), RoundedCornerShape(16.dp))
                        .padding(spacing.medium)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Column {
                            Text(
                                text = "Payment Failed",
                                color = Color(0xFFFF5252),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage,
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                if (com.khanabook.lite.pos.ui.theme.KhanaBookTheme.layout.isCompactForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Button(
                            onClick = retryPayment,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = KhanaShapes.medium
                        ) {
                            Text(
                                text = "Retry Payment",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            border = BorderStroke(0.5.dp, BorderGold),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardBG,
                                contentColor = TextLight
                            ),
                            shape = KhanaShapes.medium
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Button(
                            onClick = retryPayment,
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = KhanaShapes.medium
                        ) {
                            Text(
                                text = "Retry Payment",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(52.dp),
                            border = BorderStroke(0.5.dp, BorderGold),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardBG,
                                contentColor = TextLight
                            ),
                            shape = KhanaShapes.medium
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (paymentUrl != null) {
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text(
                        text = if (sdkAttempted) "Native payment failed — try paying via custom tabs browser" else "Alternative web payment flow available",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.small)
                    )
                    OutlinedButton(
                        onClick = {
                            openFreshBrowserFlow()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border = BorderStroke(1.dp, PrimaryGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Browser",
                            tint = PrimaryGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Browser Flow", fontWeight = FontWeight.Bold)
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
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
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
        // Soft radial background glow matching state
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.center
            val radius = size.minDimension / 2
            val glowColor = when (state) {
                PaymentScreenState.SUCCESS -> SuccessGreen.copy(alpha = 0.15f)
                PaymentScreenState.FAILED -> Color(0x26FF5252)
                else -> BrandPurple.copy(alpha = 0.15f)
            }
            val brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = center,
                radius = radius
            )
            drawCircle(brush = brush, radius = radius)
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

            // 1. Decorative outer boundary ring (thin)
            drawCircle(
                color = Color(0x22FFFFFF),
                radius = radius - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Dashboard tick marks (dials) around the gauge
            val tickCount = 36
            val tickLength = 5.dp.toPx()
            val tickDistance = radius - 12.dp.toPx()
            for (i in 0 until tickCount) {
                val angleDegrees = (i * 360f / tickCount)
                val angleRadians = Math.toRadians(angleDegrees.toDouble())
                val startX = center.x + (tickDistance * Math.cos(angleRadians)).toFloat()
                val startY = center.y + (tickDistance * Math.sin(angleRadians)).toFloat()
                val endX = center.x + ((tickDistance - tickLength) * Math.cos(angleRadians)).toFloat()
                val endY = center.y + ((tickDistance - tickLength) * Math.sin(angleRadians)).toFloat()
                
                val tickColor = when (state) {
                    PaymentScreenState.SUCCESS -> SuccessGreen.copy(alpha = 0.25f)
                    PaymentScreenState.FAILED -> Color(0x4DFF5252)
                    else -> PrimaryGold.copy(alpha = 0.2f)
                }
                drawLine(
                    color = tickColor,
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 3. Full background track line
            drawCircle(
                color = Color(0x11FFFFFF),
                radius = radius - 20.dp.toPx(),
                style = Stroke(width = strokeWidth)
            )

            // 4. State-specific indicator arc/circle on the track
            when (state) {
                PaymentScreenState.INITIALIZING,
                PaymentScreenState.PROCESSING,
                PaymentScreenState.VERIFYING -> {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(BrandPurple, PrimaryGold, BrandPurple)
                        ),
                        startAngle = 0f,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(20.dp.toPx(), 20.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 40.dp.toPx(), size.height - 40.dp.toPx())
                    )
                }
                PaymentScreenState.SUCCESS -> {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(SuccessGreen, Color(0xFF00E676))
                        ),
                        radius = radius - 20.dp.toPx(),
                        style = Stroke(width = strokeWidth)
                    )
                }
                PaymentScreenState.FAILED -> {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFC62828), Color(0xFFFF5252))
                        ),
                        radius = radius - 20.dp.toPx(),
                        style = Stroke(width = strokeWidth)
                    )
                }
                PaymentScreenState.READY -> {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(PrimaryGold, TextGold)
                        ),
                        radius = radius - 20.dp.toPx(),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            // 5. Decorative inner boundary ring (thin)
            drawCircle(
                color = Color(0x18FFFFFF),
                radius = radius - 28.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Internal textual/icon content centered inside the gauge
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
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "VERIFIED",
                        color = Color(0xFF00E676),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                PaymentScreenState.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "FAILED",
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure Link",
                        tint = PrimaryGold.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₹$amount",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (state) {
                            PaymentScreenState.INITIALIZING -> "INITIALIZING"
                            PaymentScreenState.PROCESSING -> "PROCESSING"
                            PaymentScreenState.VERIFYING -> "VERIFYING"
                            else -> "READY TO PAY"
                        },
                        color = TextGold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }
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
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = TextLight,
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
            .background(Color(0x1AFFFFFF))
    )
}
