@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.designsystem.KhanaBookLoadingOverlay
import com.khanabook.lite.pos.ui.designsystem.LoadingType
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
        onLoginSuccess: () -> Unit,
        onSignUpClick: () -> Unit = {},
        viewModel: AuthViewModel = hiltViewModel()
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var isGoogleLogin by remember { mutableStateOf(false) }

    val loginStatus by viewModel.loginStatus.collectAsState()
    val isLoading = loginStatus is AuthViewModel.LoginResult.Loading
    val isLoginIdValid = ValidationUtils.isValidPhone(loginId) || ValidationUtils.isValidEmail(loginId)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(loginStatus) {
        when (val s = loginStatus) {
            is AuthViewModel.LoginResult.Loading -> {}
            is AuthViewModel.LoginResult.Success -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                android.widget.Toast.makeText(
                                context,
                                "Welcome back!",
                                android.widget.Toast.LENGTH_SHORT
                        )
                        .show()
                onLoginSuccess()
            }
            is AuthViewModel.LoginResult.Error -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                android.widget.Toast.makeText(context, s.message, android.widget.Toast.LENGTH_LONG)
                        .show()
            }
            else -> { isGoogleLogin = false }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = spacing.large, vertical = spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Image(
                    painter = painterResource(id = R.drawable.khanabook_logo),
                    contentDescription = "KhanaBook Lite logo",
                    modifier = Modifier.size(120.dp).padding(bottom = spacing.medium),
                    contentScale = ContentScale.Fit
            )

            Text(
                    text = "Smart Billing for Restaurants",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = spacing.extraLarge)
            )

            
            OutlinedTextField(
                    value = loginId,
                    onValueChange = {
                        val updatedLoginId = it.trim()
                        loginId = updatedLoginId
                        if (ValidationUtils.isValidPhone(updatedLoginId) || ValidationUtils.isValidEmail(updatedLoginId)) {
                            runCatching { passwordFocusRequester.requestFocus() }
                        }
                    },
                    label = { Text("Login ID") },
                    placeholder = { Text("Phone number or email", color = TextGold.copy(alpha = 0.7f)) },
                    leadingIcon = {
                        Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Login",
                                tint = PrimaryGold
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = DarkBrown2,
                        focusedContainerColor = DarkBrown2,
                        unfocusedLabelColor = TextGold.copy(alpha = 0.7f),
                        focusedLabelColor = PrimaryGold,
                        focusedBorderColor = PrimaryGold,
                        unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                        cursorColor = PrimaryGold,
                        errorContainerColor = DarkBrown2,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextLight),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { runCatching { passwordFocusRequester.requestFocus() } }
                    ),
                    isError = (loginId.isNotEmpty() && !isLoginIdValid) || (loginId.isBlank() && loginStatus is AuthViewModel.LoginResult.Error),
                    supportingText = {
                        if (loginId.isNotEmpty() && !isLoginIdValid) {
                            Text("Enter a 10-digit phone number or valid email", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                        }
                    }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            
            OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password", color = TextGold.copy(alpha = 0.7f)) },
                    leadingIcon = {
                        Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = PrimaryGold
                        )
                    },
                    trailingIcon = {
                        Icon(
                                imageVector =
                                        if (showPassword) Icons.Default.Visibility
                                        else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = PrimaryGold,
                                modifier = Modifier.clickable { showPassword = !showPassword }
                        )
                    },
                    visualTransformation =
                            if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = DarkBrown2,
                        focusedContainerColor = DarkBrown2,
                        unfocusedLabelColor = TextGold.copy(alpha = 0.7f),
                        focusedLabelColor = PrimaryGold,
                        focusedBorderColor = PrimaryGold,
                        unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                        cursorColor = PrimaryGold,
                        errorContainerColor = DarkBrown2,
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextLight),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            val isLoadingStatus = loginStatus is AuthViewModel.LoginResult.Loading
                            val isLoginEnabledAction = isLoginIdValid && password.isNotBlank() && !isLoadingStatus
                            if (isLoginEnabledAction) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.login(loginId, password)
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    isError = password.isBlank() && loginStatus is AuthViewModel.LoginResult.Error
            )

            Spacer(modifier = Modifier.height(spacing.small))

            
            Text(
                    text = "Forgot Password?",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.End).clickable { showForgotDialog = true }.padding(end = spacing.medium),
                    fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(spacing.large))

            
            val loginErrorMessage = (loginStatus as? AuthViewModel.LoginResult.Error)?.message
            if (loginErrorMessage != null) {
                Text(
                        text = loginErrorMessage,
                        color = ErrorPink,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = spacing.medium).fillMaxWidth(),
                        textAlign = TextAlign.Center
                )
            }

            
            val isLoginEnabled = isLoginIdValid && password.isNotBlank() && !isLoading
            Button(
                    onClick = { 
                        if (isLoginEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.login(loginId, password) 
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (isLoginEnabled) PrimaryGold else Color.Gray,
                                    contentColor = DarkBrown1
                            ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = isLoginEnabled
            ) {
                if (isLoading && !isGoogleLogin) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DarkBrown1,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Log In", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Don't have an account? ", color = TextLight, style = MaterialTheme.typography.bodyMedium)
                Text(
                        text = "Sign Up",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { onSignUpClick() }
                )
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            Text(text = "or Continue with", color = TextGold, style = MaterialTheme.typography.labelMedium)

            Spacer(modifier = Modifier.height(spacing.medium))

            Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                        modifier =
                                Modifier.size(52.dp)
                                        .border(1.dp, BorderGold, CircleShape)
                                        .clickable(enabled = !isLoading) { 
                                            isGoogleLogin = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.loginWithGoogle(context) 
                                        },
                        shape = CircleShape,
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isLoading && isGoogleLogin) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = GoogleRed,
                                    strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                    text = "G",
                                    color = GoogleRed,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))
        }

        KhanaBookLoadingOverlay(
            visible = isLoading,
            type = if (isGoogleLogin) LoadingType.GOOGLE_LOGIN else LoadingType.LOGIN
        )

        if (showForgotDialog) {
            ForgotPasswordDialog(
                    viewModel = viewModel,
                    onDismiss = {
                        showForgotDialog = false
                        viewModel.clearResetStatus()
                    }
            )
        }
    }
}

