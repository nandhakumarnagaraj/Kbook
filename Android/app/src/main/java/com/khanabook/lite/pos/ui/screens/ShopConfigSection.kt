package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.R
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ShopConfigView(
    profile: RestaurantProfileEntity?,
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
    var logoUpdateTrigger by remember { mutableLongStateOf(0L) }

    val profileLogoUrl by viewModel.profile.collectAsStateWithLifecycle()
    val logoUrl = profileLogoUrl?.logoUrl
    val logoPath = profileLogoUrl?.logoPath

    val saveProfileLoading by viewModel.saveProfileLoading.collectAsState()
    val saveProfileError by viewModel.saveProfileError.collectAsState()
    val saveProfileSuccess by viewModel.saveProfileSuccess.collectAsState()
    val logoUploadLoading by viewModel.logoUploadLoading.collectAsState()
    val logoUploadError by viewModel.logoUploadError.collectAsState()
    val isUserChecking by viewModel.isUserChecking.collectAsStateWithLifecycle()
    val userExistsError by viewModel.userExistsError.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isGoogleAuth = currentUser?.authProvider.equals("GOOGLE", ignoreCase = true)

    val isDirty = remember(name, address, whatsapp, email, consent, reviewUrl, invoiceFooter, profile) {
        name != (profile?.shopName ?: "") ||
            address != (profile?.shopAddress ?: "") ||
            whatsapp != (profile?.whatsappNumber ?: "") ||
            email != (profile?.email ?: "") ||
            consent != (profile?.emailInvoiceConsent ?: false) ||
            reviewUrl != (profile?.reviewUrl ?: "") ||
            invoiceFooter != (profile?.invoiceFooter ?: "")
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

    LaunchedEffect(saveProfileSuccess) {
        if (saveProfileSuccess) {
            toastScope.launch { KhanaToast.show(context.getString(R.string.toast_profile_saved), ToastKind.Success) }
            viewModel.clearSaveProfileState()
            authViewModel.clearOtpStatus()
            onBack()
        }
    }

    LaunchedEffect(profile) {
        profile?.let {
            name = it.shopName ?: ""
            address = it.shopAddress ?: ""
            whatsapp = it.whatsappNumber ?: ""
            email = it.email ?: ""
            consent = it.emailInvoiceConsent
            reviewUrl = it.reviewUrl ?: ""
            invoiceFooter = it.invoiceFooter ?: ""
        }
    }

    LaunchedEffect(whatsapp) {
        if (whatsapp.length < 10) {
            viewModel.clearUserCheck()
        }
    }

    var otpValue by remember { mutableStateOf("") }
    val otpStatus by authViewModel.otpVerificationStatus.collectAsState()
    val otpFieldErrors by authViewModel.otpFieldErrors.collectAsState()

    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableIntStateOf(120) }
    var isOtpVerified by remember { mutableStateOf(false) }

    LaunchedEffect(otpStatus) {
        when (otpStatus) {
            is AuthViewModel.OtpVerificationResult.OtpSent -> {
                otpSent = true
                isOtpVerified = false
                otpTimer = 120
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_otp_sent), ToastKind.Info) }
            }
            is AuthViewModel.OtpVerificationResult.Success -> {
                isOtpVerified = true
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_verified), ToastKind.Success) }
                authViewModel.clearOtpStatus()
            }
            is AuthViewModel.OtpVerificationResult.Error -> {
                val errorMsg = (otpStatus as? AuthViewModel.OtpVerificationResult.Error)?.message.orEmpty()
                isOtpVerified = false
                toastScope.launch {
                    KhanaToast.show(
                        UserMessageSanitizer.sanitizeBackendMessage(errorMsg, "Couldn't verify OTP. Please try again."),
                        ToastKind.Error,
                    )
                }
                authViewModel.clearOtpStatus()
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

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.uploadLogo(context, it) {
                logoUpdateTrigger = System.currentTimeMillis()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(layout.contentPadding)
    ) {
        ConfigCard {
            Text("Shop Profile", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(spacing.medium))

            val logoContent = @Composable {
                Box(
                    modifier = Modifier
                        .size(KhanaBookTheme.iconSize.hero)
                        .background(Color.White)
                        .border(1.dp, Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    val logoModel = logoUrl?.takeIf { it.isNotBlank() }
                        ?: AppAssetStore.resolveAssetPath(logoPath)
                    if (!logoModel.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(logoModel)
                                .crossfade(true)
                                .memoryCacheKey("$logoModel:$logoUpdateTrigger")
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize().padding(spacing.extraSmall)
                        )
                    } else if (logoUploadLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(iconSize.medium), color = PrimaryGold, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Storefront, null, tint = Color.LightGray, modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge))
                    }
                }
                OutlinedButton(
                    onClick = { logoLauncher.launch("image/*") },
                    border = BorderStroke(1.dp, PrimaryGold),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !logoUploadLoading
                ) { Text(if (logoUploadLoading) "Uploading..." else "Change Logo", color = PrimaryGold) }
            }

            if (isCompactWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) { logoContent() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.medium)) { logoContent() }
            }

            Spacer(modifier = Modifier.height(spacing.large))
            ParchmentTextField(value = name, onValueChange = { name = it }, label = "Shop Name")
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = address, onValueChange = { address = it }, label = "Shop Address")
            val isPhoneValid = ValidationUtils.isValidPhone(whatsapp)
            val numberChanged = whatsapp != (profile?.whatsappNumber ?: "")

            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
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
                label = "Whatsapp Number",
                isError = (whatsapp.isNotEmpty() && !isPhoneValid) || userExistsError != null,
                supportingText = if (userExistsError != null) userExistsError else if (whatsapp.isNotEmpty() && !isPhoneValid) "Enter 10-digit number" else null,
                trailingIcon = {
                    if (isUserChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryGold
                        )
                    } else if (numberChanged && (!otpSent || otpTimer == 0)) {
                        Button(
                            onClick = {
                                if (isPhoneValid && userExistsError == null) authViewModel.sendOtp(whatsapp, "update_whatsapp")
                            },
                            modifier = Modifier.padding(end = spacing.extraSmall).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            enabled = isPhoneValid && !isUserChecking && userExistsError == null
                        ) {
                            Text("Send OTP", color = DarkBrown1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            if (otpSent && numberChanged) {
                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(
                    value = otpValue,
                    onValueChange = {
                        if (it.length <= 6) {
                            otpValue = it
                            if (it.length == 6) {
                                authViewModel.confirmMobileNumberUpdate(whatsapp, it)
                            } else {
                                isOtpVerified = false
                            }
                        }
                    },
                    label = "Enter 6-digit OTP",
                    isError = (otpValue.length == 6 && !isOtpVerified) || !otpFieldErrors["otp"].isNullOrBlank(),
                    supportingText = otpFieldErrors["otp"] ?: if (otpValue.length == 6 && !isOtpVerified) "Invalid OTP code" else null,
                    trailingIcon = {
                        if (otpTimer > 0 && !isOtpVerified) {
                            Text(
                                String.format("%02d:%02d", otpTimer / 60, otpTimer % 60),
                                color = com.khanabook.lite.pos.ui.theme.TextLight,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(end = spacing.medium)
                            )
                        } else if (isOtpVerified) {
                            Icon(Icons.Default.Lock, contentDescription = "Verified", tint = SuccessGreen, modifier = Modifier.padding(end = spacing.medium))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(
                value = email,
                onValueChange = { if (!isGoogleAuth) email = it },
                label = if (isGoogleAuth) "Email (Google account)" else "Email",
                enabled = !isGoogleAuth
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = reviewUrl, onValueChange = { reviewUrl = it }, label = "Review Link")
            Spacer(modifier = Modifier.height(spacing.medium))
            ParchmentTextField(value = invoiceFooter, onValueChange = { invoiceFooter = it }, label = "Invoice Footer")
            Spacer(modifier = Modifier.height(spacing.large))

            val saveButtonScale by animateFloatAsState(
                targetValue = if (!saveProfileLoading) 1f else 0.97f,
                animationSpec = tween(durationMillis = 250),
                label = "save_scale"
            )
            val saveButtonAlpha by animateFloatAsState(
                targetValue = if (!saveProfileLoading) 1f else 0.45f,
                animationSpec = tween(durationMillis = 250),
                label = "save_alpha"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Button(
                    onClick = {
                        if (numberChanged && !isOtpVerified) {
                            toastScope.launch {
                                KhanaToast.show(context.getString(R.string.toast_verify_new_whatsapp), ToastKind.Warning)
                            }
                        } else {
                            val updatedProfile = profile?.copy(
                                shopName = name,
                                shopAddress = address,
                                whatsappNumber = whatsapp,
                                email = email,
                                logoPath = profileLogoUrl?.logoPath,
                                logoUrl = profileLogoUrl?.logoUrl,
                                emailInvoiceConsent = consent,
                                reviewUrl = reviewUrl,
                                invoiceFooter = invoiceFooter,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )
                            updatedProfile?.let { viewModel.saveProfile(it) }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = saveButtonScale
                            scaleY = saveButtonScale
                            alpha = saveButtonAlpha
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !saveProfileLoading
                ) {
                    if (saveProfileLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(iconSize.medium), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedButton(
                    onClick = { onBack() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
