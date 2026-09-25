package com.khanabook.lite.pos.feature.payments.ui
import com.khanabook.lite.pos.feature.settings.ui.ConfigActionButtons
import com.khanabook.lite.pos.feature.settings.ui.ConfigCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.core.components.ParchmentTextField
import com.khanabook.lite.pos.core.designsystem.KhanaButtonRow
import com.khanabook.lite.pos.core.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.core.theme.DarkBrownSheet
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackPreferences
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.asImageBitmap
import com.khanabook.lite.pos.feature.payments.domain.QrCodeManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.feature.onboarding.viewmodel.AgreementStatus
import com.khanabook.lite.pos.feature.onboarding.viewmodel.AgreementUiState
import com.khanabook.lite.pos.feature.payments.viewmodel.EasebuzzOnboardingViewModel
import com.khanabook.lite.pos.feature.onboarding.viewmodel.MerchantAgreementViewModel
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingUiState
import com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.TextLight

private val UPI_VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentConfigView(
    profile: RestaurantProfileEntity?,
    saveProfileLoading: Boolean = false,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    onSectionSelected: (String) -> Unit = {},
    readOnly: Boolean = false,
    easebuzzVm: EasebuzzOnboardingViewModel = hiltViewModel()
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    var currency by remember { mutableStateOf("INR") }
    var currencyExpanded by remember { mutableStateOf(false) }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    var easebuzzEnabled by remember { mutableStateOf(profile?.easebuzzEnabled ?: false) }
    LaunchedEffect(profile?.easebuzzEnabled) {
        easebuzzEnabled = profile?.easebuzzEnabled ?: false
    }
    val paymentReadiness by easebuzzVm.paymentReadiness.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { easebuzzVm.loadPaymentReadiness() }
    var showTestQrDialog by remember { mutableStateOf(false) }
    val feedbackPrefs = com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackPreferences()
    val feedbackSettings by com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackSettings(feedbackPrefs)
    val toastScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(layout.contentPadding)
    ) {
        ConfigCard {
            if (readOnly) {
                com.khanabook.lite.pos.feature.settings.ui.ReadOnlyConfigNotice(
                    "Read-only: only the restaurant owner can edit payment settings."
                )
            }
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { if (!readOnly) currencyExpanded = it }
            ) {
                ParchmentTextField(
                    value = currency,
                    onValueChange = {},
                    label = "Currency *",
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false },
                    containerColor = DarkBrownSheet
                ) {
                    DropdownMenuItem(
                        text = { Text("INR", color = TextGold) },
                        onClick = {
                            currency = "INR"
                            currencyExpanded = false
                        },
                        colors = MenuDefaults.itemColors(textColor = TextGold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.large))
            Text("Payment Methods", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            PaymentToggle("Cash Payment", cashEnabled, onCheckedChange = { cashEnabled = it }, enabled = !readOnly)
            PaymentToggle("POS Machine", posEnabled, onCheckedChange = { posEnabled = it }, enabled = !readOnly)
            PaymentToggle("Offline UPI QR", upiSupported, onCheckedChange = { upiSupported = it }, enabled = !readOnly)
            if (upiSupported) {
                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(
                    value = upiHandle,
                    onValueChange = { upiHandle = it.trim().lowercase() },
                    label = "UPI ID *",
                    enabled = !readOnly
                )
                if (upiHandle.isNotBlank()) {
                    val isValid = UPI_VPA_REGEX.matches(upiHandle)
                    if (isValid) {
                        Spacer(modifier = Modifier.height(spacing.extraSmall))
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showTestQrDialog = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test UPI QR Code ↗", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "Format: yourname@bank (e.g. cafe@okhdfcbank)",
                            color = PrimaryGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            PaymentToggle(
                "Voice Soundbox (Audio Alert)",
                feedbackSettings.voiceAnnouncementEnabled,
                onCheckedChange = { feedbackPrefs.setVoiceAnnouncementEnabled(it) },
                enabled = !readOnly
            )
            PaymentToggle("Easebuzz Online", easebuzzEnabled, onCheckedChange = { easebuzzEnabled = it }, enabled = !readOnly)
            val readinessMessage = when (val state = paymentReadiness) {
                PaymentReadinessUiState.Loading -> "Checking online payment setup with the server…"
                PaymentReadinessUiState.Unavailable -> "Cannot verify online payment setup. Reconnect and open this screen again."
                is PaymentReadinessUiState.Ready -> when {
                    state.readiness.agreementRequired -> "Payment agreement needs the owner's signature before payment links can be created."
                    !state.readiness.subMerchantActive -> "Easebuzz onboarding and KYC must be active before payment links can be created."
                    state.readiness.easebuzzEnabled && !easebuzzEnabled -> "Save to turn Easebuzz Online off. New payment links remain possible until the server confirms the change."
                    !state.readiness.easebuzzEnabled && easebuzzEnabled -> "Save to turn Easebuzz Online on. The server still blocks new payment links."
                    !state.readiness.easebuzzEnabled -> "Easebuzz Online is off. New payment links are blocked until the owner turns it on."
                    !state.readiness.paymentLinkReady -> "The server has not confirmed payment-link readiness."
                    else -> "Payment link setup is ready. Khanabook commission: 0%; Easebuzz processing fees may apply."
                }
            }
            Text(
                text = readinessMessage,
                color = if ((paymentReadiness as? PaymentReadinessUiState.Ready)?.readiness?.let {
                        it.paymentLinkReady && it.easebuzzEnabled == easebuzzEnabled
                    } == true)
                    SuccessGreen else TextGold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = spacing.extraSmall)
            )
            Spacer(modifier = Modifier.height(spacing.small))
            EasebuzzOnboardingHub(
                onNavigateToOnboarding = onNavigateToOnboarding,
                onOpenAgreement = { onSectionSelected("merchant_agreement") },
                onOpenComplianceDocs = { onSectionSelected("compliance_documents") },
                readOnly = readOnly,
                easebuzzVm = easebuzzVm
            )

            if (showTestQrDialog && upiHandle.isNotBlank()) {
                val qrBitmap = remember(upiHandle, profile?.shopName) {
                    QrCodeManager.generateUpiQr(upiHandle, profile?.shopName ?: "KhanaBook Merchant", 1.0, 512)
                }
                AlertDialog(
                    onDismissRequest = { showTestQrDialog = false },
                    containerColor = DarkBrownSheet,
                    title = {
                        Text("Interactive UPI Verification Test", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                    },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Scan this test QR with Google Pay, PhonePe, or Paytm to confirm your registered bank account name.",
                                color = TextGold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Test UPI QR",
                                    modifier = Modifier.size(180.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(spacing.small))
                            Text(
                                "UPI ID: $upiHandle\nTest Amount: ₹1.00",
                                color = TextGold.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showTestQrDialog = false }) {
                            Text("Done", color = PrimaryGold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))
            ConfigActionButtons(
                onSave = {
                    if (upiSupported) {
                        if (upiHandle.isBlank()) {
                            toastScope.launch {
                                KhanaToast.show("Enter UPI ID to generate amount QR", ToastKind.Error)
                            }
                            return@ConfigActionButtons
                        }
                        if (!UPI_VPA_REGEX.matches(upiHandle.trim())) {
                            toastScope.launch {
                                KhanaToast.show("Invalid UPI ID format. Format: yourname@bank", ToastKind.Error)
                            }
                            return@ConfigActionButtons
                        }
                    }
                    profile?.copy(
                        currency = currency,
                        upiEnabled = upiSupported,
                        upiHandle = upiHandle.trim().lowercase(),
                        upiMobile = null,
                        upiQrPath = null,
                        upiQrUrl = null,
                        cashEnabled = cashEnabled,
                        posEnabled = posEnabled,
                        easebuzzEnabled = easebuzzEnabled,

                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                    )?.let { onSave(it) }
                },
                onBack = onBack,
                isSaving = saveProfileLoading,
                saveEnabled = !readOnly
            )
        }
    }
}

@Composable
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextGold, style = MaterialTheme.typography.bodyMedium)
        KhanaBookSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedTrackColor = SuccessGreen,
            enabled = enabled
        )
    }
}

