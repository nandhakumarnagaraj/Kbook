@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
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
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val ownerFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val isShopNameValid = ValidationUtils.isValidName(shopName)
    val isOwnerNameValid = ValidationUtils.isValidName(ownerName)
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
        when (val status = signUpStatus) {
            is AuthViewModel.SignUpResult.Loading -> {}
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

    KhanaBookPurpleBackground(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Midnight Purple Gradient Area with decorative blobs
            KhanaBookPurpleBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // White card containing Logo
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.size(76.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.khanabook_logo),
                                contentDescription = "KhanaBook logo",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "KhanaBook",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Restaurant POS & Management",
                        color = KbLavender,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "🔒 Secure Data",
                            "⚡ Works Offline",
                            "☁️ Auto Backup"
                        ).forEach { badge ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = badge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Form Sheet Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.kbBgCard
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
                        color = Color(0xFF1E293B),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Start billing in 2 minutes",
                        color = Color(0xFF4B5563),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    // RESTAURANT NAME
                    Text(
                        text = "RESTAURANT NAME",
                        color = Color(0xFF2D2D4E),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    TextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        placeholder = { Text("Restaurant Name", color = Color(0xFF71718D)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(color = Color(0xFF1F2937), fontSize = 16.sp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            disabledTextColor = Color(0xFF9CA3AF),
                            errorTextColor = KbError,
                            focusedContainerColor = Color(0xFFECEDF6),
                            unfocusedContainerColor = Color(0xFFECEDF6),
                            disabledContainerColor = Color(0xFFECEDF6),
                            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        isError = (shopName.isNotEmpty() && !isShopNameValid) || fieldError("name", "shopName") != null,
                        supportingText = {
                            val err = fieldError("name", "shopName")
                            if (err != null) {
                                Text(err, color = KbError, style = MaterialTheme.typography.labelSmall)
                            } else if (shopName.isNotEmpty() && !isShopNameValid) {
                                Text("Shop name must be at least 2 characters", color = KbError, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { ownerFocusRequester.requestFocus() } })
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // WHATSAPP NUMBER (renamed and integrated Send OTP)
                    Text(
                        text = "WHATSAPP NUMBER",
                        color = Color(0xFF2D2D4E),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    TextField(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it.take(10)
                            if (phoneNumber.length == 10) {
                                viewModel.checkUserExists(phoneNumber)
                            }
                        },
                        placeholder = { Text("WhatsApp Number", color = Color(0xFF71718D)) },
                        leadingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                            ) {
                                    Text(
                                        text = "+91",
                                        color = Color(0xFF5B4FCF),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(20.dp)
                                            .background(MaterialTheme.kbOutlineSubtle)
                                    )
                            }
                        },
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
                                        otpSent = true // force UI to show OTP field immediately
                                    },
                                    modifier = Modifier.padding(end = 4.dp).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KbBrandSaffron,
                                        disabledContainerColor = Color(0xFFECEDF6)
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    enabled = isPhoneValid && !isLoading && !isUserChecking && userExistsError == null
                                ) {
                                    Text(
                                        "Send OTP",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Sent",
                                    color = KbWhatsAppGreen,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusRequester(phoneFocusRequester),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(color = Color(0xFF1F2937), fontSize = 16.sp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            disabledTextColor = Color(0xFF9CA3AF),
                            errorTextColor = KbError,
                            focusedContainerColor = Color(0xFFECEDF6),
                            unfocusedContainerColor = Color(0xFFECEDF6),
                            disabledContainerColor = Color(0xFFECEDF6),
                            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        isError = (phoneNumber.isNotEmpty() && !isPhoneValid) || userExistsError != null || fieldError("phoneNumber", "loginId", "whatsappNumber") != null,
                        supportingText = {
                            val backendFieldError = fieldError("phoneNumber", "loginId", "whatsappNumber")
                            if (backendFieldError != null) {
                                Text(backendFieldError, color = KbError, style = MaterialTheme.typography.labelSmall)
                            } else if (userExistsError != null) {
                                Text(userExistsError.orEmpty(), color = KbError, style = MaterialTheme.typography.labelSmall)
                            } else if (phoneNumber.isNotEmpty() && !isPhoneValid) {
                                Text("Enter a valid 10-digit number", color = KbError, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { passwordFocusRequester.requestFocus() } })
                    )

                    // Conditional OTP Verification Field directly inline
                    if (otpSent) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "ENTER OTP",
                            color = Color(0xFF2D2D4E),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.12.em
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

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

                        Spacer(modifier = Modifier.height(6.dp))

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
                                color = if (otpTimer > 0) MaterialTheme.kbTextTertiary else KbPurpleAccent,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clickable(enabled = otpTimer == 0 && !isLoading) {
                                        viewModel.sendOtp(phoneNumber)
                                    }
                            )

                            Text(
                                text = "Change number?", color = KbBrandSaffron,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clickable(enabled = !isLoading) { otpSent = false }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // PASSWORD
                    Text(
                        text = "PASSWORD",
                        color = Color(0xFF2D2D4E),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    TextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = { Text("Enter password", color = Color(0xFF71718D)) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = Color(0xFF71718D),
                                modifier = Modifier.clickable { showPassword = !showPassword }
                            )
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusRequester(passwordFocusRequester),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(color = Color(0xFF1F2937), fontSize = 16.sp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            disabledTextColor = Color(0xFF9CA3AF),
                            errorTextColor = KbError,
                            focusedContainerColor = Color(0xFFECEDF6),
                            unfocusedContainerColor = Color(0xFFECEDF6),
                            disabledContainerColor = Color(0xFFECEDF6),
                            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        isError = (newPassword.isNotEmpty() && !isPasswordValid) || fieldError("password") != null,
                        supportingText = {
                            val backendFieldError = fieldError("password")
                            if (backendFieldError != null) {
                                Text(backendFieldError, color = KbError, style = MaterialTheme.typography.labelSmall)
                            } else if (newPassword.isNotEmpty() && !isPasswordValid) {
                                Text("Min 8 chars, uppercase, digit & special character", color = KbError, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    // CONFIRM PASSWORD
                    Text(
                        text = "CONFIRM PASSWORD",
                        color = Color(0xFF2D2D4E),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    TextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = { Text("Re‑enter password", color = Color(0xFF71718D)) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Confirm Password",
                                tint = Color(0xFF71718D),
                                modifier = Modifier.clickable { showPassword = !showPassword }
                            )
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(color = Color(0xFF1F2937), fontSize = 16.sp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            disabledTextColor = Color(0xFF9CA3AF),
                            errorTextColor = KbError,
                            focusedContainerColor = Color(0xFFECEDF6),
                            unfocusedContainerColor = Color(0xFFECEDF6),
                            disabledContainerColor = Color(0xFFECEDF6),
                            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = KbBrandSaffron
                        ),
                        singleLine = true,
                        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                        supportingText = {
                            if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
                                Text("Passwords do not match", color = KbError, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    Spacer(modifier = Modifier.height(4.dp)) // Reduced gap above checkbox

                    // Terms and Conditions Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = KbPurpleAccent,
                                uncheckedColor = MaterialTheme.kbOutlineSubtle,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val annotatedString = buildAnnotatedString {
                            append("I agree to ")
                            pushStringAnnotation(tag = "URL", annotation = "https://khanabook.com/legal-privacy.html")
                            withStyle(style = SpanStyle(color = KbPurpleAccent, fontWeight = FontWeight.Bold)) {
                                append("Terms")
                            }
                            pop()
                            append(" & ")
                            pushStringAnnotation(tag = "URL", annotation = "https://khanabook.com/legal-privacy.html")
                            withStyle(style = SpanStyle(color = KbPurpleAccent, fontWeight = FontWeight.Bold)) {
                                append("Privacy Policy")
                            }
                            pop()
                        }

                        ClickableText(
                            text = annotatedString,
                            style = TextStyle(
                                color = Color(0xFF4B5563),
                                fontSize = 14.sp
                            ),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp)) // Reduced Spacing before the CTA button

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
                                    ownerName = ownerName,
                                    phoneNumber = phoneNumber,
                                    otp = otp,
                                    password = newPassword
                                )
                            } else {
                                val errorMsg = when {
                                    !isShopNameValid -> "Shop name must be at least 2 characters"
                                    !isOwnerNameValid -> "Owner name must be at least 2 characters"
                                    !isPhoneValid -> "Please enter a valid 10-digit WhatsApp number"
                                    userExistsError != null -> userExistsError ?: "WhatsApp number already registered"
                                    otp.length != 6 -> "Please enter a valid 6-digit OTP"
                                    !isPasswordValid -> "Password must be at least 8 characters with uppercase, digit & symbol"
                                    else -> "Please correct the highlighted errors"
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(errorMsg)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFormFilled) KbBrandSaffron else MaterialTheme.kbBgSecondary,
                            contentColor = if (isFormFilled) Color.White else MaterialTheme.kbTextDisabled,
                            disabledContainerColor = MaterialTheme.kbBgSecondary,
                            disabledContentColor = MaterialTheme.kbTextDisabled
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = isFormFilled
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
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
                            color = Color(0xFF4B5563),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Sign In",
                            color = KbBrandSaffron,
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


