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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentConfigView(
    profile: RestaurantProfileEntity?,
    saveProfileLoading: Boolean = false,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {}
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
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = it }
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
            PaymentToggle("Cash Payment", cashEnabled) { cashEnabled = it }
            PaymentToggle("POS Machine", posEnabled) { posEnabled = it }
            PaymentToggle("Offline UPI QR", upiSupported) { upiSupported = it }
            if (upiSupported) {
                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(
                    value = upiHandle,
                    onValueChange = { upiHandle = it.trim() },
                    label = "UPI ID *"
                )
            }
            PaymentToggle("Easebuzz Online", easebuzzEnabled) { easebuzzEnabled = it }
            if (easebuzzEnabled) {
                Spacer(modifier = Modifier.height(spacing.small))
                androidx.compose.material3.OutlinedButton(
                    onClick = onNavigateToOnboarding,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Setup Online Payments →", color = PrimaryGold, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))
            ConfigActionButtons(
                onSave = {
                        if (upiSupported && upiHandle.isBlank()) {
                            toastScope.launch {
                                KhanaToast.show("Enter UPI ID to generate amount QR", ToastKind.Error)
                            }
                            return@ConfigActionButtons
                        }
                        profile?.copy(
                            currency = currency,
                            upiEnabled = upiSupported,
                            upiHandle = upiHandle.trim(),
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
                isSaving = saveProfileLoading
            )
        }
    }
}

@Composable
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextGold, style = MaterialTheme.typography.bodyMedium)
        KhanaBookSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedTrackColor = SuccessGreen
        )
    }
}