@Composable
fun EasebuzzOnboardingHub(
    onNavigateToOnboarding: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenComplianceDocs: () -> Unit,
    readOnly: Boolean = false,
    easebuzzVm: EasebuzzOnboardingViewModel = hiltViewModel(),
    agreementVm: MerchantAgreementViewModel = hiltViewModel()
) {
    val onboardingUiState by easebuzzVm.uiState.collectAsStateWithLifecycle()
    val agreementUiState by agreementVm.uiState.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(Unit) {
        easebuzzVm.loadStatus()
        agreementVm.load()
    }

    val ebStatus: EasebuzzOnboardingStatusResponse? = when (val s = onboardingUiState) {
        is OnboardingUiState.Active -> s.status
        is OnboardingUiState.AwaitingKyc -> s.status
        is OnboardingUiState.Rejected -> s.status
        else -> null
    }

    val agreementStatus: AgreementStatus? = when (val s = agreementUiState) {
        is AgreementUiState.Ready -> s.status
        else -> null
    }

    val isRegistered = ebStatus?.hasSubMerchant == true || ebStatus?.isActive == true

    if (onboardingUiState is OnboardingUiState.Loading || onboardingUiState is OnboardingUiState.Error) {
        Text(
            text = if (onboardingUiState is OnboardingUiState.Loading)
                "Checking Easebuzz account status…"
            else
                "Easebuzz account status is unavailable. Reconnect and open this screen again.",
            color = TextGold,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = spacing.small)
        )
        return
    }

    if (!isRegistered) {
        // ── State 1: Unregistered (Ultra-clean 1-step call to action) ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.small),
            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
            shape = KhanaRadii.card,
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Accept Online Payments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGold
                    )
                }

                Text(
                    text = "Link your bank account & PAN to start accepting customer payments via Dynamic UPI QR & Payment Gateway in 2 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGold.copy(alpha = 0.85f)
                )

                Button(
                    onClick = onNavigateToOnboarding,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = KhanaRadii.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Setup Online Payments →",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkBrown1,
                        maxLines = 1
                    )
                }
            }
        }
        return
    }

    // ── State 2: Registered & Active ──
    val kycStatusStr = ebStatus?.kycStatus?.uppercase() ?: ""
    val isKycActive = kycStatusStr == "ACTIVE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.small),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
        shape = KhanaRadii.card,
        border = BorderStroke(
            1.dp,
            if (ebStatus?.isActive == true) SuccessGreen.copy(alpha = 0.5f) else BorderGold.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // Active Header with Sub-Merchant ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (ebStatus?.isActive == true) "Easebuzz Account Active" else "Registration Submitted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    )
                }
                if (!ebStatus?.subMerchantId.isNullOrBlank()) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "ID: ${ebStatus?.subMerchantId}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = if (agreementStatus?.hasCurrentAgreement == true && ebStatus?.isActive == true)
                    "Your Easebuzz account and payment agreement are ready for online payments."
                else
                    "Complete Easebuzz activation and sign the current payment agreement before creating payment links.",
                style = MaterialTheme.typography.bodySmall,
                color = TextGold.copy(alpha = 0.85f)
            )

            // Bank Payout Verification prompt (only shown if KYC is not yet active)
            if (!isKycActive) {
                Surface(
                    color = DarkBrown1,
                    shape = KhanaRadii.card,
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = PrimaryGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Daily Bank Payout Verification",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryGold
                            )
                        }

                        Text(
                            text = when {
                                kycStatusStr == "CPV_PENDING" ->
                                    "Complete the quick agentless contact verification to unlock automatic daily 6:00 AM bank payouts."
                                kycStatusStr == "REJECTED" ->
                                    "A submitted document was rejected. Please upload a fresh copy."
                                ebStatus?.businessProof1Present == true ->
                                    "Address proof submitted. Payout verification is under review by Easebuzz."
                                else ->
                                    "Upload 1 business address proof (Electricity bill, GST, or Rent deed) to unlock automated daily 6:00 AM settlements to your bank account."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLight.copy(alpha = 0.85f)
                        )

                        Button(
                            onClick = onOpenComplianceDocs,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                            shape = KhanaRadii.button,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when {
                                    kycStatusStr == "CPV_PENDING" -> "Complete Verification →"
                                    kycStatusStr == "REJECTED" -> "Re-upload Proof →"
                                    ebStatus?.businessProof1Present == true -> "View Uploaded Proofs"
                                    else -> "Upload Payout Proof →"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkBrown1
                            )
                        }
                    }
                }
            }

            // Quick footer actions: Account Details & Merchant Agreement
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onNavigateToOnboarding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
                    shape = KhanaRadii.button
                ) {
                    Text("Account Details ↗", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
                }

                TextButton(onClick = onOpenAgreement) {
                    Text(
                        text = if (agreementStatus?.hasCurrentAgreement == true) "✓ Agreement Signed" else "Payment Agreement ↗",
                        color = if (agreementStatus?.hasCurrentAgreement == true) SuccessGreen else TextGold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
