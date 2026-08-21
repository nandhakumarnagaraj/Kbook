@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun ForgotPasswordDialog(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) }
    var resendTimer by remember { mutableIntStateOf(0) }
    val isPhoneValid = ValidationUtils.isValidPhone(phone)
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val resetStatus by viewModel.resetPasswordStatus.collectAsStateWithLifecycle()
    val resetFieldErrors by viewModel.resetPasswordFieldErrors.collectAsStateWithLifecycle()
    val isResetLoading = resetStatus is AuthViewModel.ResetPasswordResult.Loading

    fun fieldError(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        resetFieldErrors[key]?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(resetStatus) {
        when (resetStatus) {
            is AuthViewModel.ResetPasswordResult.OtpSent -> {
                step = 2
                resendTimer = 60
                while (resendTimer > 0) { delay(1000); resendTimer-- }
            }
            is AuthViewModel.ResetPasswordResult.Success -> {
                onSuccess(context.getString(R.string.toast_password_reset))
                onDismiss()
            }
            else -> {}
        }
    }

    // Compact green badge for a completed/verified step
    @Composable
    fun VerifiedBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, note: String) {
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
                Spacer(Modifier.width(spacing.small))
                Text(label, color = TextLight, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall))
                Spacer(Modifier.width(spacing.extraSmall))
                Text(note, color = SuccessGreen, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    KhanaBookLargeDialog(
        title = "Forgot Password",
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        subtitle = {
            Text(
                text = when (step) {
                    1 -> "Step 1 of 3 | Verify phone number"
                    2 -> "Step 2 of 3 | Verify OTP"
                    else -> "Step 3 of 3 | Create new password"
                },
                color = TextGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        },
        actions = {
            if (step == 1) {
                Button(
                    onClick = { if (isPhoneValid && !isResetLoading) viewModel.sendOtp(phone, "reset") },
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                    shape = KhanaRadii.lg,
                    enabled = isPhoneValid && !isResetLoading
                ) {
                    if (isResetLoading)
                        CircularProgressIndicator(modifier = Modifier.size(iconSize.small), color = DarkBrown1, strokeWidth = 2.dp)
                    else
                        Text("Send OTP", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            } else {
                // Back + primary action side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isResetLoading) {
                                if (step == 3) { newPassword = ""; confirmPassword = "" }
                                if (step == 2) { otp = "" }
                                step -= 1
                                viewModel.clearResetStatus()
                            }
                        },
                        modifier = Modifier.weight(1f).height(KhanaBookTheme.spacing.buttonHeight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGold),
                        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.45f)),
                        shape = KhanaRadii.lg
                    ) { Text("Back", style = MaterialTheme.typography.labelLarge) }

                    Button(
                        onClick = {
                            when (step) {
                                2 -> if (otp.length == 6) step = 3
                                3 -> if (newPassword.isNotBlank() && newPassword == confirmPassword)
                                    viewModel.resetPassword(phone, otp, newPassword)
                            }
                        },
                        modifier = Modifier.weight(2f).height(KhanaBookTheme.spacing.buttonHeight),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                        shape = KhanaRadii.lg,
                        enabled = when (step) {
                            2 -> otp.length == 6
                            3 -> newPassword.isNotBlank() && newPassword == confirmPassword
                            else -> false
                        }
                    ) {
                        Text(
                            text = if (step == 2) "Verify OTP" else "Reset Password",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (step == 2) {
                    TextButton(
                        onClick = { if (resendTimer == 0 && !isResetLoading) viewModel.sendOtp(phone, "reset") },
                        enabled = resendTimer == 0 && !isResetLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (resendTimer > 0) "Resend OTP in ${resendTimer}s" else "Resend OTP",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (resendTimer > 0 || isResetLoading) TextMuted else PrimaryGold
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            ForgotPasswordStepRow(currentStep = step)

            Text(
                text = when (step) {
                    1 -> "Enter your registered WhatsApp number to receive an OTP."
                    2 -> "Enter the 6-digit OTP sent to $phone via WhatsApp."
                    else -> "Create a new strong password for your account."
                },
                color = TextLight.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            // Phone — active input on step 1, verified badge on steps 2-3
            if (step == 1) {
                KhanaBookInputField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() }.take(10) },
                    label = "WhatsApp Number",
                    placeholder = "Enter your 10-digit number",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = PrimaryGold) },
                    isError = phone.isNotEmpty() && !isPhoneValid || fieldError("phoneNumber", "loginId", "whatsappNumber") != null,
                    supportingText = {
                        val err = fieldError("phoneNumber", "loginId", "whatsappNumber")
                        when {
                            err != null -> Text(err, color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                            phone.isNotEmpty() && !isPhoneValid -> Text("Enter 10-digit number", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            } else {
                VerifiedBadge(Icons.Default.Phone, phone, "Sent")
            }

            // OTP — active input on step 2, verified badge on step 3
            if (step == 2) {
                KhanaBookInputField(
                    value = otp,
                    onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                    label = "Enter OTP",
                    placeholder = "6-digit code",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    isError = fieldError("otp") != null,
                    supportingText = {
                        val err = fieldError("otp")
                        when {
                            err != null -> Text(err, color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                            otp.isNotEmpty() && otp.length < 6 -> Text(
                                "${6 - otp.length} more digit${if (6 - otp.length == 1) "" else "s"} required",
                                color = TextGold.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            } else if (step == 3) {
                VerifiedBadge(Icons.Default.Lock, "OTP Verified", "Confirmed")
            }

            // Password fields — step 3 only
            if (step == 3) {
                KhanaBookInputField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password",
                    placeholder = "Min 8 chars with symbols",
                    modifier = Modifier.fillMaxWidth(),
                    isError = fieldError("password") != null,
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = PrimaryGold) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.clickable { showNewPassword = !showNewPassword }
                        )
                    },
                    supportingText = {
                        fieldError("password")?.let { Text(it, color = ErrorPink, style = MaterialTheme.typography.labelSmall) }
                    }
                )
                val passwordsMatch = confirmPassword.isEmpty() || newPassword == confirmPassword
                KhanaBookInputField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm Password",
                    placeholder = "Repeat new password",
                    modifier = Modifier.fillMaxWidth(),
                    isError = !passwordsMatch,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = PrimaryGold) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.clickable { showConfirmPassword = !showConfirmPassword }
                        )
                    },
                    supportingText = {
                        if (!passwordsMatch) Text("Passwords do not match", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                    }
                )
            }

            val resetErrorMessage = (resetStatus as? AuthViewModel.ResetPasswordResult.Error)?.message
            if (resetErrorMessage != null) {
                Text(resetErrorMessage, color = ErrorPink, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ForgotPasswordStepRow(currentStep: Int) {
    val steps = listOf("Phone", "OTP", "Password")
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        steps.forEachIndexed { index, label ->
            val stepNumber = index + 1
            val isActive = stepNumber == currentStep
            val isDone = stepNumber < currentStep
            Surface(
                modifier = Modifier.weight(1f),
                color = when {
                    isDone -> SuccessGreen.copy(alpha = 0.16f)
                    isActive -> PrimaryGold.copy(alpha = 0.18f)
                    else -> DarkBrown2
                },
                shape = KhanaRadii.lg,
                border = BorderStroke(
                    1.dp,
                    when {
                        isDone -> SuccessGreen.copy(alpha = 0.55f)
                        isActive -> PrimaryGold.copy(alpha = 0.75f)
                        else -> BorderGold.copy(alpha = 0.3f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isDone) "Done" else "Step $stepNumber",
                        color = if (isDone) SuccessGreen else TextGold.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = label,
                        color = if (isActive || isDone) TextLight else TextGold.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
internal fun loginTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                focusedBorderColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                focusedLabelColor = PrimaryGold,
                unfocusedLabelColor = TextMuted,
                errorBorderColor = ErrorPink,
                errorLabelColor = ErrorPink
        )
