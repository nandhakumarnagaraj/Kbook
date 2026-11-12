@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.util.ValidationUtils
import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import com.khanabook.lite.pos.feature.settings.viewmodel.QuickStartViewModel

data class QuickMenuItem(
    val name: String = "",
    val price: String = ""
)

@Composable
fun QuickStartScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: QuickStartViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val iconSize = KhanaBookTheme.iconSize
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val initialPhone = remember(currentUser) {
        currentUser?.whatsappNumber?.takeIf { it.isNotBlank() }
            ?: currentUser?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: ""
    }

    var shopName by remember { mutableStateOf("") }
    var whatsappNumber by remember { mutableStateOf("") }
    var isOtpVerified by remember { mutableStateOf(false) }
    var otpSent by remember { mutableStateOf(false) }
    var otpValue by remember { mutableStateOf("") }
    var otpTimer by remember { mutableIntStateOf(60) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf<String?>(null) }

    var menuItems by remember {
        mutableStateOf(
            listOf(
                QuickMenuItem(),
                QuickMenuItem(),
                QuickMenuItem()
            )
        )
    }

    // Pre-fill phone if user already has a verified number
    LaunchedEffect(initialPhone) {
        if (initialPhone.isNotBlank() && whatsappNumber.isBlank()) {
            whatsappNumber = initialPhone
            isOtpVerified = true
        }
    }

    val otpStatus by authViewModel.otpVerificationStatus.collectAsStateWithLifecycle()

    LaunchedEffect(otpStatus) {
        when (otpStatus) {
            is AuthViewModel.OtpVerificationResult.OtpSent -> {
                isSendingOtp = false
                isVerifyingOtp = false
                otpSent = true
                otpTimer = 60
                otpError = null
                coroutineScope.launch {
                    KhanaToast.show(context.getString(R.string.toast_otp_sent), ToastKind.Info)
                }
            }
            is AuthViewModel.OtpVerificationResult.Success -> {
                isSendingOtp = false
                isVerifyingOtp = false
                isOtpVerified = true
                otpSent = false
                otpError = null
                coroutineScope.launch {
                    KhanaToast.show("WhatsApp number verified", ToastKind.Success)
                }
            }
            is AuthViewModel.OtpVerificationResult.Error -> {
                isSendingOtp = false
                isVerifyingOtp = false
                val msg = (otpStatus as? AuthViewModel.OtpVerificationResult.Error)?.message ?: "Verification failed"
                otpError = msg
                coroutineScope.launch {
                    KhanaToast.show(msg, ToastKind.Error)
                }
            }
            null -> {
                isSendingOtp = false
                isVerifyingOtp = false
            }
        }
    }

    LaunchedEffect(otpSent, otpTimer) {
        if (otpSent && otpTimer > 0) {
            delay(1000L)
            otpTimer--
        }
    }

    val isPhoneValid = whatsappNumber.isEmpty() || ValidationUtils.isValidPhone(whatsappNumber)
    val phoneRequirementMet = whatsappNumber.isEmpty() || isOtpVerified
    val canSubmit = shopName.isNotBlank() &&
        menuItems.any { it.name.isNotBlank() && it.price.isNotBlank() } &&
        phoneRequirementMet

    var isSkipVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(5000L)
        isSkipVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.completionEvent.collect {
            authViewModel.clearOtpStatus()
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
            .statusBarsPadding()
    ) {
        StickyBottomScaffold(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                .align(Alignment.TopCenter),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.smallMedium)
                ) {
                    // Error display
                    if (error != null) {
                        Text(
                            text = error ?: "",
                            color = ErrorPink,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = spacing.small)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val tutorialUrl = "https://kbook.iadv.cloud/cdn/tutorials/create-quickbill.webm"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tutorialUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    coroutineScope.launch {
                                        KhanaToast.show("Unable to open tutorial video", ToastKind.Warning)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = KhanaRadii.button,
                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextGold
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(iconSize.small),
                                tint = PrimaryGold
                            )
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            Text(
                                text = "Watch Tutorials",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (whatsappNumber.isNotBlank() && !isOtpVerified) {
                                    coroutineScope.launch {
                                        KhanaToast.show("Please verify WhatsApp number with OTP first", ToastKind.Warning)
                                    }
                                    return@Button
                                }
                                val validItems = menuItems.filter { it.name.isNotBlank() && it.price.isNotBlank() }
                                viewModel.completeQuickStart(
                                    shopName = shopName.trim(),
                                    items = validItems,
                                    whatsappNumber = whatsappNumber.trim().takeIf { it.isNotBlank() && isOtpVerified }
                                )
                            },
                            enabled = canSubmit && !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = KhanaRadii.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGold,
                                disabledContainerColor = PrimaryGold.copy(alpha = 0.3f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = DarkBrown1,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Start Billing",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DarkBrown1,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.small))

                    Text(
                        text = "You can always add categories, variants, GST, and printer settings later from the Settings tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            bottomBarContainerColor = DarkBrown1
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.large),
                verticalArrangement = Arrangement.Top
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PrimaryGold,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedVisibility(
                        visible = isSkipVisible,
                        enter = fadeIn(animationSpec = tween(400))
                    ) {
                        TextButton(onClick = onSkip) {
                            Text("Skip", color = TextGold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.small))

                Text(
                    text = "Let's get you billing in under a minute",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLight.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(spacing.large))

                // Shop Name & WhatsApp Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = KhanaRadii.card,
                    colors = CardDefaults.cardColors(containerColor = DarkBrown2)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Store,
                                contentDescription = null,
                                tint = PrimaryGold,
                                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                            )
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                text = "Your Shop",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))

                        KhanaBookInputField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = "Shop name",
                            placeholder = "e.g. Sharma's Chai Point",
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(spacing.medium))

                        val isPhone10 = whatsappNumber.length == 10

                        KhanaBookInputField(
                            value = whatsappNumber,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(10)
                                whatsappNumber = digits
                                if (digits != initialPhone) {
                                    isOtpVerified = false
                                    otpSent = false
                                    otpValue = ""
                                    otpError = null
                                }
                            },
                            label = "WhatsApp number",
                            placeholder = "10-digit mobile number",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = if (otpSent) ImeAction.Next else ImeAction.Done
                            ),
                            supportingText = {
                                Text(
                                    text = when {
                                        whatsappNumber.isNotEmpty() && !isPhoneValid -> "Enter valid 10-digit number"
                                        isOtpVerified -> "Verified WhatsApp number"
                                        else -> "Used for digital bills & customer updates"
                                    },
                                    color = when {
                                        whatsappNumber.isNotEmpty() && !isPhoneValid -> ErrorPink
                                        isOtpVerified -> SuccessGreen
                                        else -> TextGold.copy(alpha = 0.7f)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            isError = whatsappNumber.isNotEmpty() && !isPhoneValid,
                            trailingIcon = {
                                if (isOtpVerified) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(iconSize.small)
                                    )
                                } else if (isPhone10 && isPhoneValid) {
                                    if (isSendingOtp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = PrimaryGold,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                isSendingOtp = true
                                                authViewModel.sendOtp(whatsappNumber, "update_whatsapp")
                                            },
                                            enabled = !isSendingOtp && (otpTimer == 0 || !otpSent),
                                            shape = KhanaRadii.xl,
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text(
                                                text = if (otpSent && otpTimer > 0) "${otpTimer}s" else "Get OTP",
                                                color = DarkBrown1,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (otpSent && !isOtpVerified) {
                            Spacer(modifier = Modifier.height(spacing.medium))

                            KhanaBookInputField(
                                value = otpValue,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(6)
                                    otpValue = digits
                                    if (digits.length == 6) {
                                        focusManager.clearFocus()
                                        isVerifyingOtp = true
                                        authViewModel.confirmMobileNumberUpdate(whatsappNumber, digits)
                                    }
                                },
                                label = "Enter 6-digit OTP",
                                placeholder = "• • • • • •",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                supportingText = {
                                    Text(
                                        text = otpError ?: if (otpTimer > 0) "OTP expires in ${String.format("%02d:%02d", otpTimer / 60, otpTimer % 60)}" else "OTP expired. Tap Get OTP to resend.",
                                        color = if (otpError != null) ErrorPink else TextGold.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                isError = otpError != null,
                                trailingIcon = {
                                    if (isVerifyingOtp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = PrimaryGold,
                                            strokeWidth = 2.dp
                                        )
                                    } else if (otpTimer > 0) {
                                        Text(
                                            text = String.format("%02d:%02d", otpTimer / 60, otpTimer % 60),
                                            color = TextGold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // Menu Items Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = KhanaRadii.card,
                    colors = CardDefaults.cardColors(containerColor = DarkBrown2)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = PrimaryGold,
                                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                            )
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                text = "Your Menu",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.extraSmall))

                        Text(
                            text = "Add your most popular items. You can add more later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLight.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(spacing.medium))

                        menuItems.forEachIndexed { index, item ->
                            QuickMenuItemRow(
                                index = index,
                                item = item,
                                onNameChange = { newName ->
                                    menuItems = menuItems.toMutableList().also {
                                        it[index] = it[index].copy(name = newName)
                                    }
                                },
                                onPriceChange = { newPrice ->
                                    menuItems = menuItems.toMutableList().also {
                                        it[index] = it[index].copy(price = newPrice)
                                    }
                                },
                                onRemove = if (menuItems.size > 1) {
                                    { menuItems = menuItems.toMutableList().also { it.removeAt(index) } }
                                } else null
                            )

                            if (index < menuItems.lastIndex) {
                                Spacer(modifier = Modifier.height(spacing.small))
                            }
                        }

                        if (menuItems.size < 10) {
                            Spacer(modifier = Modifier.height(spacing.medium))
                            OutlinedButton(
                                onClick = {
                                    menuItems = menuItems + QuickMenuItem()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = KhanaRadii.button,
                                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextGold
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
                                Spacer(modifier = Modifier.width(spacing.extraSmall))
                                Text("Add another item")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))
            }
        }

        // Loading overlay
        KhanaBookLoadingOverlay(
            visible = isLoading,
            type = LoadingType.SAVING,
            message = "Setting up your shop..."
        )
    }
}

@Composable
private fun QuickMenuItemRow(
    index: Int,
    item: QuickMenuItem,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onRemove: (() -> Unit)?
) {
    val spacing = KhanaBookTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Item number badge
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .size(24.dp)
                .background(PrimaryGold.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryGold
            )
        }

        // Name field
        KhanaBookInputField(
            value = item.name,
            onValueChange = onNameChange,
            label = "Item name",
            placeholder = "e.g. Chai",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.weight(1f)
        )

        // Price field
        KhanaBookInputField(
            value = item.price,
            onValueChange = { newValue ->
                val filtered = newValue.filter { ch -> ch.isDigit() || ch == '.' }
                onPriceChange(filtered)
            },
            label = "₹ Price",
            placeholder = "₹",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.width(90.dp)
        )

        // Remove button
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove item",
                    tint = TextLight.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(spacing.extraLarge))
        }
    }
}
