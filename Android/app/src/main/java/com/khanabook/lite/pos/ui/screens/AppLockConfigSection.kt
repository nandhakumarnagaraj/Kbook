package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookLargeDialog
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel

// TODO: Update these before release
private const val SUPPORT_WHATSAPP = "919876543210"
private const val SUPPORT_EMAIL = "support@khanabook.in"

@Composable
fun AppLockConfigView(
    onBack: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val setupState by viewModel.pinSetupState.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(viewModel.isPinEnabled()) }
    val showBiometric = remember { viewModel.hasBiometric(context) }
    val showPinOptions = isEnabled && (
        setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle ||
            setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success
        )

    var showChangePassword by remember { mutableStateOf(false) }
    var showHelpSupport by remember { mutableStateOf(false) }
    var showAboutApp by remember { mutableStateOf(false) }

    LaunchedEffect(setupState) {
        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            isEnabled = viewModel.isPinEnabled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.5f),
                    modifier = Modifier.size(iconSize.medium)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text("App Lock", color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isEnabled) "PIN lock is active" else "Disabled - anyone can open the app",
                        color = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enable ->
                        if (enable) viewModel.startEnablePin()
                        else viewModel.startDisablePin()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SuccessGreen,
                        checkedTrackColor = SuccessGreen.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextGold.copy(alpha = 0.5f),
                        uncheckedTrackColor = DarkBrown1
                    )
                )
            }
        }

        if (showPinOptions) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("PIN Options", color = TextLight, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { viewModel.startChangePin() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BorderGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = PrimaryGold, modifier = Modifier.size(iconSize.xsmall))
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Change PIN", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                    }
                    if (showBiometric) {
                        Text(
                            "Biometric unlock is available on this device and will be used alongside your PIN.",
                            color = TextGold.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            Text(
                (setupState as com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success).message,
                color = SuccessGreen,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        when (val state = setupState) {
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterNew -> {
                Text(
                    "Set a new 4-digit PIN",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.ConfirmNew -> {
                Text(
                    "Confirm your PIN",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterCurrent -> {
                Text(
                    if (state.nextStep != null) "Enter current PIN to verify" else "Enter current PIN to disable",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            else -> {}
        }

        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle ||
            setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {

            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Filled.Password,
                        text = "Change Password",
                        onClick = {
                            authViewModel.clearResetStatus()
                            showChangePassword = true
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = spacing.medium),
                        color = BorderGold.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        text = "Help & Support",
                        onClick = { showHelpSupport = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = spacing.medium),
                        color = BorderGold.copy(alpha = 0.1f)
                    )
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        text = "About App",
                        onClick = { showAboutApp = true }
                    )
                }
            }
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            initialPhone = currentUser?.phoneNumber ?: currentUser?.whatsappNumber ?: "",
            authViewModel = authViewModel,
            onDismiss = {
                authViewModel.clearResetStatus()
                showChangePassword = false
            }
        )
    }

    if (showHelpSupport) {
        HelpSupportDialog(onDismiss = { showHelpSupport = false })
    }

    if (showAboutApp) {
        AboutAppDialog(onDismiss = { showAboutApp = false })
    }
}

@Composable
private fun ChangePasswordDialog(
    initialPhone: String,
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val resetStatus by authViewModel.resetPasswordStatus.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(1) }
    var phone by remember { mutableStateOf(initialPhone) }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val isLoading = resetStatus is AuthViewModel.ResetPasswordResult.Loading

    LaunchedEffect(resetStatus) {
        when (resetStatus) {
            is AuthViewModel.ResetPasswordResult.OtpSent -> {
                step = 2
                localError = null
            }
            is AuthViewModel.ResetPasswordResult.Success -> onDismiss()
            is AuthViewModel.ResetPasswordResult.Error -> {
                localError = (resetStatus as AuthViewModel.ResetPasswordResult.Error).message
            }
            else -> {}
        }
    }

    KhanaBookLargeDialog(
        title = "Change Password",
        onDismissRequest = onDismiss,
        subtitle = {
            Text(
                text = when (step) {
                    1 -> "Step 1 of 3 — Verify your phone"
                    2 -> "Step 2 of 3 — Enter OTP"
                    else -> "Step 3 of 3 — Set new password"
                },
                color = PrimaryGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    ) {
        if (step >= 2) {
            CpVerifiedBadge(icon = Icons.Default.Phone, label = phone, note = "Verified")
        }
        if (step >= 3) {
            CpVerifiedBadge(icon = Icons.Default.Lock, label = "OTP Verified", note = "Confirmed")
        }

        if (step == 1) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Registered Phone Number", color = TextGold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = PrimaryGold.copy(alpha = 0.7f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (step == 2) {
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                label = { Text("Enter OTP", color = TextGold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = {
                    localError = null
                    authViewModel.sendOtp(phone.trim(), "reset")
                },
                enabled = !isLoading
            ) {
                Text("Resend OTP", color = PrimaryGold.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (step == 3) {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password", color = TextGold) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = PrimaryGold.copy(alpha = 0.7f)
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm New Password", color = TextGold) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        localError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = { localError = null; step -= 1 },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isLoading
                ) {
                    Text("Back", color = TextGold)
                }
            }
            Button(
                onClick = {
                    localError = null
                    when (step) {
                        1 -> {
                            val trimmed = phone.trim()
                            if (trimmed.length < 10) {
                                localError = "Please enter a valid phone number."
                            } else {
                                authViewModel.sendOtp(trimmed, "reset")
                            }
                        }
                        2 -> {
                            if (otp.length < 4) {
                                localError = "Please enter the OTP sent to your phone."
                            } else {
                                step = 3
                            }
                        }
                        3 -> when {
                            newPassword.length < 6 -> localError = "Password must be at least 6 characters."
                            newPassword != confirmPassword -> localError = "Passwords do not match."
                            else -> authViewModel.resetPassword(phone.trim(), otp, newPassword)
                        }
                    }
                },
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                shape = RoundedCornerShape(10.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(iconSize.small), strokeWidth = 2.dp, color = DarkBrown1)
                } else {
                    Text(
                        when (step) { 1 -> "Send OTP"; 2 -> "Next"; else -> "Set Password" },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CpVerifiedBadge(icon: ImageVector, label: String, note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = TextLight, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(note, color = SuccessGreen, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HelpSupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing

    KhanaBookLargeDialog(
        title = "Help & Support",
        onDismissRequest = onDismiss,
        subtitle = {
            Text(
                "We're here to help you succeed",
                color = PrimaryGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    ) {
        Text(
            "Need help with KhanaBook POS? Reach out to our support team — we respond quickly.",
            color = TextLight.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val url = "https://wa.me/$SUPPORT_WHATSAPP?text=Hi%2C%20I%20need%20help%20with%20KhanaBook%20POS"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat on WhatsApp", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "KhanaBook POS Support")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Email, null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Email Support", color = PrimaryGold, fontWeight = FontWeight.Medium)
        }

        Text(
            "Mon – Sat, 10 AM – 7 PM IST",
            color = TextGold.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AboutAppDialog(onDismiss: () -> Unit) {
    val spacing = KhanaBookTheme.spacing

    KhanaBookLargeDialog(
        title = "About App",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(PrimaryGold.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "KhanaBook Lite",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                color = TextGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))

        Text(
            "A smart, offline-first POS solution built for restaurants and food businesses. Manage orders, track payments, and generate reports — all from your Android device.",
            color = TextLight.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))

        Text(
            "© 2025 KhanaBook. All rights reserved.",
            color = TextGold.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
