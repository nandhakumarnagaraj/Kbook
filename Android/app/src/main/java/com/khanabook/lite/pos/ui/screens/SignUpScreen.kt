@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.designsystem.KhanaBookLoadingOverlay
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSnackbarHost
import com.khanabook.lite.pos.ui.designsystem.LoadingType
import com.khanabook.lite.pos.ui.designsystem.verticalScrollbar
import com.khanabook.lite.pos.ui.designsystem.KhanaBookPurpleBackground
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onLoginClick: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var shopName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    
    val phoneFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    val isShopNameValid = ValidationUtils.isValidName(shopName)
    val isPhoneValid = ValidationUtils.isValidPhone(phoneNumber)
    val isPasswordValid = ValidationUtils.isValidPassword(newPassword)

    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableIntStateOf(60) }
    val signUpStatus by viewModel.signUpStatus.collectAsState()
    val loginStatus by viewModel.loginStatus.collectAsState()
    val isUserChecking by viewModel.isUserChecking.collectAsState()
    val userExistsError by viewModel.userExistsError.collectAsState()
    val signUpFieldErrors by viewModel.signUpFieldErrors.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val isLoading = signUpStatus is AuthViewModel.SignUpResult.Loading || loginStatus is AuthViewModel.LoginResult.Loading

    LaunchedEffect(signUpStatus) {
        when (signUpStatus) {
            is AuthViewModel.SignUpResult.Success -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is AuthViewModel.SignUpResult.OtpSent -> {
                otpSent = true
                otpTimer = 60
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is AuthViewModel.SignUpResult.Error -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    LaunchedEffect(otpSent) {
        if (otpSent) {
            otpTimer = 60
            while (otpTimer > 0) {
                delay(1000)
                otpTimer--
            }
        }
    }

    fun fieldError(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        signUpFieldErrors[key]?.takeIf { it.isNotBlank() }
    }

    BackHandler(onBack = onLoginClick)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0820))
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            KhanaBookPurpleBackground(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(vertical = 48.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.size(110.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.khanabook_logo),
                                contentDescription = "KhanaBook logo",
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "KhanaBook",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Restaurant POS & Management",
                        color = Color(0xFFB4ACE8), // Soft lavender tint
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Surface Form Card - smoother rounded corners
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .verticalScrollbar(scrollState)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = "Create Your Restaurant",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Start billing in 2 minutes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // RESTAURANT NAME
                    Text(
                        text = "RESTAURANT NAME",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val shopNameError = (shopName.isNotEmpty() && !isShopNameValid) || fieldError("name", "shopName") != null
                    OnboardingInputField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        placeholder = "Restaurant Name",
                        isError = shopNameError,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { phoneFocusRequester.requestFocus() } })
                    )
                    val err = fieldError("name", "shopName")
                    if (err != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err, color = KbError, style = MaterialTheme.typography.labelSmall)
                    } else if (shopName.isNotEmpty() && !isShopNameValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Shop name must be at least 2 characters", color = KbError, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // WHATSAPP NUMBER
                    Text(
                        text = "WHATSAPP NUMBER",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OnboardingPhoneInput(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it.take(10)
                            if (phoneNumber.length == 10) {
                                viewModel.checkUserExists(phoneNumber)
                            }
                        },
                        placeholder = "WhatsApp Number",
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(phoneFocusRequester),
                        isError = (phoneNumber.isNotEmpty() && !isPhoneValid) || userExistsError != null || fieldError("phoneNumber", "loginId", "whatsappNumber") != null,
                        trailingIcon = {
                            if (isUserChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = KbBrandSaffron
                                )
                            } else if (!otpSent || otpTimer == 0) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.sendOtp(phoneNumber)
                                        otpSent = true
                                    },
                                    modifier = Modifier.height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    enabled = isPhoneValid && !isLoading && !isUserChecking && userExistsError == null
                                ) {
                                    Text(
                                        "Send OTP",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Sent",
                                    color = KbWhatsAppGreen,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { passwordFocusRequester.requestFocus() } })
                    )

                    val phoneError = fieldError("phoneNumber", "loginId", "whatsappNumber") ?: userExistsError
                    if (phoneError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(phoneError, color = KbError, style = MaterialTheme.typography.labelSmall)
                    } else if (phoneNumber.isNotEmpty() && !isPhoneValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Enter a valid 10-digit number", color = KbError, style = MaterialTheme.typography.labelSmall)
                    }

                    // Conditional OTP row inline
                    if (otpSent) {
                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "ENTER OTP",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.12.em
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OtpInputRow(
                            otp = otp,
                            onOtpChange = { code -> otp = code.filter { ch -> ch.isDigit() }.take(6) }
                        )

                        val otpError = fieldError("otp")
                        if (otpError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = otpError, color = KbError,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val resendText = if (otpTimer > 0) {
                                val min = otpTimer / 60
                                val sec = otpTimer % 60
                                "Resend code in ${String.format("%02d:%02d", min, sec)}"
                            } else {
                                "Resend code"
                            }

                            Text(
                                text = resendText,
                                color = if (otpTimer > 0) MaterialTheme.colorScheme.onSurfaceVariant else KbBrandVioletBright,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clickable(enabled = otpTimer == 0 && !isLoading) {
                                        viewModel.sendOtp(phoneNumber)
                                    }
                            )

                            Text(
                                text = "Change number?", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clickable(enabled = !isLoading) { otpSent = false }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // PASSWORD
                    Text(
                        text = "PASSWORD",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val passwordError = (newPassword.isNotEmpty() && !isPasswordValid) || fieldError("password") != null
                    OnboardingInputField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = "Enter password",
                        isError = passwordError,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { confirmPasswordFocusRequester.requestFocus() } }),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester)
                    )
                    val pwErr = fieldError("password")
                    if (pwErr != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(pwErr, color = KbError, style = MaterialTheme.typography.labelSmall)
                    } else if (newPassword.isNotEmpty() && !isPasswordValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Must be 8+ chars with uppercase, digit & symbol", color = KbError, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // CONFIRM PASSWORD
                    Text(
                        text = "CONFIRM PASSWORD",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val passwordsMatch = confirmPassword.isEmpty() || newPassword == confirmPassword

                    OnboardingInputField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = "Re‑enter password",
                        isError = !passwordsMatch,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Confirm Password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(confirmPasswordFocusRequester)
                    )
                    if (!passwordsMatch) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Passwords do not match", color = KbError, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Terms and Conditions Checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = KbBrandVioletBright,
                                uncheckedColor = MaterialTheme.kbOutlineSubtle,
                                checkmarkColor = Color.White
                            )
                        )
                        val annotatedString = buildAnnotatedString {
                            append("I agree to ")
                            pushStringAnnotation(tag = "URL", annotation = "https://khanabook.com/legal-privacy.html")
                            withStyle(style = SpanStyle(color = KbBrandVioletBright, fontWeight = FontWeight.Bold)) {
                                append("Terms")
                            }
                            pop()
                            append(" & ")
                            pushStringAnnotation(tag = "URL", annotation = "https://khanabook.com/legal-privacy.html")
                            withStyle(style = SpanStyle(color = KbBrandVioletBright, fontWeight = FontWeight.Bold)) {
                                append("Privacy Policy")
                            }
                            pop()
                        }
                        ClickableText(
                            text = annotatedString,
                            style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let {
                                        // Handle terms/privacy web link open
                                    }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val isFormFilled = shopName.isNotBlank() &&
                            phoneNumber.isNotBlank() &&
                            otpSent &&
                            otp.isNotBlank() &&
                            newPassword.isNotBlank() &&
                            confirmPassword.isNotBlank() &&
                            agreedToTerms

                    val isFormValid = isShopNameValid && isPhoneValid && isPasswordValid && (newPassword == confirmPassword) && agreedToTerms && otpSent && otp.length == 6 && !isLoading && userExistsError == null

                    Button(
                        onClick = {
                            if (isFormValid) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.signUp(
                                    shopName = shopName,
                                    ownerName = shopName, // Map empty ownerName to shopName cleanly
                                    phoneNumber = phoneNumber,
                                    otp = otp,
                                    password = newPassword
                                )
                            } else {
                                val errorMsg = when {
                                    !isShopNameValid -> "Shop name must be at least 2 characters"
                                    !isPhoneValid -> "Please enter a valid 10-digit WhatsApp number"
                                    userExistsError != null -> userExistsError ?: "WhatsApp number already registered"
                                    otp.length != 6 -> "Please enter a valid 6-digit OTP"
                                    !isPasswordValid -> "Password must be at least 8 characters with uppercase, digit & symbol"
                                    newPassword != confirmPassword -> "Passwords do not match"
                                    !agreedToTerms -> "You must agree to the Terms & Privacy Policy"
                                    else -> "Please correct the highlighted errors"
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(errorMsg)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = isFormFilled
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Create Account",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have an account? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Sign In",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable { onLoginClick() }
                        )
                    }
                }
            }
        }

        KhanaBookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        KhanaBookLoadingOverlay(
            visible = isLoading,
            type = LoadingType.SIGNUP
        )
    }
}
