package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbPrimary
import com.khanabook.lite.pos.ui.theme.kbSecondary
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
import com.khanabook.lite.pos.ui.theme.KbSuccess
import com.khanabook.lite.pos.ui.theme.SwiggyOrange
import com.khanabook.lite.pos.ui.theme.ZomatoRed
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
    val context = LocalContext.current
    var currency by remember { mutableStateOf(profile?.currency ?: "INR") }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    val easebuzzEnabled = profile?.easebuzzEnabled ?: false
    val ownWebsiteEnabled = profile?.ownWebsiteEnabled ?: false
    val zomatoEnabled = profile?.zomatoEnabled ?: false
    val swiggyEnabled = profile?.swiggyEnabled ?: false

    val toastScope = rememberCoroutineScope()

    fun openWebDashboard(path: String = "") {
        val base = BuildConfig.WEB_ADMIN_URL.trimEnd('/')
        val url = if (path.isBlank()) base else "$base/$path"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

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
                "Setup is managed via the web admin dashboard.",
                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(spacing.medium))

            PlatformToggle("Easebuzz Payments", easebuzzEnabled) { openWebDashboard("payments/easebuzz") }
            PlatformToggle("Own Website", ownWebsiteEnabled) { openWebDashboard("marketplace/own-website") }
            PlatformToggle("Zomato Integration", zomatoEnabled) { openWebDashboard("marketplace/zomato") }
            PlatformToggle("Swiggy Integration", swiggyEnabled) { openWebDashboard("marketplace/swiggy") }

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
fun PaymentToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
        KhanaBookSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedTrackColor = KbSuccess
        )
    }
}

@Composable
fun PlatformToggle(label: String, enabled: Boolean, onOpenDashboard: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onOpenDashboard),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KhanaBookSwitch(
                checked = enabled,
                onCheckedChange = {},
                enabled = false,
                checkedTrackColor = KbSuccess
            )
            Spacer(modifier = Modifier.size(spacing.small))
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open dashboard",
                tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp).padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun DashboardLinkCard(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
