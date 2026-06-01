@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.android.awaitFrame
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SignUpScreen(
        onSignUpSuccess: () -> Unit,
        onLoginClick: () -> Unit = {},
        viewModel: AuthViewModel = hiltViewModel()
) {
    var shopName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val phoneFocusRequester = remember { FocusRequester() }
    val otpFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize


    val isNameValid = ValidationUtils.isValidName(shopName)
    val isPhoneValid = ValidationUtils.isValidPhone(phoneNumber)
    val isPasswordValid = ValidationUtils.isValidPassword(newPassword)
    val passwordsMatch = newPassword == confirmPassword && newPassword.isNotEmpty()

    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableIntStateOf(120) }
    val signUpStatus by viewModel.signUpStatus.collectAsState()
    val loginStatus by viewModel.loginStatus.collectAsState()
    val isUserChecking by viewModel.isUserChecking.collectAsState()
    val userExistsError by viewModel.userExistsError.collectAsState()
    val signUpFieldErrors by viewModel.signUpFieldErrors.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val isLoading = signUpStatus is AuthViewModel.SignUpResult.Loading || loginStatus is AuthViewModel.LoginResult.Loading

    LaunchedEffect(signUpStatus) {
        when (val status = signUpStatus) {
            is AuthViewModel.SignUpResult.Loading -> {}
            is AuthViewModel.SignUpResult.Success -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is AuthViewModel.SignUpResult.OtpSent -> {
                otpSent = true
                otpTimer = 120
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHostState.showSnackbar(
                        "OTP Sent to your WhatsApp!",
                        duration = SnackbarDuration.Long
                )
            }
            is AuthViewModel.SignUpResult.Error -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                snackbarHostState.showSnackbar(status.message)
            }
            else -> {}
        }
    }

    LaunchedEffect(phoneNumber) {
        if (phoneNumber.length < 10) {
            viewModel.clearUserCheck()
        }
    }

    LaunchedEffect(loginStatus) {
        if (loginStatus is AuthViewModel.LoginResult.Success) {
            onSignUpSuccess()
            viewModel.resetSignUpStatus()
        }
    }

    fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    fun fieldError(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        signUpFieldErrors[key]?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(otpSent) {
        if (otpSent) {
            awaitFrame()
            runCatching { otpFocusRequester.requestFocus() }
            otpTimer = 60
            while (otpTimer > 0) {
                delay(1000)
                otpTimer--
            }
        }
    }

    val currentStep = if (otpSent) 2 else 1

    Scaffold(
        snackbarHost = { KhanaBookSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.White
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = KbBrandSaffron
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    StepRow(currentStep = currentStep)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verify your number",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.kbTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "We'll send an OTP via WhatsApp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.kbTextSecondary
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                        phoneNumber = filtered
                        if (filtered.length == 10) {
                            viewModel.checkUserExists(filtered)
                        }
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("Enter your 10-digit number") },
                    leadingIcon = {
                        Text(
                            text = "+91",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { if (otpSent) runCatching { otpFocusRequester.requestFocus() } }
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(phoneFocusRequester),
                    shape = KbShape.Medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KbBrandSaffron,
                        unfocusedBorderColor = MaterialTheme.kbOutlineSubtle,
                        cursorColor = KbBrandSaffron,
                        focusedLabelColor = KbBrandSaffron
                    ),
                    singleLine = true,
                    enabled = !isLoading,
                    isError = (phoneNumber.isNotEmpty() && !isPhoneValid) || userExistsError != null || fieldError("phoneNumber", "loginId", "whatsappNumber") != null,
                    supportingText = {
                        val backendFieldError = fieldError("phoneNumber", "loginId", "whatsappNumber")
                        if (backendFieldError != null) {
                            Text(backendFieldError, color = KbError, style = MaterialTheme.typography.labelSmall)
                        } else if (userExistsError != null) {
                            Text(userExistsError.orEmpty(), color = KbError, style = MaterialTheme.typography.labelSmall)
                        } else if (phoneNumber.isNotEmpty() && !isPhoneValid) {
                            Text("Enter 10-digit number", color = KbError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )

                if (!otpSent) {
                    Spacer(modifier = Modifier.height(24.dp))

                    val canSendOtp = isPhoneValid && userExistsError == null && !isLoading && !isUserChecking
                    Button(
                        onClick = {
                            if (canSendOtp) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.sendOtp(phoneNumber)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KbButtonSize.HeightLarge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canSendOtp) KbBrandSaffron else MaterialTheme.kbTextDisabled,
                            contentColor = Color.White
                        ),
                        shape = KbShape.Medium,
                        enabled = canSendOtp
                    ) {
                        if (isLoading && signUpStatus is AuthViewModel.SignUpResult.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send OTP", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                if (otpSent) {
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop Name") },
                        placeholder = { Text("Enter your shop/restaurant name") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.kbSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = KbShape.Medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KbBrandSaffron,
                            unfocusedBorderColor = MaterialTheme.kbOutlineSubtle,
                            cursorColor = KbBrandSaffron,
                            focusedLabelColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { otpFocusRequester.requestFocus() } }),
                        isError = (shopName.isNotEmpty() && !isNameValid) || fieldError("name", "shopName") != null,
                        supportingText = {
                            val backendFieldError = fieldError("name", "shopName")
                            if (backendFieldError != null) {
                                Text(backendFieldError, color = KbError, style = MaterialTheme.typography.labelSmall)
                            } else if (shopName.isNotEmpty() && !isNameValid)
                                Text("Shop name too short", color = KbError, style = MaterialTheme.typography.labelSmall)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "We've sent a 6-digit code to +91 $phoneNumber",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.kbTextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OtpInputRow(
                        otp = otp,
                        onOtpChange = { otp = it.filter { ch -> ch.isDigit() }.take(6) }
                    )

                    if (fieldError("otp") != null) {
                        Text(
                            text = fieldError("otp").orEmpty(),
                            color = KbError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Create New Password") },
                        placeholder = { Text("Min 8 chars with symbols") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.kbSecondary) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.kbSecondary,
                                modifier = Modifier.clickable(enabled = !isLoading) { showNewPassword = !showNewPassword }.padding(end = 8.dp)
                            )
                        },
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                        shape = KbShape.Medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KbBrandSaffron,
                            unfocusedBorderColor = MaterialTheme.kbOutlineSubtle,
                            cursorColor = KbBrandSaffron,
                            focusedLabelColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { runCatching { confirmPasswordFocusRequester.requestFocus() } }
                        ),
                        isError = (newPassword.isNotEmpty() && !isPasswordValid) || fieldError("password") != null,
                        supportingText = {
                            val backendFieldError = fieldError("password")
                            if (backendFieldError != null)
                                Text(backendFieldError, color = KbError, style = MaterialTheme.typography.labelSmall)
                            else if (newPassword.isNotEmpty() && !isPasswordValid)
                                Text("Min 8 chars, uppercase, digit & special character", color = KbError, style = MaterialTheme.typography.labelSmall)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        placeholder = { Text("Repeat your password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.kbSecondary) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.kbSecondary,
                                modifier = Modifier.clickable(enabled = !isLoading) { showConfirmPassword = !showConfirmPassword }.padding(end = 8.dp)
                            )
                        },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().focusRequester(confirmPasswordFocusRequester),
                        shape = KbShape.Medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KbBrandSaffron,
                            unfocusedBorderColor = MaterialTheme.kbOutlineSubtle,
                            cursorColor = KbBrandSaffron,
                            focusedLabelColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val isFormValidAction = isNameValid && isPhoneValid && isPasswordValid && passwordsMatch && otp.length == 6 && !isLoading && userExistsError == null
                                if (isFormValidAction) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.signUp(shopName, phoneNumber, otp, newPassword)
                                }
                                focusManager.clearFocus()
                            }
                        ),
                        isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                        supportingText = {
                            if (confirmPassword.isNotEmpty() && !passwordsMatch)
                                Text("Passwords do not match", color = KbError, style = MaterialTheme.typography.labelSmall)
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val isFormValid = isNameValid && isPhoneValid && isPasswordValid && passwordsMatch && otp.length == 6 && !isLoading && userExistsError == null
                    Button(
                        onClick = {
                            if (isFormValid) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.signUp(shopName, phoneNumber, otp, newPassword)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(KbButtonSize.HeightLarge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFormValid) KbBrandSaffron else MaterialTheme.kbTextDisabled,
                            contentColor = Color.White
                        ),
                        shape = KbShape.Medium,
                        enabled = isFormValid
                    ) {
                        if (isLoading && signUpStatus is AuthViewModel.SignUpResult.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign Up", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Already have an account? ", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Log In",
                        color = KbBrandSaffron,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable(enabled = !isLoading) { onLoginClick() }
                    )
                }
            }
        }

        KhanaBookLoadingOverlay(
            visible = isLoading,
            type = LoadingType.SIGNUP,
            message = if (signUpStatus is AuthViewModel.SignUpResult.Loading) "Creating Account..." else "Logging in..."
        )
    }
}

@Composable
private fun StepRow(currentStep: Int) {
    val steps = listOf("Phone", "OTP", "Profile")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val stepNumber = index + 1
            val isActive = stepNumber == currentStep
            val isDone = stepNumber < currentStep
            val color = when {
                isActive -> Color.White
                isDone -> Color.White.copy(alpha = 0.8f)
                else -> Color.White.copy(alpha = 0.5f)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (isActive) Color.White else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isActive) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isDone) "✓" else "$stepNumber",
                            color = if (isActive) KbBrandSaffron else color,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                )
            }
        }
    }
}
