@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.newbill

import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.khanabook.lite.pos.data.local.entity.getInvoiceNumberDisplay
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.ConnectionStatus
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.sendInvoiceViaSms
import com.khanabook.lite.pos.domain.util.shareInstantInvoiceLink
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import java.util.Locale

@Composable
fun SuccessStep(
        viewModel: BillingViewModel,
        settingsViewModel: SettingsViewModel,
        onDone: () -> Unit,
        onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lastBill by viewModel.lastBill.collectAsStateWithLifecycle()
    val printStatus by viewModel.printStatus.collectAsStateWithLifecycle()
    val receiptPrinting by viewModel.receiptPrinting.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val totalAmount = lastBill?.bill?.totalAmount?.toDoubleOrNull() ?: 0.0
    val scope = rememberCoroutineScope()
    var isSharingInvoice by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                isTtsReady = status == TextToSpeech.SUCCESS
            }
            tts.value = engine
        } catch (e: Exception) {
            android.util.Log.e("NewBillScreen", "Failed to initialize TextToSpeech engine", e)
            isTtsReady = false
        }
        onDispose {
            try {
                engine?.stop()
                engine?.shutdown()
            } catch (e: Exception) {
                android.util.Log.e("NewBillScreen", "Error during TextToSpeech shutdown", e)
            }
            tts.value = null
            isTtsReady = false
        }
    }

    LaunchedEffect(isTtsReady, lastBill?.bill?.id) {
        if (!isTtsReady || lastBill == null) return@LaunchedEffect
        tts.value?.let { ttsEngine ->
            try {
                ttsEngine.language = Locale("en", "IN")
                ttsEngine.speak(
                    "Payment of ${formatAmountForSpeech(totalAmount)} received successfully.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "payment-success-${lastBill?.bill?.id}"
                )
            } catch (e: Exception) {
                android.util.Log.e("NewBillScreen", "Failed to speak total amount via TTS", e)
            }
        }
    }

    ScrollableCenteredLayout(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Button(
                    onClick = {
                        val currentBill = lastBill ?: return@Button
                        scope.launch {
                            isSharingInvoice = true
                            try {
                                if (connectionStatus == ConnectionStatus.Unavailable) {
                                    onShowMessage("Offline. Sharing invoice text by SMS.")
                                    sendInvoiceViaSms(context, currentBill, profile)
                                    return@launch
                                }

                                shareInstantInvoiceLink(context, currentBill, profile)
                            } finally {
                                isSharingInvoice = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightLarge),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = Color.White,
                        disabledContainerColor = WhatsAppGreen.copy(alpha = 0.35f),
                        disabledContentColor = Color.White.copy(alpha = 0.65f)
                    ),
                    shape = KhanaRadii.lg,
                    enabled = lastBill != null && !isSharingInvoice
                ) {
                    if (isSharingInvoice) {
                        KhanaInlineLoader(color = Color.White)
                        Spacer(modifier = Modifier.width(spacing.small))
                    } else {
                        Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(iconSize.small))
                        Spacer(modifier = Modifier.width(spacing.small))
                    }
                    Text(
                        text = if (isSharingInvoice) "Preparing Link" else "Share Invoice",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                KhanaPrimaryButton(
                    text = if (receiptPrinting) "Preparing Invoice" else "Print Invoice",
                    onClick = { lastBill?.let { viewModel.printReceipt(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = lastBill?.let { it.bill.orderStatus != "cancelled" } == true && !receiptPrinting,
                    isLoading = receiptPrinting,
                    leadingIcon = Icons.Default.Receipt
                )

                Spacer(modifier = Modifier.height(spacing.small))

                KhanaSecondaryButton(
                    text = "Back to Home",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Home
                )
            }
        }
    ) {
        PaymentSuccessBadge()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier.padding(top = spacing.small)
        ) {
            Text(
                "Payment Successful!",
                color = TextLight,
                style = MaterialTheme.typography.headlineSmall
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(SuccessGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Payment successful",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // Receipt Summary Card
        KhanaBookCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium)
                .border(BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f)), KhanaRadii.lg),
            colors = CardDefaults.cardColors(containerColor = CardBG.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TRANSACTION SUMMARY",
                    color = TextGold,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(spacing.medium))

                // Invoice No & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Invoice No:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = lastBill?.let { it.bill.getInvoiceNumberDisplay() } ?: "N/A",
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Mode:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Surface(
                        color = lastBill?.let { getPayModeColor(PaymentMode.fromDbValue(it.bill.paymentMode)) } ?: Color.Gray,
                        shape = KhanaRadii.sm
                    ) {
                        Text(
                            text = lastBill?.let { PaymentMode.fromDbValue(it.bill.paymentMode).displayLabel } ?: "N/A",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Items Ordered:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = lastBill?.let { "${it.items.size} item${if (it.items.size == 1) "" else "s"}" } ?: "N/A",
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))
                HorizontalDivider(color = BorderGold.copy(alpha = 0.15f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(spacing.medium))

                Text(
                    text = "Amount Received",
                    color = TextGold,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    text = CurrencyUtils.formatPrice(totalAmount),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        // Live Print Status or Connection Badge
        val liveStatus = printStatus ?: if (connectionStatus == ConnectionStatus.Unavailable) "Offline" else ""
        if (liveStatus.isNotEmpty()) {
            Surface(
                color = if (liveStatus.contains("failed") || liveStatus == "Offline") DangerRed.copy(alpha = 0.15f) else PrimaryGold.copy(alpha = 0.15f),
                shape = KhanaRadii.md,
                border = BorderStroke(1.dp, if (liveStatus.contains("failed") || liveStatus == "Offline") DangerRed.copy(alpha = 0.35f) else PrimaryGold.copy(alpha = 0.35f)),
                modifier = Modifier.padding(vertical = spacing.small)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (liveStatus.contains("failed") || liveStatus == "Offline") DangerRed else VegGreen,
                                CircleShape
                            )
                    )
                    Text(
                        text = liveStatus,
                        color = TextLight,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))
        LaunchedEffect(printStatus) {
            printStatus?.let { onShowMessage(it) }
        }
    }
}

@Composable
private fun PaymentSuccessBadge() {
    val scale = remember { Animatable(0f) }
    val ripple1Scale = remember { Animatable(1f) }
    val ripple1Alpha = remember { Animatable(0.4f) }
    val ripple2Scale = remember { Animatable(1f) }
    val ripple2Alpha = remember { Animatable(0.4f) }
    val confettiProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate checkmark circle scale with a bounce spring
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        // Animate ripple rings
        launch {
            ripple1Scale.animateTo(
                targetValue = 2.0f,
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
            )
        }
        launch {
            ripple1Alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
            )
        }
        // Delayed second ripple
        launch {
            kotlinx.coroutines.delay(200)
            launch {
                ripple2Scale.animateTo(
                    targetValue = 2.0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
                )
            }
            launch {
                ripple2Alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
                )
            }
        }
        // Confetti burst
        launch {
            kotlinx.coroutines.delay(300)
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .size(KhanaBookTheme.layout.heroImageSize),
        contentAlignment = Alignment.Center
    ) {
        // Outer Ripple 1
        Box(
            modifier = Modifier
                .size(KhanaBookTheme.iconSize.hero)
                .graphicsLayer(
                    scaleX = ripple1Scale.value,
                    scaleY = ripple1Scale.value,
                    alpha = ripple1Alpha.value
                )
                .background(SuccessGreen.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, SuccessGreen.copy(alpha = 0.25f), CircleShape)
        )

        // Outer Ripple 2
        Box(
            modifier = Modifier
                .size(KhanaBookTheme.iconSize.hero)
                .graphicsLayer(
                    scaleX = ripple2Scale.value,
                    scaleY = ripple2Scale.value,
                    alpha = ripple2Alpha.value
                )
                .background(SuccessGreen.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, SuccessGreen.copy(alpha = 0.25f), CircleShape)
        )

        // Main Checkmark Circle
        Box(
            modifier = Modifier
                .size(KhanaBookTheme.iconSize.hero)
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value
                )
                .background(SuccessGreen, CircleShape)
                .border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Payment successful",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        // Confetti Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val center = this.center
            val progress = confettiProgress.value
            if (progress > 0f && progress < 1f) {
                val numParticles = 16
                val maxRadius = 140.dp.toPx()
                val particleColors = listOf(
                    Color(0xFF4285F4), // Google Blue
                    Color(0xFFEA4335), // Google Red
                    Color(0xFFFBBC05), // Google Yellow
                    Color(0xFF34A853), // Google Green
                    Color(0xFFFF007F)  // Pink
                )
                for (i in 0 until numParticles) {
                    val angle = (i * 360f / numParticles) * (Math.PI / 180f)
                    val distance = maxRadius * progress
                    val x = center.x + (Math.cos(angle) * distance).toFloat()
                    val y = center.y + (Math.sin(angle) * distance).toFloat()
                    val color = particleColors[i % particleColors.size]
                    val size = 6.dp.toPx() * (1f - progress)
                    
                    // Draw alternating stars/squares/circles
                    when (i % 3) {
                        0 -> drawCircle(color = color, radius = size / 2, center = androidx.compose.ui.geometry.Offset(x, y))
                        1 -> drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(x - size/2, y - size/2), size = androidx.compose.ui.geometry.Size(size, size))
                        else -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x, y - size/2)
                                lineTo(x + size/4, y - size/4)
                                lineTo(x + size/2, y)
                                lineTo(x + size/4, y + size/4)
                                lineTo(x, y + size/2)
                                lineTo(x - size/4, y + size/4)
                                lineTo(x - size/2, y)
                                lineTo(x - size/4, y - size/4)
                                close()
                            }
                            drawPath(path = path, color = color)
                        }
                    }
                }
            }
        }
    }
}

private fun formatAmountForSpeech(amount: Double): String {
    val totalPaise = (amount * 100).roundToLong().coerceAtLeast(0)
    val rupees = totalPaise / 100
    val paise = totalPaise % 100
    return if (paise == 0L) {
        "$rupees rupees"
    } else {
        "$rupees rupees and $paise paise"
    }
}

internal suspend fun loadShopLogoBlocking(
    context: android.content.Context,
    logoUrl: String?,
    logoPath: String?
): android.graphics.Bitmap? {
    if (!logoUrl.isNullOrBlank()) {
        try {
            val request = ImageRequest.Builder(context)
                .data(logoUrl)
                .allowHardware(false)
                .size(128)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
            if (bitmap != null) return bitmap
        } catch (_: Exception) { }
    }
    return AppAssetStore.resolveAssetPath(logoPath)?.let { path ->
        try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) { null }
    }
}

private fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500
        PaymentMode.POS -> PrimaryGold
        else -> Brown500
    }
}
