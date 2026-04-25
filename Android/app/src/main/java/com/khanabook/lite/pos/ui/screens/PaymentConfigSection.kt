package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold

@Composable
fun PaymentConfigView(profile: RestaurantProfileEntity?, onSave: (RestaurantProfileEntity) -> Unit, onBack: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val isCompactWidth = layout.isCompactForm
    var currency by remember { mutableStateOf(profile?.currency ?: "INR") }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var upiMobile by remember { mutableStateOf(profile?.upiMobile ?: "") }
    var qrPath by remember { mutableStateOf(profile?.upiQrPath) }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    var zomatoEnabled by remember { mutableStateOf(profile?.zomatoEnabled ?: false) }
    var swiggyEnabled by remember { mutableStateOf(profile?.swiggyEnabled ?: false) }
    var ownWebsiteEnabled by remember { mutableStateOf(profile?.ownWebsiteEnabled ?: false) }
    var qrUpdateTrigger by remember { mutableStateOf(0L) }

    // Easebuzz gateway config — when enabled and the device is online, the UPI
    // flow uses the live gateway (real-time success/failed via webhook). When
    // disabled or offline, the manual QR + counter-confirmation flow is used.
    var easebuzzEnabled by remember { mutableStateOf(profile?.easebuzzEnabled ?: false) }
    var easebuzzMerchantKey by remember { mutableStateOf(profile?.easebuzzMerchantKey ?: "") }
    var easebuzzSalt by remember { mutableStateOf(profile?.easebuzzSalt ?: "") }
    var easebuzzEnv by remember { mutableStateOf(profile?.easebuzzEnv ?: "test") }

    val context = LocalContext.current
    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            qrPath = AppAssetStore.saveUriToAppAsset(context, it, "qr", "upi_qr.png")
            qrUpdateTrigger = System.currentTimeMillis()
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
            ParchmentTextField(value = currency, onValueChange = { currency = it }, label = "Currency *")
            Spacer(modifier = Modifier.height(spacing.large))
            Text("Enable Payment Methods", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            PaymentToggle("Cash Payment", cashEnabled) { cashEnabled = it }
            PaymentToggle("POS Machine", posEnabled) { posEnabled = it }
            PaymentToggle("Zomato Orders", zomatoEnabled) { zomatoEnabled = it }
            PaymentToggle("Swiggy Orders", swiggyEnabled) { swiggyEnabled = it }
            PaymentToggle("Own Website", ownWebsiteEnabled) { ownWebsiteEnabled = it }

            Spacer(modifier = Modifier.height(spacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = upiSupported, onCheckedChange = { upiSupported = it }, colors = CheckboxDefaults.colors(checkedColor = SuccessGreen))
                Text("Enable UPI QR Payments", color = TextGold, style = MaterialTheme.typography.bodyMedium)
            }
            if (upiSupported) {
                Spacer(modifier = Modifier.height(spacing.medium))

                val qrContent = @Composable {
                    Box(
                        modifier = Modifier
                            .size(KhanaBookTheme.iconSize.hero)
                            .background(Color.White)
                            .border(1.dp, Color.LightGray)
                            .padding(spacing.extraSmall),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!qrPath.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(AppAssetStore.resolveAssetPath(qrPath))
                                    .setParameter("refresh", qrUpdateTrigger)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.QrCode, null, tint = Color.LightGray, modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge))
                        }
                    }
                    OutlinedButton(
                        onClick = { qrLauncher.launch("image/*") },
                        border = BorderStroke(1.dp, PrimaryGold),
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Upload QR", color = PrimaryGold) }
                }

                if (isCompactWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) { qrContent() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.medium)) { qrContent() }
                }

                Spacer(modifier = Modifier.height(spacing.medium))
                ParchmentTextField(value = upiHandle, onValueChange = { upiHandle = it }, label = "UPI Handle")
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(value = upiMobile, onValueChange = { upiMobile = it }, label = "UPI Mobile Number")
            }

            Spacer(modifier = Modifier.height(spacing.large))
            Text(
                "Easebuzz (Online UPI Gateway)",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "When enabled and online, UPI payments are verified by Easebuzz in real time. " +
                    "When offline or disabled, the manual QR + counter-confirmation flow is used.",
                color = TextGold,
                style = MaterialTheme.typography.bodySmall
            )
            PaymentToggle("Enable Easebuzz", easebuzzEnabled) { easebuzzEnabled = it }
            if (easebuzzEnabled) {
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(
                    value = easebuzzMerchantKey,
                    onValueChange = { easebuzzMerchantKey = it.trim() },
                    label = "Merchant Key"
                )
                Spacer(modifier = Modifier.height(spacing.small))
                ParchmentTextField(
                    value = easebuzzSalt,
                    onValueChange = { easebuzzSalt = it.trim() },
                    label = "Salt"
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Environment",
                        color = TextGold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    EnvChip("test", easebuzzEnv == "test") { easebuzzEnv = "test" }
                    Spacer(modifier = Modifier.size(spacing.small))
                    EnvChip("prod", easebuzzEnv == "prod") { easebuzzEnv = "prod" }
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = {
                        profile?.copy(
                            currency = currency,
                            upiEnabled = upiSupported,
                            upiHandle = upiHandle,
                            upiMobile = upiMobile,
                            upiQrPath = qrPath,
                            cashEnabled = cashEnabled,
                            posEnabled = posEnabled,
                            zomatoEnabled = zomatoEnabled,
                            swiggyEnabled = swiggyEnabled,
                            ownWebsiteEnabled = ownWebsiteEnabled,
                            easebuzzEnabled = easebuzzEnabled,
                            easebuzzMerchantKey = easebuzzMerchantKey.ifBlank { null },
                            easebuzzSalt = easebuzzSalt.ifBlank { null },
                            easebuzzEnv = easebuzzEnv,
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
private fun EnvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, if (selected) SuccessGreen else PrimaryGold),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            label,
            color = if (selected) SuccessGreen else PrimaryGold,
            style = MaterialTheme.typography.bodyMedium
        )
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = SuccessGreen))
    }
}