@Composable
fun ForgotPasswordDialog(viewModel: AuthViewModel, onDismiss: () -> Unit) {
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

    val resetStatus by viewModel.resetPasswordStatus.collectAsState()
    val isResetLoading = resetStatus is AuthViewModel.ResetPasswordResult.Loading

    LaunchedEffect(resetStatus) {
        when (resetStatus) {
            is AuthViewModel.ResetPasswordResult.OtpSent -> {
                step = 2
                resendTimer = 60
                while (resendTimer > 0) { delay(1000); resendTimer-- }
            }
            is AuthViewModel.ResetPasswordResult.Success -> {
                android.widget.Toast.makeText(context, "Password reset successfully!", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            else -> {}
        }
    }

    KhanaBookLargeDialog(
        title = "Forgot Password",
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        actions = {
            Button(
                onClick = {
                    when (step) {
                        1 -> if (isPhoneValid && !isResetLoading) viewModel.sendOtp(phone, "reset")
                        2 -> if (otp.length == 6) step = 3
                        3 -> if (newPassword.isNotBlank() && newPassword == confirmPassword) {
                            viewModel.resetPassword(phone, otp, newPassword)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                shape = RoundedCornerShape(12.dp),
                enabled = when (step) {
                    1 -> isPhoneValid && !isResetLoading
                    2 -> otp.length == 6
                    3 -> newPassword.isNotBlank() && newPassword == confirmPassword
                    else -> false
                }
            ) {
                if (step == 1 && isResetLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBrown1, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = when (step) {
                            1 -> "Send OTP"
                            2 -> "Verify OTP"
                            3 -> "Reset Password"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (step == 2) {
                TextButton(
                    onClick = { if (resendTimer == 0 && !isResetLoading) viewModel.sendOtp(phone, "reset") },
                    enabled = resendTimer == 0 && !isResetLoading
                ) {
                    Text(
                        text = if (resendTimer > 0) "Resend OTP in ${resendTimer}s" else "Resend OTP",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (resendTimer > 0 || isResetLoading) Color.Gray else PrimaryGold
                    )
                }
            }

            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        when (step) {
            1 -> {
                Text(
                    text = "Enter your registered WhatsApp number to receive an OTP.",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                KhanaBookInputField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() }.take(10) },
                    label = "WhatsApp Number",
                    placeholder = "Enter your 10-digit number",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = PrimaryGold) },
                    isError = phone.isNotEmpty() && !isPhoneValid,
                    supportingText = {
                        if (phone.isNotEmpty() && !isPhoneValid) {
                            Text("Enter 10-digit number", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
            2 -> {
                Text(
                    text = "Enter the 6-digit OTP sent to $phone via WhatsApp.",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                KhanaBookInputField(
                    value = otp,
                    onValueChange = { if (it.length <= 6) otp = it },
                    label = "Enter OTP",
                    placeholder = "6-digit code",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    supportingText = {
                        if (otp.isNotEmpty() && otp.length < 6) {
                            Text(
                                "${6 - otp.length} more digit${if (6 - otp.length == 1) "" else "s"} required",
                                color = TextGold.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }
            3 -> {
                Text(
                    text = "Create a new strong password for your account.",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                KhanaBookInputField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password",
                    placeholder = "Min 8 chars with symbols",
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = PrimaryGold) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle New Password",
                            tint = PrimaryGold,
                            modifier = Modifier.clickable { showNewPassword = !showNewPassword }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(spacing.small))
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
                            contentDescription = "Toggle Confirm Password",
                            tint = PrimaryGold,
                            modifier = Modifier.clickable { showConfirmPassword = !showConfirmPassword }
                        )
                    },
                    supportingText = {
                        if (!passwordsMatch) {
                            Text("Passwords do not match", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
        }

        val resetErrorMessage = (resetStatus as? AuthViewModel.ResetPasswordResult.Error)?.message
        if (resetErrorMessage != null) {
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = resetErrorMessage,
                color = ErrorPink,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun loginTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                focusedBorderColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                focusedLabelColor = PrimaryGold,
                unfocusedLabelColor = Color.Gray,
                errorBorderColor = ErrorPink,
                errorLabelColor = ErrorPink
        )
