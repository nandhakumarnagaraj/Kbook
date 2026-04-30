package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PaymentConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    var currency by remember { mutableStateOf(profile?.currency ?: "INR") }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var upiMobile by remember { mutableStateOf(profile?.upiMobile ?: "") }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    var zomatoEnabled by remember { mutableStateOf(profile?.zomatoEnabled ?: false) }
    var swiggyEnabled by remember { mutableStateOf(profile?.swiggyEnabled ?: false) }
    var ownWebsiteEnabled by remember { mutableStateOf(profile?.ownWebsiteEnabled ?: false) }

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
            ParchmentTextField(
                value = currency,
                onValueChange = {},
                label = "Currency *",
                enabled = false
            )
            Spacer(modifier = Modifier.height(spacing.large))
            Text("Payment Methods", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            PaymentToggle("Cash Payment", cashEnabled) { cashEnabled = it }
            PaymentToggle("POS Machine", posEnabled) { posEnabled = it }
            PaymentToggle("Zomato Orders", zomatoEnabled) { zomatoEnabled = it }
            PaymentToggle("Swiggy Orders", swiggyEnabled) { swiggyEnabled = it }
            PaymentToggle("Own Website", ownWebsiteEnabled) { ownWebsiteEnabled = it }
            PaymentToggle("Offline UPI QR", upiSupported) { upiSupported = it }
            if (upiSupported) {
                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(
                    value = upiHandle,
                    onValueChange = { upiHandle = it.trim() },
                    label = "UPI ID *"
                )
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(
                    value = upiMobile,
                    onValueChange = { upiMobile = it.filter(Char::isDigit).take(10) },
                    label = "UPI Mobile"
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    "UPI ID is enough. The app generates each bill's amount QR offline.",
                    color = TextGold.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = {
                        if (upiSupported && upiHandle.isBlank()) {
                            toastScope.launch {
                                KhanaToast.show("Enter UPI ID to generate amount QR", ToastKind.Error)
                            }
                            return@Button
                        }
                        profile?.copy(
                            currency = currency,
                            upiEnabled = upiSupported,
                            upiHandle = upiHandle.trim(),
                            upiMobile = upiMobile,
                            upiQrPath = null,
                            upiQrUrl = null,
                            cashEnabled = cashEnabled,
                            posEnabled = posEnabled,
                            zomatoEnabled = zomatoEnabled,
                            swiggyEnabled = swiggyEnabled,
                            ownWebsiteEnabled = ownWebsiteEnabled,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(28.dp)
                ) { Text("Save", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = BorderStroke(1.dp, TextGold),
                    shape = RoundedCornerShape(28.dp)
                ) { Text("Back") }
            }
        }
    }
}

@Composable
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
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
