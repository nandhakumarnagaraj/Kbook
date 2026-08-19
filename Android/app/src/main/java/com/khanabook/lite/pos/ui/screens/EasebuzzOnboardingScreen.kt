@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.designsystem.KhanaBookLoadingOverlay
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.EasebuzzOnboardingViewModel
import com.khanabook.lite.pos.ui.viewmodel.OnboardingEvent
import com.khanabook.lite.pos.ui.viewmodel.OnboardingStep
import com.khanabook.lite.pos.ui.viewmodel.OnboardingUiState

@Composable
fun EasebuzzOnboardingScreen(
    onBack: () -> Unit,
    viewModel: EasebuzzOnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.Toast -> {
                    KhanaToast.show(
                        event.message,
                        if (event.isError) ToastKind.Error else ToastKind.Success
                    )
                }
                is OnboardingEvent.OnboardingComplete -> onBack()
            }
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Online Payments Setup",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.goBack()) onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
                .imePadding()
        ) {
        when (val state = uiState) {
            is OnboardingUiState.Loading -> {
                KhanaBookLoadingOverlay(visible = true, message = "Checking status...")
            }
            is OnboardingUiState.NotStarted -> {
                NotStartedContent(
                    onStart = { viewModel.startOnboarding() },
                    spacing = spacing
                )
            }
            is OnboardingUiState.InProgress -> {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                            (fadeOut() + slideOutHorizontally { -it / 3 })
                    },
                    label = "onboarding_step"
                ) { step ->
                    when (step) {
                        OnboardingStep.BusinessDetails -> BusinessDetailsStep(
                            viewModel = viewModel,
                            isSubmitting = isSubmitting,
                            spacing = spacing
                        )
                        OnboardingStep.BankDetails -> BankDetailsStep(
                            viewModel = viewModel,
                            isSubmitting = isSubmitting,
                            spacing = spacing
                        )
                        OnboardingStep.OtpVerification -> OtpVerificationStep(
                            viewModel = viewModel,
                            isSubmitting = isSubmitting,
                            spacing = spacing
                        )
                        OnboardingStep.KycStatus -> KycStatusStep(
                            viewModel = viewModel,
                            status = null,
                            spacing = spacing
                        )
                    }
                }
            }
            is OnboardingUiState.AwaitingKyc -> {
                KycStatusStep(
                    viewModel = viewModel,
                    status = state.status,
                    spacing = spacing
                )
            }
            is OnboardingUiState.Active -> {
                ActiveContent(status = state.status, spacing = spacing, onBack = onBack)
            }
            is OnboardingUiState.Rejected -> {
                RejectedContent(
                    viewModel = viewModel,
                    status = state.status,
                    isSubmitting = isSubmitting,
                    spacing = spacing
                )
            }
            is OnboardingUiState.Error -> {
                ErrorContent(message = state.message, onRetry = { viewModel.loadStatus() }, spacing = spacing)
            }
        }

        if (isSubmitting && uiState is OnboardingUiState.InProgress) {
            KhanaBookLoadingOverlay(visible = true, message = "Submitting...")
        }
        }
    }
}

@Composable
private fun NotStartedContent(onStart: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = PrimaryGold,
            modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
        )
        Spacer(Modifier.height(spacing.large))
        Text(
            "Accept Online Payments",
            style = MaterialTheme.typography.headlineSmall,
            color = TextLight,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Register your business with Easebuzz to accept UPI, cards, and netbanking payments directly from customers.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            "You'll need:\n• Business PAN card\n• Bank account details (IFSC)\n• Phone number for OTP\n• KYC documents (uploaded later)",
            style = MaterialTheme.typography.bodySmall,
            color = TextGold.copy(alpha = 0.7f),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(spacing.extraLarge))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Registration", color = DarkBrown1)
        }
    }
}

