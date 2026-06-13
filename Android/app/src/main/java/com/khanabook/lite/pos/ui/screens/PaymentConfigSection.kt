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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KbBrandSaffron
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbSecondary
import com.khanabook.lite.pos.ui.theme.kbTextPrimary
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
import com.khanabook.lite.pos.ui.theme.KbSuccess
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PaymentConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    onOpenEasebuzzKyc: () -> Unit = {},
    onOpenMarketplaceOrders: () -> Unit = {}
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    var currency by remember { mutableStateOf(profile?.currency ?: "INR") }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    var easebuzzEnabled by remember { mutableStateOf(profile?.easebuzzEnabled ?: false) }
    var ownWebsiteEnabled by remember { mutableStateOf(profile?.ownWebsiteEnabled ?: false) }
    var zomatoEnabled by remember { mutableStateOf(profile?.zomatoEnabled ?: false) }
    var swiggyEnabled by remember { mutableStateOf(profile?.swiggyEnabled ?: false) }

    val toastScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            Text("Payment Methods", color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.titleMedium)
            PaymentToggle("Cash Payment", cashEnabled) { cashEnabled = it }
            PaymentToggle("POS Machine", posEnabled) { posEnabled = it }
            PaymentToggle("Offline UPI QR", upiSupported) { upiSupported = it }
            if (upiSupported) {
                Spacer(modifier = Modifier.height(spacing.medium))
                val isUpiValid = upiHandle.isBlank() || ValidationUtils.isValidUpiId(upiHandle)
                ParchmentTextField(
                    value = upiHandle,
                    onValueChange = { upiHandle = it.trim() },
                    label = "UPI ID *",
                    isError = upiHandle.isNotBlank() && !isUpiValid,
                    supportingText = if (upiHandle.isNotBlank() && !isUpiValid) "Format: name@provider (e.g., name@paytm)" else null
                )
            }

            Spacer(modifier = Modifier.height(spacing.large))
            Text("Online Platforms", color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything here stays inside the app. No browser hops.",
                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(spacing.medium))

            IntegrationCard(
                title = "Easebuzz Onboarding",
                subtitle = "Native KYC flow and online payment activation.",
                icon = Icons.Outlined.Security,
                tone = KbBrandSaffron
            ) {
                PaymentToggle("Enable Easebuzz", easebuzzEnabled) { easebuzzEnabled = it }
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Button(
                        onClick = onOpenEasebuzzKyc,
                        enabled = easebuzzEnabled,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Security, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.size(spacing.extraSmall))
                        Text("Open KYC", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { easebuzzEnabled = false },
                        modifier = Modifier.weight(1f).height(44.dp),
                        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text("Reset")
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            IntegrationCard(
                title = "Marketplace Integrations",
                subtitle = "Zomato, Swiggy, and own-website checkout flags.",
                icon = Icons.Outlined.LocalShipping,
                tone = MaterialTheme.kbSecondary
            ) {
                PaymentToggle("Own Website", ownWebsiteEnabled) { ownWebsiteEnabled = it }
                PaymentToggle("Zomato Integration", zomatoEnabled) { zomatoEnabled = it }
                PaymentToggle("Swiggy Integration", swiggyEnabled) { swiggyEnabled = it }
                Spacer(modifier = Modifier.height(spacing.small))
                Button(
                    onClick = onOpenMarketplaceOrders,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbSecondary),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(imageVector = Icons.Outlined.Storefront, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.size(spacing.extraSmall))
                    Text("Open Marketplace Orders", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))
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
                            easebuzzEnabled = easebuzzEnabled,
                            ownWebsiteEnabled = ownWebsiteEnabled,
                            zomatoEnabled = zomatoEnabled,
                            swiggyEnabled = swiggyEnabled,
                            upiQrPath = null,
                            upiQrUrl = null,
                            cashEnabled = cashEnabled,
                            posEnabled = posEnabled,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KbSuccess),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Save", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Back") }
            }
        }
    }
}

@Composable
fun IntegrationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: Color,
    content: @Composable () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = tone.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            content()
        }
    }
}

@Composable
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KhanaBookSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                checkedTrackColor = KbSuccess
            )
            Spacer(modifier = Modifier.size(spacing.small))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (checked) KbSuccess else MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
