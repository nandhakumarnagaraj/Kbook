@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.designsystem.PrimaryButton
import com.khanabook.lite.pos.ui.designsystem.SecondaryButton
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KbBrandSaffron
import com.khanabook.lite.pos.ui.theme.KbShape
import com.khanabook.lite.pos.ui.theme.KbButtonSize
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbSecondary
import com.khanabook.lite.pos.ui.theme.kbTextPrimary
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
import com.khanabook.lite.pos.ui.theme.KbSuccess
import kotlinx.coroutines.launch

private val ZomatoBrand = Color(0xFFE23744)
private val SwiggyBrand = Color(0xFFFC8019)

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

    // ── Payment method toggles ───────────────────────────────────────────────
    var currency by remember { mutableStateOf(profile?.currency ?: "INR") }
    var upiSupported by remember { mutableStateOf(profile?.upiEnabled ?: false) }
    var upiHandle by remember { mutableStateOf(profile?.upiHandle ?: "") }
    var cashEnabled by remember { mutableStateOf(profile?.cashEnabled ?: true) }
    var posEnabled by remember { mutableStateOf(profile?.posEnabled ?: false) }
    var easebuzzEnabled by remember { mutableStateOf(profile?.easebuzzEnabled ?: false) }
    var ownWebsiteEnabled by remember { mutableStateOf(profile?.ownWebsiteEnabled ?: false) }

    // ── Zomato integration ───────────────────────────────────────────────────
    var zomatoEnabled by remember { mutableStateOf(profile?.zomatoEnabled ?: false) }
    var zomatoStoreId by remember { mutableStateOf(profile?.zomatoStoreId ?: "") }
    var zomatoApiKey by remember { mutableStateOf(profile?.zomatoApiKey ?: "") }
    var zomatoApiKeyVisible by remember { mutableStateOf(false) }

    // ── Swiggy integration ───────────────────────────────────────────────────
    var swiggyEnabled by remember { mutableStateOf(profile?.swiggyEnabled ?: false) }
    var swiggyStoreId by remember { mutableStateOf(profile?.swiggyStoreId ?: "") }
    var swiggyApiKey by remember { mutableStateOf(profile?.swiggyApiKey ?: "") }
    var swiggyApiKeyVisible by remember { mutableStateOf(false) }

    val toastScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(layout.contentPadding)
    ) {
        ConfigCard {
            // ── Currency Dropdown (restricted to INR) ────────────────────────
            var currencyMenuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = currencyMenuExpanded,
                onExpandedChange = { currencyMenuExpanded = it }
            ) {
                ParchmentTextField(
                    value = currency,
                    onValueChange = {},
                    label = "Currency *",
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.kbSecondary
                        )
                    }
                )
                ExposedDropdownMenu(
                    expanded = currencyMenuExpanded,
                    onDismissRequest = { currencyMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("INR") },
                        onClick = {
                            currency = "INR"
                            currencyMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.large))

            // ── Payment methods ──────────────────────────────────────────────
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

            // ── Online Platforms ─────────────────────────────────────────────
            Text("Online Platforms", color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.titleMedium)
            Text(
                "Configure your delivery platform integrations below.",
                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Easebuzz card ────────────────────────────────────────────────
            IntegrationCard(
                title = "Easebuzz Payments",
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
                        modifier = Modifier.weight(1f).height(KbButtonSize.HeightMedium),
                        colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                        shape = KbShape.Medium
                    ) {
                        Icon(imageVector = Icons.Outlined.Security, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.size(spacing.extraSmall))
                        Text("Open KYC", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { easebuzzEnabled = false },
                        modifier = Modifier.weight(1f).height(KbButtonSize.HeightMedium),
                        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                        shape = KbShape.Medium
                    ) {
                        Text("Reset")
                    }
                }
                // Info tip
                if (easebuzzEnabled) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    PlatformInfoChip(
                        text = "Complete KYC to start accepting online payments via Easebuzz",
                        tint = KbBrandSaffron
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Zomato card ──────────────────────────────────────────────────
            IntegrationCard(
                title = "Zomato Integration",
                subtitle = "Connect your Zomato outlet to receive orders.",
                icon = Icons.Outlined.Storefront,
                tone = ZomatoBrand
            ) {
                PaymentToggle("Enable Zomato", zomatoEnabled) {
                    zomatoEnabled = it
                    // Clear credentials when disabled
                    if (!it) { zomatoStoreId = ""; zomatoApiKey = "" }
                }

                // Animated expandable config form
                AnimatedVisibility(
                    visible = zomatoEnabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(200))
                ) {
                    Column(modifier = Modifier.padding(top = spacing.small)) {
                        ParchmentTextField(
                            value = zomatoStoreId,
                            onValueChange = { zomatoStoreId = it.trim() },
                            label = "Zomato Store ID *",
                            supportingText = "Found in Zomato Partner Portal → Outlet Details"
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        KhanaBookInputField(
                            value = zomatoApiKey,
                            onValueChange = { zomatoApiKey = it.trim() },
                            label = "Zomato API Key *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (zomatoApiKeyVisible) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { zomatoApiKeyVisible = !zomatoApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (zomatoApiKeyVisible) Icons.Default.Visibility
                                                      else Icons.Default.VisibilityOff,
                                        contentDescription = if (zomatoApiKeyVisible) "Hide key" else "Show key",
                                        tint = MaterialTheme.kbTextSecondary
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        PlatformInfoChip(
                            text = "Get Store ID & API Key from Zomato Partner Portal → API Access",
                            tint = ZomatoBrand
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Swiggy card ──────────────────────────────────────────────────
            IntegrationCard(
                title = "Swiggy Integration",
                subtitle = "Connect your Swiggy outlet to receive orders.",
                icon = Icons.Outlined.Storefront,
                tone = SwiggyBrand
            ) {
                PaymentToggle("Enable Swiggy", swiggyEnabled) {
                    swiggyEnabled = it
                    // Clear credentials when disabled
                    if (!it) { swiggyStoreId = ""; swiggyApiKey = "" }
                }

                // Animated expandable config form
                AnimatedVisibility(
                    visible = swiggyEnabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(200))
                ) {
                    Column(modifier = Modifier.padding(top = spacing.small)) {
                        ParchmentTextField(
                            value = swiggyStoreId,
                            onValueChange = { swiggyStoreId = it.trim() },
                            label = "Swiggy Store ID *",
                            supportingText = "Found in Swiggy Partner Portal → Store Settings"
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        KhanaBookInputField(
                            value = swiggyApiKey,
                            onValueChange = { swiggyApiKey = it.trim() },
                            label = "Swiggy API Key *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (swiggyApiKeyVisible) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { swiggyApiKeyVisible = !swiggyApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (swiggyApiKeyVisible) Icons.Default.Visibility
                                                      else Icons.Default.VisibilityOff,
                                        contentDescription = if (swiggyApiKeyVisible) "Hide key" else "Show key",
                                        tint = MaterialTheme.kbTextSecondary
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        PlatformInfoChip(
                            text = "Get Store ID & API Key from Swiggy Partner Portal → Integration",
                            tint = SwiggyBrand
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Own website ──────────────────────────────────────────────────
            IntegrationCard(
                title = "Own Website",
                subtitle = "Accept orders through your own website checkout.",
                icon = Icons.Outlined.LocalShipping,
                tone = MaterialTheme.kbSecondary
            ) {
                PaymentToggle("Own Website", ownWebsiteEnabled) { ownWebsiteEnabled = it }
                Spacer(modifier = Modifier.height(spacing.small))
                Button(
                    onClick = onOpenMarketplaceOrders,
                    modifier = Modifier.fillMaxWidth().height(KbButtonSize.HeightMedium),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbSecondary),
                    shape = KbShape.Medium
                ) {
                    Icon(imageVector = Icons.Outlined.Storefront, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.size(spacing.extraSmall))
                    Text("View Live Orders", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Save / Back ──────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                PrimaryButton(
                    text = "Save",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // Validate UPI
                        if (upiSupported && upiHandle.isBlank()) {
                            toastScope.launch {
                                KhanaToast.show("Enter UPI ID to generate amount QR", ToastKind.Error)
                            }
                            return@PrimaryButton
                        }
                        // Validate Zomato credentials if enabled
                        if (zomatoEnabled && (zomatoStoreId.isBlank() || zomatoApiKey.isBlank())) {
                            toastScope.launch {
                                KhanaToast.show("Enter Zomato Store ID and API Key", ToastKind.Error)
                            }
                            return@PrimaryButton
                        }
                        // Validate Swiggy credentials if enabled
                        if (swiggyEnabled && (swiggyStoreId.isBlank() || swiggyApiKey.isBlank())) {
                            toastScope.launch {
                                KhanaToast.show("Enter Swiggy Store ID and API Key", ToastKind.Error)
                            }
                            return@PrimaryButton
                        }
                        profile?.copy(
                            currency = if (currency.equals("INR", ignoreCase = true)) "INR" else "INR",
                            upiEnabled = upiSupported,
                            upiHandle = upiHandle.trim(),
                            easebuzzEnabled = easebuzzEnabled,
                            ownWebsiteEnabled = ownWebsiteEnabled,
                            zomatoEnabled = zomatoEnabled,
                            zomatoStoreId = zomatoStoreId.trim().ifBlank { null },
                            zomatoApiKey = zomatoApiKey.trim().ifBlank { null },
                            swiggyEnabled = swiggyEnabled,
                            swiggyStoreId = swiggyStoreId.trim().ifBlank { null },
                            swiggyApiKey = swiggyApiKey.trim().ifBlank { null },
                            upiQrPath = null,
                            upiQrUrl = null,
                            cashEnabled = cashEnabled,
                            posEnabled = posEnabled,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                    }
                )
                SecondaryButton(
                    text = "Back",
                    modifier = Modifier.weight(1f),
                    onClick = onBack
                )
            }
        }
    }
}

// ── Info chip ────────────────────────────────────────────────────────────────

@Composable
private fun PlatformInfoChip(text: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp).padding(top = 1.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── IntegrationCard ──────────────────────────────────────────────────────────

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
        shape = KbShape.Large,
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

// ── PaymentToggle ─────────────────────────────────────────────────────────────

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