@Composable
private fun BusinessDetailsStep(
    viewModel: EasebuzzOnboardingViewModel,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    val focusManager = LocalFocusManager.current
    var businessName by remember { mutableStateOf(viewModel.businessName) }
    var legalEntityName by remember { mutableStateOf(viewModel.legalEntityName) }
    var businessType by remember { mutableStateOf(viewModel.businessType) }
    var pan by remember { mutableStateOf(viewModel.pan) }
    var gst by remember { mutableStateOf(viewModel.gst) }
    var businessAddress by remember { mutableStateOf(viewModel.businessAddress) }
    var state by remember { mutableStateOf(viewModel.state) }
    var contactEmail by remember { mutableStateOf(viewModel.contactEmail) }
    var contactPhone by remember { mutableStateOf(viewModel.contactPhone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(spacing.medium)
    ) {
        StepIndicator(current = 1, total = 3, spacing = spacing)
        Spacer(Modifier.height(spacing.medium))
        Text("Business Details", style = MaterialTheme.typography.titleMedium, color = PrimaryGold)
        Spacer(Modifier.height(spacing.medium))

        OnboardingField("Business Name *", businessName, { businessName = it }, focusManager, ImeAction.Next)
        OnboardingField("Legal Entity Name", legalEntityName, { legalEntityName = it }, focusManager, ImeAction.Next)
        BusinessTypeDropdown(businessType) { businessType = it }
        OnboardingField("PAN *", pan, { pan = it.uppercase().take(10) }, focusManager, ImeAction.Next,
            keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Characters)
        OnboardingField("GST Number", gst, { gst = it.uppercase().take(15) }, focusManager, ImeAction.Next,
            capitalization = KeyboardCapitalization.Characters)
        OnboardingField("Business Address *", businessAddress, { businessAddress = it }, focusManager, ImeAction.Next)
        OnboardingField("State *", state, { state = it }, focusManager, ImeAction.Next)
        OnboardingField("Email *", contactEmail, { contactEmail = it.trim() }, focusManager, ImeAction.Next,
            keyboardType = KeyboardType.Email)
        OnboardingField("Phone *", contactPhone, { contactPhone = it.filter { c -> c.isDigit() }.take(10) },
            focusManager, ImeAction.Done, keyboardType = KeyboardType.Phone)

        Spacer(Modifier.height(spacing.large))
        val isPanValid = EasebuzzOnboardingViewModel.isValidPan(pan)
        val isValid = businessName.isNotBlank() && isPanValid &&
            businessAddress.isNotBlank() && state.isNotBlank() &&
            contactEmail.contains("@") && contactPhone.length == 10

        if (pan.length == 10 && !isPanValid) {
            Text(
                "Invalid PAN format (expected: AAAAA9999A)",
                color = DangerRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
            )
        }

        Button(
            onClick = {
                viewModel.businessName = businessName
                viewModel.legalEntityName = legalEntityName
                viewModel.businessType = businessType
                viewModel.pan = pan
                viewModel.gst = gst
                viewModel.businessAddress = businessAddress
                viewModel.state = state
                viewModel.contactEmail = contactEmail
                viewModel.contactPhone = contactPhone
                viewModel.submitBusinessDetails()
            },
            enabled = isValid && !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next → Bank Details", color = DarkBrown1)
        }
        Spacer(Modifier.height(spacing.large))
    }
}

@Composable
private fun BankDetailsStep(
    viewModel: EasebuzzOnboardingViewModel,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    val focusManager = LocalFocusManager.current
    var bankAccountNo by remember { mutableStateOf(viewModel.bankAccountNo) }
    var confirmAccountNo by remember { mutableStateOf(viewModel.bankAccountNo) }
    var ifsc by remember { mutableStateOf(viewModel.ifsc) }
    var bankName by remember { mutableStateOf(viewModel.bankName) }
    var branchName by remember { mutableStateOf(viewModel.branchName) }
    var beneficiaryName by remember { mutableStateOf(viewModel.beneficiaryName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(spacing.medium)
    ) {
        StepIndicator(current = 2, total = 3, spacing = spacing)
        Spacer(Modifier.height(spacing.medium))
        Text("Bank Details", style = MaterialTheme.typography.titleMedium, color = PrimaryGold)
        Spacer(Modifier.height(spacing.small))
        Text(
            "Payments will be settled directly to this account.",
            style = MaterialTheme.typography.bodySmall,
            color = TextGold.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(spacing.medium))

        OnboardingField("Account Number *", bankAccountNo,
            { bankAccountNo = it.filter { c -> c.isDigit() } }, focusManager, ImeAction.Next,
            keyboardType = KeyboardType.Number)
        OnboardingField("Confirm Account Number *", confirmAccountNo,
            { confirmAccountNo = it.filter { c -> c.isDigit() } }, focusManager, ImeAction.Next,
            keyboardType = KeyboardType.Number)
        if (confirmAccountNo.isNotBlank() && bankAccountNo != confirmAccountNo) {
            Text(
                "Account numbers do not match",
                color = DangerRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
            )
        }
        OnboardingField("IFSC Code *", ifsc, { 
            ifsc = it.uppercase().take(11)
            if (ifsc.length == 11) viewModel.lookupIfsc(ifsc)
        }, focusManager, ImeAction.Next,
            capitalization = KeyboardCapitalization.Characters)
        
        val ifscInfo by viewModel.ifscBankInfo.collectAsStateWithLifecycle()
        if (ifsc.length == 11 && !EasebuzzOnboardingViewModel.isValidIfsc(ifsc)) {
            Text(
                "Invalid IFSC format (expected: ABCD0123456)",
                color = DangerRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
            )
        } else if (ifscInfo != null) {
            Text(
                "✓ ${ifscInfo!!.bankName}, ${ifscInfo!!.branchName}",
                color = SuccessGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = spacing.small, bottom = spacing.small)
            )
            // Auto-fill from IFSC lookup
            LaunchedEffect(ifscInfo) {
                ifscInfo?.let {
                    if (bankName.isBlank()) bankName = it.bankName
                    if (branchName.isBlank()) branchName = it.branchName
                }
            }
        }
        OnboardingField("Bank Name *", bankName, { bankName = it }, focusManager, ImeAction.Next)
        OnboardingField("Branch Name *", branchName, { branchName = it }, focusManager, ImeAction.Next)
        OnboardingField("Beneficiary Name *", beneficiaryName, { beneficiaryName = it }, focusManager, ImeAction.Done)

        Spacer(Modifier.height(spacing.large))
        val isIfscValid = EasebuzzOnboardingViewModel.isValidIfsc(ifsc)
        val accountsMatch = bankAccountNo == confirmAccountNo
        val isValid = bankAccountNo.length >= 8 && accountsMatch && isIfscValid &&
            bankName.isNotBlank() && branchName.isNotBlank() && beneficiaryName.isNotBlank()

        Button(
            onClick = {
                viewModel.bankAccountNo = bankAccountNo
                viewModel.ifsc = ifsc
                viewModel.bankName = bankName
                viewModel.branchName = branchName
                viewModel.beneficiaryName = beneficiaryName
                viewModel.submitBankDetailsAndOnboard()
            },
            enabled = isValid && !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSubmitting) "Submitting..." else "Submit & Continue", color = DarkBrown1)
        }
        Spacer(Modifier.height(spacing.large))
    }
}

