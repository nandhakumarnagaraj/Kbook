@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.feature.settings.ui
import com.khanabook.lite.pos.feature.auth.ui.InlinePinEntry

import com.khanabook.lite.pos.feature.settings.ui.RestaurantPaymentFlowSelector
import com.khanabook.lite.pos.core.theme.KhanaRadii

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.khanabook.lite.pos.core.designsystem.verticalScrollbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import com.khanabook.lite.pos.R
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode
import com.khanabook.lite.pos.core.util.AppAssetStore
import com.khanabook.lite.pos.core.util.UserMessageSanitizer
import com.khanabook.lite.pos.core.util.ValidationUtils
import com.khanabook.lite.pos.core.components.KhanaTimePickerField
import com.khanabook.lite.pos.core.components.ParchmentTextField
import com.khanabook.lite.pos.core.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.ToastKind
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ShopConfigView(
    profile: RestaurantProfileEntity?,
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    appLockViewModel: com.khanabook.lite.pos.feature.auth.viewmodel.AppLockViewModel = hiltViewModel(),
    onBack: () -> Unit,
    readOnly: Boolean = false,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val toastScope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val iconSize = KhanaBookTheme.iconSize
    val isCompactWidth = layout.isCompactForm
    var name by remember { mutableStateOf(profile?.shopName ?: "") }
    var address by remember { mutableStateOf(profile?.shopAddress ?: "") }
    var whatsapp by remember { mutableStateOf(profile?.whatsappNumber ?: "") }
    var email by remember { mutableStateOf(profile?.email ?: "") }
    var consent by remember { mutableStateOf(profile?.emailInvoiceConsent ?: false) }
    var reviewUrl by remember { mutableStateOf(profile?.reviewUrl ?: "") }
    var invoiceFooter by remember { mutableStateOf(profile?.invoiceFooter ?: "") }
    var openingTime by remember { mutableStateOf((profile?.openingTime ?: "08:00").take(5)) }
    var closingTime by remember { mutableStateOf((profile?.closingTime ?: "23:00").take(5)) }
    var pendingLogoUri by remember { mutableStateOf<Uri?>(null) }

    val logoUrl = profile?.logoUrl
    val logoPath = profile?.logoPath

    val saveProfileLoading by viewModel.saveProfileLoading.collectAsStateWithLifecycle()
    val saveProfileError by viewModel.saveProfileError.collectAsStateWithLifecycle()
    // saveProfileSuccess is consumed by SettingsScreen's shared effect via the onSaved
    // callback — collecting it here would leave a stale-copy hazard if re-added.
    val logoUploadLoading by viewModel.logoUploadLoading.collectAsStateWithLifecycle()
    val logoUploadError by viewModel.logoUploadError.collectAsStateWithLifecycle()
    val isUserChecking by viewModel.isUserChecking.collectAsStateWithLifecycle()
    val userExistsError by viewModel.userExistsError.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val enteredPin by appLockViewModel.enteredPin.collectAsStateWithLifecycle()
    val pinError by appLockViewModel.errorMessage.collectAsStateWithLifecycle()
    val isGoogleAuth = currentUser?.authProvider.equals("GOOGLE", ignoreCase = true)
    val isPhoneValid = ValidationUtils.isValidPhone(whatsapp)
    val numberChanged = whatsapp != (profile?.whatsappNumber ?: "")
    val emailChanged = !isGoogleAuth && email.trim() != (profile?.email?.trim() ?: "")
    val isEmailValid = ValidationUtils.isValidEmail(email.trim())
    val currentPaymentFlowMode = OrderPaymentFlowMode.fromDbValue(profile?.orderPaymentFlowMode)
    var selectedPaymentFlowMode by remember {
        mutableStateOf(currentPaymentFlowMode)
    }
    var pendingPaymentFlowMode by remember { mutableStateOf<OrderPaymentFlowMode?>(null) }
    var showModePinDialog by remember { mutableStateOf(false) }
    val isPinEnabled = remember { appLockViewModel.isPinEnabled() }

    val isDirty = remember(name, address, whatsapp, email, consent, reviewUrl, invoiceFooter, openingTime, closingTime, profile) {
        name != (profile?.shopName ?: "") ||
            address != (profile?.shopAddress ?: "") ||
            whatsapp != (profile?.whatsappNumber ?: "") ||
            email != (profile?.email ?: "") ||
            consent != (profile?.emailInvoiceConsent ?: false) ||
            reviewUrl != (profile?.reviewUrl ?: "") ||
            invoiceFooter != (profile?.invoiceFooter ?: "") ||
            openingTime != (profile?.openingTime ?: "08:00").take(5) ||
            closingTime != (profile?.closingTime ?: "23:00").take(5)
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty && !saveProfileLoading) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        KhanaBookDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = "Unsaved Changes",
            message = "You have unsaved changes. Discard them?"
        ) {
            TextButton(onClick = { showUnsavedDialog = false }) {
                Text("Keep Editing", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { showUnsavedDialog = false; onBack() }) {
                Text("Discard", color = DangerRed, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    LaunchedEffect(saveProfileError) {
        saveProfileError?.let { error ->
            toastScope.launch {
                KhanaToast.show(
                    UserMessageSanitizer.sanitizeBackendMessage(error, "Couldn't save profile. Please try again."),
                    ToastKind.Error,
                )
            }
            viewModel.clearSaveProfileState()
        }
    }

    LaunchedEffect(logoUploadError) {
        logoUploadError?.let { error ->
            toastScope.launch {
                KhanaToast.show(error, ToastKind.Error)
            }
            viewModel.clearLogoUploadState()
        }
    }

    // Success handling moved to SettingsScreen's shared success effect (onSaved callback
    // sets pendingSaveSection = "shop" there). A private LaunchedEffect here could be
    // disposed mid-save by the recomposition triggered when the save itself updates the
    // profile (orderPaymentFlowMode re-keys remembered state), swallowing the first
    // tap's toast + navigation — the reported "Save needs two clicks" on this screen.

    LaunchedEffect(enteredPin, showModePinDialog) {
        if (showModePinDialog && enteredPin.length == 4) {
            appLockViewModel.verifyPin {
                pendingPaymentFlowMode?.let { mode ->
                    selectedPaymentFlowMode = mode
                    viewModel.updateOrderPaymentFlowMode(mode)
                }
                pendingPaymentFlowMode = null
                showModePinDialog = false
                appLockViewModel.clearPin()
            }
        }
    }

    var otpValue by remember { mutableStateOf("") }
    val otpStatus by authViewModel.otpVerificationStatus.collectAsStateWithLifecycle()
    val otpFieldErrors by authViewModel.otpFieldErrors.collectAsStateWithLifecycle()

    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableIntStateOf(120) }
    var isOtpVerified by remember { mutableStateOf(false) }

    var emailOtpValue by remember { mutableStateOf("") }
    val emailOtpStatus by authViewModel.emailOtpVerificationStatus.collectAsStateWithLifecycle()
    val emailOtpFieldErrors by authViewModel.emailOtpFieldErrors.collectAsStateWithLifecycle()

    var emailOtpSent by remember { mutableStateOf(false) }
    var emailOtpTimer by remember { mutableIntStateOf(120) }
    var isEmailOtpVerified by remember { mutableStateOf(true) }
    var phoneServerError by remember { mutableStateOf<String?>(null) }
    var emailServerError by remember { mutableStateOf<String?>(null) }

    val isOtpLoading = otpStatus is AuthViewModel.OtpVerificationResult.Loading
    val isEmailOtpLoading = emailOtpStatus is AuthViewModel.OtpVerificationResult.Loading

    LaunchedEffect(profile) {
        profile?.let {
            phoneServerError = null
            emailServerError = null
            name = it.shopName ?: ""
            address = it.shopAddress ?: ""
            whatsapp = it.whatsappNumber ?: ""
            email = it.email ?: ""
            consent = it.emailInvoiceConsent
            reviewUrl = it.reviewUrl ?: ""
            invoiceFooter = it.invoiceFooter ?: ""
            openingTime = (it.openingTime ?: "08:00").take(5)
            closingTime = (it.closingTime ?: "23:00").take(5)
            selectedPaymentFlowMode = OrderPaymentFlowMode.fromDbValue(it.orderPaymentFlowMode)
            isOtpVerified = true
            isEmailOtpVerified = true
            otpSent = false
            emailOtpSent = false
        }
    }

    LaunchedEffect(whatsapp) {
        if (whatsapp.length < 10) {
            viewModel.clearUserCheck()
        }
    }

    LaunchedEffect(otpStatus) {
        when (otpStatus) {
            is AuthViewModel.OtpVerificationResult.OtpSent -> {
                otpSent = true
                isOtpVerified = false
                phoneServerError = null
                otpTimer = 120
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_otp_sent), ToastKind.Info) }
            }
            is AuthViewModel.OtpVerificationResult.Success -> {
                isOtpVerified = true
                phoneServerError = null
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_verified), ToastKind.Success) }
                authViewModel.clearOtpStatus()
            }
            is AuthViewModel.OtpVerificationResult.Error -> {
                val errorMsg = (otpStatus as? AuthViewModel.OtpVerificationResult.Error)?.message.orEmpty()
                val sanitized = UserMessageSanitizer.sanitizeBackendMessage(errorMsg, "Couldn't verify OTP. Please try again.")
                phoneServerError = sanitized
                isOtpVerified = false
                toastScope.launch {
                    KhanaToast.show(sanitized, ToastKind.Error)
                }
                authViewModel.clearOtpStatus()
            }
            else -> {}
        }
    }

    LaunchedEffect(emailOtpStatus) {
        when (emailOtpStatus) {
            is AuthViewModel.OtpVerificationResult.OtpSent -> {
                emailOtpSent = true
                isEmailOtpVerified = false
                emailServerError = null
                emailOtpTimer = 120
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_email_otp_sent), ToastKind.Info) }
            }
            is AuthViewModel.OtpVerificationResult.Success -> {
                isEmailOtpVerified = true
                emailServerError = null
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_verified), ToastKind.Success) }
                authViewModel.clearEmailOtpStatus()
            }
            is AuthViewModel.OtpVerificationResult.Error -> {
                val errorMsg = (emailOtpStatus as? AuthViewModel.OtpVerificationResult.Error)?.message.orEmpty()
                val sanitized = UserMessageSanitizer.sanitizeBackendMessage(errorMsg, "Couldn't verify OTP. Please try again.")
                emailServerError = sanitized
                isEmailOtpVerified = false
                toastScope.launch {
                    KhanaToast.show(sanitized, ToastKind.Error)
                }
                authViewModel.clearEmailOtpStatus()
            }
            else -> {}
        }
    }

    LaunchedEffect(otpSent, otpTimer) {
        if (otpSent && otpTimer > 0) {
            kotlinx.coroutines.delay(1000)
            otpTimer--
        }
    }

    LaunchedEffect(emailOtpSent, emailOtpTimer) {
        if (emailOtpSent && emailOtpTimer > 0) {
            kotlinx.coroutines.delay(1000)
            emailOtpTimer--
        }
    }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pendingLogoUri = it
        }
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .verticalScrollbar(scrollState, width = 3.dp)
                .verticalScroll(scrollState)
                .padding(layout.contentPadding)
        ) {
            // Card 1: Restaurant Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = KhanaRadii.lg,
                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Restaurant Details",
                                color = TextLight,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Configure your restaurant information",
                                color = TextGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (readOnly) {
                        com.khanabook.lite.pos.feature.settings.ui.ReadOnlyConfigNotice(
                            "Read-only: only the restaurant owner can edit the shop configuration."
                        )
                    }

                    // Logo Uploader Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier.padding(vertical = spacing.extraSmall)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(KhanaRadii.md)
                                .background(Color.White)
                                .border(1.dp, BorderGold.copy(alpha = 0.5f), KhanaRadii.md),
                            contentAlignment = Alignment.Center
                        ) {
                            val pendingModel = pendingLogoUri?.toString()
                            val remoteModel = logoUrl?.takeIf { it.isNotBlank() }
                            val localModel = AppAssetStore.resolveAssetPath(logoPath)
                            val logoModel = pendingModel ?: remoteModel ?: localModel
                            if (!logoModel.isNullOrBlank()) {
                                var loadStage by remember(logoModel) { mutableIntStateOf(0) }
                                var isLogoLoading by remember(logoModel) { mutableStateOf(true) }
                                val canFallbackToLocal =
                                    pendingModel == null && remoteModel != null &&
                                        localModel != null && logoModel == remoteModel
                                val effectiveModel = when (loadStage) {
                                    0 -> logoModel
                                    1 -> if (canFallbackToLocal) localModel else null
                                    else -> null
                                }
                                if (effectiveModel != null) {
                                    val logoCacheKey = "$effectiveModel:${profile?.logoVersion ?: 0}"
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(effectiveModel)
                                                .crossfade(true)
                                                .memoryCacheKey(logoCacheKey)
                                                .diskCacheKey(logoCacheKey)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .build(),
                                            contentDescription = "Logo",
                                            modifier = Modifier.fillMaxSize().padding(spacing.extraSmall),
                                            onLoading = { isLogoLoading = true },
                                            onSuccess = { isLogoLoading = false },
                                            onError = {
                                                isLogoLoading = false
                                                loadStage =
                                                    if (loadStage == 0 && canFallbackToLocal) 1 else 2
                                            }
                                        )
                                        if (isLogoLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(iconSize.medium).align(Alignment.Center),
                                                color = PrimaryGold,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                } else {
                                    Icon(Icons.Default.Storefront, null, tint = Color.LightGray, modifier = Modifier.size(KhanaBookTheme.iconSize.large))
                                }
                            } else if (logoUploadLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(iconSize.medium), color = PrimaryGold, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Storefront, null, tint = Color.LightGray, modifier = Modifier.size(KhanaBookTheme.iconSize.large))
                            }
                        }
                        OutlinedButton(
                            onClick = { logoLauncher.launch("image/*") },
                            border = BorderStroke(1.dp, PrimaryGold),
                            shape = KhanaRadii.xl,
                            enabled = !logoUploadLoading && !readOnly
                        ) {
                            Text("Change Logo", color = PrimaryGold, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Field 1: Shop Name
                    ParchmentTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Shop Name *",
                        placeholder = "Enter Shop Name",
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    // Field 2: Shop Address
                    ParchmentTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Shop Address *",
                        placeholder = "Enter Shop Address",
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    // Field 3: WhatsApp Number
                    ParchmentTextField(
                        value = whatsapp,
                        onValueChange = {
                            val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                            whatsapp = filtered
                            phoneServerError = null
                            if (filtered != (profile?.whatsappNumber ?: "")) {
                                otpSent = false
                                isOtpVerified = false
                                otpValue = ""
                                if (filtered.length == 10) {
                                    viewModel.checkUserExists(filtered, profile?.whatsappNumber ?: "")
                                }
                            } else {
                                isOtpVerified = true
                                viewModel.clearUserCheck()
                            }
                        },
                        label = "WhatsApp Number",
                        placeholder = "Enter WhatsApp Number",
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        isError = (whatsapp.isNotEmpty() && !isPhoneValid) || userExistsError != null || phoneServerError != null,
                        supportingText = phoneServerError ?: userExistsError ?: if (whatsapp.isNotEmpty() && !isPhoneValid) "Enter 10-digit number" else null,
                        trailingIcon = {
                            if (isUserChecking || isOtpLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryGold
                                )
                            } else if (numberChanged && (!otpSent || otpTimer == 0)) {
                                Button(
                                    onClick = {
                                        phoneServerError = null
                                        if (isPhoneValid && userExistsError == null) authViewModel.sendOtp(whatsapp, "update_whatsapp")
                                    },
                                    modifier = Modifier.padding(end = 4.dp).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                    shape = KhanaRadii.xl,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    enabled = isPhoneValid && !isUserChecking && !isOtpLoading && userExistsError == null && !readOnly
                                ) {
                                    Text("Get OTP", color = DarkBrown1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            } else if (numberChanged && isOtpVerified) {
                                Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = spacing.medium))
                            }
                        }
                    )

                    if (otpSent && numberChanged) {
                        ParchmentTextField(
                            value = otpValue,
                            onValueChange = {
                                val filtered = it.filter { ch -> ch.isDigit() }.take(6)
                                otpValue = filtered
                                if (filtered.length == 6) {
                                    authViewModel.confirmMobileNumberUpdate(whatsapp, filtered)
                                } else {
                                    isOtpVerified = false
                                }
                            },
                            label = "Enter 6-digit OTP",
                            placeholder = "6-digit OTP",
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                            },
                            enabled = !readOnly,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            isError = (otpValue.length == 6 && !isOtpVerified) || !otpFieldErrors["otp"].isNullOrBlank(),
                            supportingText = otpFieldErrors["otp"] ?: if (otpValue.length == 6 && !isOtpVerified) "Invalid OTP code" else null,
                            trailingIcon = {
                                if (otpTimer > 0 && !isOtpVerified) {
                                    Text(
                                        String.format("%02d:%02d", otpTimer / 60, otpTimer % 60),
                                        color = TextLight,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(end = spacing.medium)
                                    )
                                } else if (otpTimer == 0 && !isOtpVerified) {
                                    TextButton(
                                        onClick = {
                                            if (isPhoneValid && !isOtpLoading) authViewModel.sendOtp(whatsapp, "update_whatsapp")
                                        },
                                        modifier = Modifier.padding(end = 4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Resend", color = PrimaryGold, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                } else if (isOtpVerified) {
                                    Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = spacing.medium))
                                }
                            }
                        )
                    }

                    // Field 4: Email
                    ParchmentTextField(
                        value = email,
                        onValueChange = {
                            if (!isGoogleAuth) {
                                email = it
                                emailServerError = null
                                if (it.trim() != (profile?.email?.trim() ?: "")) {
                                    isEmailOtpVerified = false
                                    emailOtpSent = false
                                } else {
                                    isEmailOtpVerified = true
                                }
                            }
                        },
                        label = if (isGoogleAuth) "Email (Google account)" else "Email *",
                        placeholder = "Enter Email Address",
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        },
                        enabled = !isGoogleAuth && !readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        isError = (!isGoogleAuth && email.isNotBlank() && !isEmailValid) || !emailOtpFieldErrors["email"].isNullOrBlank() || emailServerError != null,
                        supportingText = emailServerError ?: emailOtpFieldErrors["email"] ?: if (!isGoogleAuth && email.isNotBlank() && !isEmailValid) "Enter valid email address" else null,
                        trailingIcon = {
                            if (!isGoogleAuth && isEmailOtpLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryGold
                                )
                            } else if (!isGoogleAuth && emailChanged && isEmailValid && (!emailOtpSent || emailOtpTimer == 0) && !isEmailOtpVerified) {
                                Button(
                                    onClick = {
                                        emailServerError = null
                                        authViewModel.sendEmailUpdateOtp(email.trim())
                                    },
                                    modifier = Modifier.padding(end = 4.dp).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                    shape = KhanaRadii.xl,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    enabled = !readOnly && !isEmailOtpLoading
                                ) {
                                    Text("Get OTP", color = DarkBrown1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            } else if (!isGoogleAuth && emailChanged && isEmailOtpVerified) {
                                Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = spacing.medium))
                            }
                        }
                    )

                    if (!isGoogleAuth && emailOtpSent && emailChanged) {
                        ParchmentTextField(
                            value = emailOtpValue,
                            onValueChange = {
                                val filtered = it.filter { ch -> ch.isDigit() }.take(6)
                                emailOtpValue = filtered
                                if (filtered.length == 6) {
                                    authViewModel.confirmEmailUpdate(email.trim(), filtered)
                                } else {
                                    isEmailOtpVerified = false
                                }
                            },
                            label = "Enter 6-digit Email OTP",
                            placeholder = "6-digit OTP",
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                            },
                            enabled = !readOnly,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            isError = (emailOtpValue.length == 6 && !isEmailOtpVerified) || !emailOtpFieldErrors["otp"].isNullOrBlank(),
                            supportingText = emailOtpFieldErrors["otp"] ?: if (emailOtpValue.length == 6 && !isEmailOtpVerified) "Invalid OTP code" else null,
                            trailingIcon = {
                                if (emailOtpTimer > 0 && !isEmailOtpVerified) {
                                    Text(
                                        String.format("%02d:%02d", emailOtpTimer / 60, emailOtpTimer % 60),
                                        color = TextLight,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(end = spacing.medium)
                                    )
                                } else if (emailOtpTimer == 0 && !isEmailOtpVerified) {
                                    TextButton(
                                        onClick = {
                                            if (isEmailValid && !isEmailOtpLoading) authViewModel.sendEmailUpdateOtp(email.trim())
                                        },
                                        modifier = Modifier.padding(end = 4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Resend", color = PrimaryGold, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                } else if (isEmailOtpVerified) {
                                    Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = spacing.medium))
                                }
                            }
                        )
                    }

                    // Field 5: Review Link
                    ParchmentTextField(
                        value = reviewUrl,
                        onValueChange = { reviewUrl = it },
                        label = "Review Link",
                        placeholder = "Enter Review Link",
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                    )

                    // Field 6: Invoice Footer
                    ParchmentTextField(
                        value = invoiceFooter,
                        onValueChange = { invoiceFooter = it },
                        label = "Invoice Footer",
                        placeholder = "Enter Invoice Footer",
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(20.dp))
                        },
                        enabled = !readOnly,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Card 2: Business Hours
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = KhanaRadii.lg,
                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f))
            ) {
                // Opening/Closing times stay hidden until the header is tapped, so the
                // card reads as one line (hours + rollover) until the shopkeeper wants them.
                var isBusinessHoursExpanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(KhanaRadii.md)
                            .clickable { isBusinessHoursExpanded = !isBusinessHoursExpanded }
                            .padding(vertical = spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Business Hours",
                                color = TextLight,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                // Non-breaking space keeps "12:30 AM" on one line; it otherwise
                                // wrapped between the minutes and the AM/PM suffix.
                                text = "Daily Order Number will reset at " + businessDayRolloverLabel(
                                    cutoffTime = profile?.businessDayCutoffTime,
                                    cutoffEffectiveFrom = profile?.cutoffEffectiveFrom,
                                    openingTime = openingTime,
                                    closingTime = closingTime
                                ).replace(' ', '\u00A0'),
                                color = TextGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            imageVector = if (isBusinessHoursExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isBusinessHoursExpanded) "Hide opening and closing time" else "Show opening and closing time",
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isBusinessHoursExpanded,
                        enter = expandVertically(animationSpec = tween(durationMillis = 220)) + fadeIn(animationSpec = tween(durationMillis = 220)),
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 180)) + fadeOut(animationSpec = tween(durationMillis = 180))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            KhanaTimePickerField(
                                label = "Opening Time",
                                time = openingTime,
                                onTimeSelected = { openingTime = it },
                                enabled = !readOnly,
                                modifier = Modifier.weight(1f)
                            )
                            KhanaTimePickerField(
                                label = "Closing Time",
                                time = closingTime,
                                onTimeSelected = { closingTime = it },
                                enabled = !readOnly,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Card 3: Billing Flow Mode Selector
            RestaurantPaymentFlowSelector(
                selectedMode = selectedPaymentFlowMode,
                enabled = profile != null,
                onModeSelected = { mode ->
                    if (mode == selectedPaymentFlowMode) return@RestaurantPaymentFlowSelector
                    pendingPaymentFlowMode = mode
                    if (isPinEnabled) {
                        appLockViewModel.clearPin()
                        showModePinDialog = true
                    } else {
                        selectedPaymentFlowMode = mode
                        viewModel.updateOrderPaymentFlowMode(mode)
                        pendingPaymentFlowMode = null
                    }
                }
            )

            Spacer(modifier = Modifier.height(spacing.large))

            // Full-Width Gold Pill CTA: Save Configuration
            Button(
                onClick = {
                    if (numberChanged && !isOtpVerified) {
                        toastScope.launch {
                            KhanaToast.show(context.getString(R.string.toast_verify_new_whatsapp), ToastKind.Warning)
                        }
                    } else if (!isGoogleAuth && email.isNotBlank() && !isEmailValid) {
                        toastScope.launch {
                            KhanaToast.show("Please enter a valid email address", ToastKind.Warning)
                        }
                    } else if (emailChanged && !isEmailOtpVerified) {
                        toastScope.launch {
                            KhanaToast.show(context.getString(R.string.toast_verify_new_email), ToastKind.Warning)
                        }
                    } else {
                        val updatedProfile = profile?.copy(
                            shopName = name,
                            shopAddress = address,
                            whatsappNumber = whatsapp,
                            email = email,
                            logoPath = profile?.logoPath,
                            logoUrl = profile?.logoUrl,
                            emailInvoiceConsent = consent,
                            reviewUrl = reviewUrl,
                            invoiceFooter = invoiceFooter,
                            openingTime = openingTime,
                            closingTime = closingTime,
                            orderPaymentFlowMode = selectedPaymentFlowMode.dbValue,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        updatedProfile?.let {
                            focusManager.clearFocus()
                            onSaved()
                            viewModel.saveProfileWithLogo(context, it, pendingLogoUri)
                            pendingLogoUri = null
                        }
                    }
                },
                enabled = !readOnly && !saveProfileLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGold,
                    contentColor = DarkBrown1,
                    disabledContainerColor = PrimaryGold.copy(alpha = 0.4f),
                    disabledContentColor = DarkBrown1.copy(alpha = 0.5f)
                ),
                shape = KhanaRadii.xl
            ) {
                if (saveProfileLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DarkBrown1,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                }
                Text(
                    text = if (saveProfileLoading) "Saving..." else "Save Configuration",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkBrown1
                )
            }

            Spacer(modifier = Modifier.height(spacing.huge))
        }
    }

    if (showModePinDialog) {
        AlertDialog(
            onDismissRequest = {
                showModePinDialog = false
                pendingPaymentFlowMode = null
                appLockViewModel.clearPin()
            },
            title = { Text("Confirm Billing Mode", color = PrimaryGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    Text(
                        "Enter your app PIN to switch to ${pendingPaymentFlowMode?.displayLabel.orEmpty()}.",
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    InlinePinEntry(
                        pin = enteredPin,
                        onDigit = appLockViewModel::appendDigit,
                        onDelete = appLockViewModel::deleteDigit,
                        errorMessage = pinError
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showModePinDialog = false
                        pendingPaymentFlowMode = null
                        appLockViewModel.clearPin()
                    }
                ) {
                    Text("Cancel", color = TextGold)
                }
            },
            containerColor = DarkBrown2
        )
    }
}

