@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.applock

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel

@Composable
fun ChangePasswordView(
    onBack: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val initialPhone = currentUser?.phoneNumber ?: currentUser?.whatsappNumber ?: ""
    val resetStatus by authViewModel.resetPasswordStatus.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(1) }
    var phone by remember { mutableStateOf(initialPhone) }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val isLoading = resetStatus is AuthViewModel.ResetPasswordResult.Loading

    LaunchedEffect(Unit) {
        authViewModel.clearResetStatus()
    }

    LaunchedEffect(resetStatus) {
        when (resetStatus) {
            is AuthViewModel.ResetPasswordResult.OtpSent -> {
                step = 2
                localError = null
            }
            is AuthViewModel.ResetPasswordResult.Success -> {
                KhanaToast.show("Password changed successfully", ToastKind.Success)
                authViewModel.clearResetStatus()
                onBack()
            }
            is AuthViewModel.ResetPasswordResult.Error -> {
                localError = (resetStatus as AuthViewModel.ResetPasswordResult.Error).message
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            CpStepIndicator(currentStep = step, totalSteps = 3)

            Text(
                text = when (step) {
                    1 -> "Verify your phone"
                    2 -> "Enter the OTP"
                    else -> "Set a new password"
                },
                color = TextLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

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
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBrown1.copy(alpha = 0.5f))
                .padding(horizontal = spacing.large, vertical = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = {
                        localError = null
                        if (step == 3) { newPassword = ""; confirmPassword = "" }
                        if (step == 2) { otp = "" }
                        step -= 1
                    },
                    modifier = Modifier.weight(1f).height(KhanaBookTheme.spacing.buttonHeightLarge),
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
                    shape = KhanaRadii.pill,
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
                modifier = Modifier.weight(2f).height(KhanaBookTheme.spacing.buttonHeightLarge),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                shape = KhanaRadii.pill,
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
                isCompleted -> SuccessGreen
                isActive -> PrimaryGold
                else -> BorderGold.copy(alpha = 0.3f)
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
            .background(SuccessGreen.copy(alpha = 0.08f), KhanaRadii.md)
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
            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall))
            Spacer(modifier = Modifier.width(4.dp))
            Text(note, color = SuccessGreen, style = MaterialTheme.typography.labelSmall)
        }
    }
}