@Composable
private fun OtpVerificationStep(
    viewModel: EasebuzzOnboardingViewModel,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    var otp by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableStateOf(0) }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            kotlinx.coroutines.delay(1000)
            resendCooldown--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(current = 3, total = 3, spacing = spacing)
        Spacer(Modifier.height(spacing.extraLarge))
        Text("OTP Verification", style = MaterialTheme.typography.titleMedium, color = PrimaryGold)
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Enter the OTP sent to your registered phone number to verify your identity.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.extraLarge))

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("Enter 6-digit OTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGold,
                unfocusedBorderColor = BorderGold,
                focusedLabelColor = PrimaryGold,
                cursorColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
            ),
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(Modifier.height(spacing.large))
        Button(
            onClick = { viewModel.verifyOtp(otp) },
            enabled = otp.length == 6 && !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text(if (isSubmitting) "Verifying..." else "Verify OTP", color = DarkBrown1)
        }

        Spacer(Modifier.height(spacing.medium))
        TextButton(
            onClick = {
                viewModel.resendOtp()
                resendCooldown = 30
            },
            enabled = resendCooldown == 0
        ) {
            Text(
                if (resendCooldown > 0) "Resend OTP (${resendCooldown}s)" else "Resend OTP",
                color = if (resendCooldown > 0) TextGold.copy(alpha = 0.4f) else TextGold
            )
        }
    }
}

