package com.khanabook.lite.pos.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.DarkMode
import com.khanabook.lite.pos.ui.theme.globalIsDark
import com.khanabook.lite.pos.ui.theme.kbBgCard
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import java.time.Year

// TODO: Update these before release
private const val SUPPORT_WHATSAPP = "919471676935"
private const val SUPPORT_EMAIL = "kbook@pcts.tech"

@Composable
fun SettingsListView(
    onSelectItem: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    var isSoundBoxEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
                .getBoolean("is_sound_box_enabled", true)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
    ) {
        SettingsGroupLabel("SECURITY")
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsItem(
            icon = Icons.Outlined.Lock,
            text = "App Lock",
            subtitle = "PIN & biometric protection",
            onClick = { onSelectItem("app_lock") }
        )
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsItem(
            icon = Icons.Outlined.Password,
            text = "Change Password",
            subtitle = "Update your login password",
            onClick = { onSelectItem("change_password") }
        )

        Spacer(modifier = Modifier.height(spacing.medium))
        SettingsGroupLabel("PREFERENCES")
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsItem(
            icon = Icons.Outlined.TextIncrease,
            text = "Display",
            subtitle = "Font size & layout density",
            onClick = { onSelectItem("ui_scale") }
        )
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsToggleItem(
            icon = Icons.Outlined.DarkMode,
            text = "Dark Mode",
            subtitle = "Switch to dark interface",
            checked = globalIsDark,
            onCheckedChange = { isDark ->
                globalIsDark = isDark
                val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_dark_theme", isDark).apply()
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    if (isDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        )
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsToggleItem(
            icon = Icons.Default.Notifications,
            text = "Payment Sound Box",
            subtitle = "Announce received payment amounts",
            checked = isSoundBoxEnabled,
            onCheckedChange = { enabled ->
                isSoundBoxEnabled = enabled
                context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_sound_box_enabled", enabled)
                    .apply()
            }
        )

        Spacer(modifier = Modifier.height(spacing.medium))
        SettingsGroupLabel("ABOUT")
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsItem(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            text = "Help & Support",
            subtitle = "FAQs & contact support",
            onClick = { onSelectItem("help_support") }
        )
        Spacer(modifier = Modifier.height(spacing.extraSmall))
        SettingsItem(
            icon = Icons.Outlined.Info,
            text = "About App",
            subtitle = "Version ${BuildConfig.VERSION_NAME} · Licenses",
            onClick = { onSelectItem("about_app") }
        )
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    val spacing = KhanaBookTheme.spacing
    Text(
        text = text,
        color = MaterialTheme.kbSecondary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
    )
}

@Composable
fun AppLockView(
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val setupState by viewModel.pinSetupState.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(viewModel.isPinEnabled()) }
    val showBiometric = remember { viewModel.hasBiometric(context) }
    val showPinOptions = isEnabled && (
        setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle ||
            setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success
        )

    LaunchedEffect(setupState) {
        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            isEnabled = viewModel.isPinEnabled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.smallMedium)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            if (isEnabled) KbSuccess.copy(alpha = 0.15f) else MaterialTheme.kbOutlineSubtle.copy(alpha = 0.1f),
                            CircleShape
                        )
                        .border(
                            2.dp,
                            if (isEnabled) KbSuccess.copy(alpha = 0.4f) else MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (isEnabled) KbSuccess else MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    "App Lock",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isEnabled) "PIN lock is active" else "Disabled — anyone can open the app",
                    color = if (isEnabled) KbSuccess else MaterialTheme.kbTextSecondary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                KhanaBookSwitch(
                    checked = isEnabled,
                    onCheckedChange = { enable ->
                        if (enable) viewModel.startEnablePin()
                        else viewModel.startDisablePin()
                    },
                    checkedTrackColor = KbSuccess
                )
            }
        }

        if (showPinOptions) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("PIN Options", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { viewModel.startChangePin() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.kbSecondary, modifier = Modifier.size(iconSize.xsmall))
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Change PIN", color = MaterialTheme.kbTertiary, style = MaterialTheme.typography.labelLarge)
                    }
                    if (showBiometric) {
                        Text(
                            "Biometric unlock is available on this device and will be used alongside your PIN.",
                            color = MaterialTheme.kbTextSecondary.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            Text(
                (setupState as com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success).message,
                color = KbSuccess,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        when (val state = setupState) {
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterNew -> {
                Text(
                    "Set a new 4-digit PIN",
                    color = MaterialTheme.kbTextPrimary,
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
                    Text("Cancel", color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.ConfirmNew -> {
                Text(
                    "Confirm your PIN",
                    color = MaterialTheme.kbTextPrimary,
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
                    Text("Cancel", color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterCurrent -> {
                Text(
                    if (state.nextStep != null) "Enter current PIN to verify" else "Enter current PIN to disable",
                    color = MaterialTheme.kbTextPrimary,
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
                    Text("Cancel", color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f))
                }
            }
            else -> {}
        }
    }
}

@Composable
fun ChangePasswordView(
    onBack: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val changeStatus by authViewModel.changePasswordStatus.collectAsStateWithLifecycle()
    val fieldErrors by authViewModel.changePasswordFieldErrors.collectAsStateWithLifecycle()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    var localError by remember { mutableStateOf<String?>(null) }
    val isLoading = changeStatus is AuthViewModel.ChangePasswordResult.Loading

    LaunchedEffect(Unit) {
        authViewModel.clearChangePasswordStatus()
    }

    LaunchedEffect(changeStatus) {
        when (changeStatus) {
            is AuthViewModel.ChangePasswordResult.Success -> {
                authViewModel.clearChangePasswordStatus()
                onBack()
            }
            is AuthViewModel.ChangePasswordResult.Error -> {
                localError = (changeStatus as AuthViewModel.ChangePasswordResult.Error).message
            }
            else -> {}
        }
    }

    // Validation checks
    val isMinLengthMet = newPassword.length >= 6
    val hasDigit = newPassword.any { it.isDigit() }
    val doPasswordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
    val isFormValid = currentPassword.isNotBlank() && isMinLengthMet && hasDigit && doPasswordsMatch

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium, vertical = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            
            // Current Password Section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "CURRENT PASSWORD",
                    color = MaterialTheme.kbTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    placeholder = { Text("Enter current password", color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f)) },
                    visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                            Icon(
                                imageVector = if (showCurrentPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.kbSecondary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.kbPrimary,
                        unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.kbTextPrimary,
                        unfocusedTextColor = MaterialTheme.kbTextPrimary,
                        focusedContainerColor = MaterialTheme.kbBgCard,
                        unfocusedContainerColor = MaterialTheme.kbBgCard
                    ),
                    isError = fieldErrors.containsKey("currentPassword"),
                    supportingText = fieldErrors["currentPassword"]?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // New Password Section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "NEW PASSWORD",
                    color = MaterialTheme.kbTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = { Text("Enter new password", color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f)) },
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.kbSecondary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.kbPrimary,
                        unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.kbTextPrimary,
                        unfocusedTextColor = MaterialTheme.kbTextPrimary,
                        focusedContainerColor = MaterialTheme.kbBgCard,
                        unfocusedContainerColor = MaterialTheme.kbBgCard
                    ),
                    isError = fieldErrors.containsKey("newPassword"),
                    supportingText = fieldErrors["newPassword"]?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Confirm Password Section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "CONFIRM NEW PASSWORD",
                    color = MaterialTheme.kbTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text("Confirm new password", color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f)) },
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.kbSecondary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.kbPrimary,
                        unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                        focusedTextColor = MaterialTheme.kbTextPrimary,
                        unfocusedTextColor = MaterialTheme.kbTextPrimary,
                        focusedContainerColor = MaterialTheme.kbBgCard,
                        unfocusedContainerColor = MaterialTheme.kbBgCard
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Password Requirements Card
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PASSWORD REQUIREMENTS",
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    RequirementRow(
                        checked = isMinLengthMet,
                        text = "At least 6 characters long"
                    )
                    RequirementRow(
                        checked = hasDigit,
                        text = "Contains at least one number"
                    )
                    RequirementRow(
                        checked = doPasswordsMatch,
                        text = "Passwords match"
                    )
                }
            }

            localError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Action Button Row at Bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium)
        ) {
            Button(
                onClick = {
                    if (isFormValid) {
                        localError = null
                        authViewModel.changePassword(currentPassword.trim(), newPassword.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFormValid) KbBrandSaffron else MaterialTheme.kbOutlineSubtle,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.kbOutlineSubtle,
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = isFormValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize.small),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Update Password",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(checked: Boolean, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (checked) KbSuccess else MaterialTheme.kbOutlineSubtle,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = if (checked) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextSecondary.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CpStepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..totalSteps) {
            val isCompleted = i < currentStep
            val isActive = i == currentStep
            val color = when {
                isCompleted -> KbSuccess
                isActive -> MaterialTheme.kbPrimary
                else -> MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun CpVerifiedBadge(icon: ImageVector, label: String, note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KbSuccess.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = KbSuccess, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = KbSuccess, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(note, color = KbSuccess, style = MaterialTheme.typography.labelSmall)
        }
    }
}

