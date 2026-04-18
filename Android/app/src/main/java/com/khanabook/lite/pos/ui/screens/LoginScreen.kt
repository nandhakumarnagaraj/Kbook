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
import kotlinx.coroutines.launch
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(loginStatus) {
        when (val s = loginStatus) {
            is AuthViewModel.LoginResult.Loading -> {}
            is AuthViewModel.LoginResult.Success -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_welcome_back))
                }
                onLoginSuccess()
            }
            is AuthViewModel.LoginResult.Error -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> { isGoogleLogin = false }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBrown1),
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )

        if (showForgotDialog) {
            ForgotPasswordDialog(
                    viewModel = viewModel,
                    onDismiss = {
                        showForgotDialog = false
                        viewModel.clearResetStatus()
                    },
                    onSuccess = { message ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                    }
            )
        }
    }
}

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

    val resetStatus by viewModel.resetPasswordStatus.collectAsState()
    val resetFieldErrors by viewModel.resetPasswordFieldErrors.collectAsState()
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
                .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = TextLight, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isPhoneValid && !isResetLoading
                ) {
                    if (isResetLoading)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBrown1, strokeWidth = 2.dp)
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
                        onClick = { if (!isResetLoading) { step -= 1; viewModel.clearResetStatus() } },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGold),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Back", style = MaterialTheme.typography.labelLarge) }

                    Button(
                        onClick = {
                            when (step) {
                                2 -> if (otp.length == 6) step = 3
                                3 -> if (newPassword.isNotBlank() && newPassword == confirmPassword)
                                    viewModel.resetPassword(phone, otp, newPassword)
                            }
                        },
                        modifier = Modifier.weight(2f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = DarkBrown1),
                        shape = RoundedCornerShape(12.dp),
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
                            color = if (resendTimer > 0 || isResetLoading) Color.Gray else PrimaryGold
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
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