@Composable
private fun KycStatusStep(
    viewModel: EasebuzzOnboardingViewModel,
    status: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingStatusResponse?,
    spacing: Spacing
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.large))
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = WarningYellow,
            modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
        )
        Spacer(Modifier.height(spacing.medium))
        Text("KYC Verification Pending", style = MaterialTheme.typography.titleMedium, color = TextLight)
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Your registration has been submitted to Easebuzz for verification. " +
                "This usually takes 24-48 hours.\n\n" +
                "You'll receive a notification once your KYC is approved and online payments are activated.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )

        if (status != null) {
            Spacer(Modifier.height(spacing.large))
            StatusCard("Status", status.status, spacing)
            if (!status.kycStatus.isNullOrBlank()) {
                StatusCard("KYC", status.kycStatus, spacing)
            }
            if (!status.subMerchantId.isNullOrBlank()) {
                StatusCard("Merchant ID", status.subMerchantId, spacing)
            }
        }

        Spacer(Modifier.height(spacing.extraLarge))
        OutlinedButton(
            onClick = { viewModel.loadStatus() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Status")
        }
    }
}

@Composable
private fun ActiveContent(
    status: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingStatusResponse,
    spacing: Spacing,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(KhanaBookTheme.iconSize.heroCircle)
        )
        Spacer(Modifier.height(spacing.large))
        Text("Online Payments Active! 🎉", style = MaterialTheme.typography.headlineSmall, color = TextLight)
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Your business is verified and ready to accept online payments via UPI, cards, and netbanking.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        if (!status.subMerchantId.isNullOrBlank()) {
            Spacer(Modifier.height(spacing.medium))
            StatusCard("Merchant ID", status.subMerchantId, spacing)
        }
        Spacer(Modifier.height(spacing.extraLarge))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done", color = DarkBrown1)
        }
    }
}

@Composable
private fun RejectedContent(
    viewModel: EasebuzzOnboardingViewModel,
    status: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingStatusResponse,
    isSubmitting: Boolean,
    spacing: Spacing
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.extraLarge))
        Text("⚠️", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(spacing.medium))
        Text("KYC Rejected", style = MaterialTheme.typography.titleMedium, color = DangerRed)
        Spacer(Modifier.height(spacing.medium))
        Text(
            "Your KYC verification was not approved. Please review your details and resubmit with correct information.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.large))
        Button(
            onClick = { viewModel.startOnboarding() },
            enabled = !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
            shape = KhanaRadii.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Edit & Resubmit", color = DarkBrown1)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = DangerRed)
        Spacer(Modifier.height(spacing.small))
        Text(message, style = MaterialTheme.typography.bodySmall, color = TextGold)
        Spacer(Modifier.height(spacing.large))
        OutlinedButton(onClick = onRetry) { Text("Retry", color = PrimaryGold) }
    }
}

// ─── Shared Components ──────────────────────────────────────────────────────

@Composable
private fun StepIndicator(current: Int, total: Int, spacing: Spacing) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { index ->
            val isActive = index < current
            val isCurrent = index == current - 1
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .background(
                        color = when {
                            isCurrent -> PrimaryGold
                            isActive -> SuccessGreen
                            else -> BorderGold.copy(alpha = 0.3f)
                        },
                        shape = KhanaRadii.pill
                    )
            )
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String, spacing: Spacing) {
    Surface(
        color = DarkBrown2,
        shape = KhanaRadii.card,
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
    ) {
        Row(
            modifier = Modifier.padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextGold, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(spacing.medium))
            Text(
                value,
                color = TextLight,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun OnboardingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGold,
            unfocusedBorderColor = BorderGold,
            focusedLabelColor = PrimaryGold,
            unfocusedLabelColor = TextGold.copy(alpha = 0.6f),
            cursorColor = PrimaryGold,
            focusedTextColor = TextLight,
            unfocusedTextColor = TextLight
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusinessTypeDropdown(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "SOLE_PROPRIETORSHIP" to "Sole Proprietorship",
        "PARTNERSHIP" to "Partnership",
        "PRIVATE_LIMITED" to "Private Limited",
        "LLP" to "LLP",
        "PUBLIC_LIMITED" to "Public Limited",
        "TRUST" to "Trust/Society"
    )
    var expanded by remember { mutableStateOf(false) }
    val displayName = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Business Type *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGold,
                unfocusedBorderColor = BorderGold,
                focusedLabelColor = PrimaryGold,
                unfocusedLabelColor = TextGold.copy(alpha = 0.6f),
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DarkBrown2
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = TextLight) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
