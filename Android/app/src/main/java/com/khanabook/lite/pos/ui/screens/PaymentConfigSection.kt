package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaButtonRow
import com.khanabook.lite.pos.ui.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.ui.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.DarkBrownSheet
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.asImageBitmap
import com.khanabook.lite.pos.domain.manager.QrCodeManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

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
    readOnly: Boolean = false
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
    var showTestQrDialog by remember { mutableStateOf(false) }
    val feedbackPrefs = com.khanabook.lite.pos.ui.feedback.rememberMenuFeedbackPreferences()
    val feedbackSettings by com.khanabook.lite.pos.ui.feedback.rememberMenuFeedbackSettings(feedbackPrefs)
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
                com.khanabook.lite.pos.ui.screens.shopconfig.ReadOnlyConfigNotice(
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
            PaymentToggle(
                "Voice Soundbox (Audio Alert)",
                feedbackSettings.voiceAnnouncementEnabled,
                onCheckedChange = { feedbackPrefs.setVoiceAnnouncementEnabled(it) },
                enabled = !readOnly
            )
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
            PaymentToggle("Easebuzz Online", easebuzzEnabled, onCheckedChange = { easebuzzEnabled = it }, enabled = !readOnly)
            if (easebuzzEnabled) {
                Spacer(modifier = Modifier.height(spacing.small))
                androidx.compose.material3.OutlinedButton(
                    onClick = onNavigateToOnboarding,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Setup Online Payments →", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                androidx.compose.material3.OutlinedButton(
                    onClick = { onSectionSelected("merchant_agreement") },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Merchant Agreement", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                androidx.compose.material3.OutlinedButton(
                    onClick = { onSectionSelected("compliance_documents") },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Compliance Documents", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
                }
            }

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
