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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.khanabook.lite.pos.BuildConfig
import android.util.Log
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

    val googleSignInClient = remember(context) {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.loginWithGoogleToken(idToken)
            } else {
                viewModel.setGoogleLoginError("Google Sign-In did not return a valid token. Please try again.")
            }
        } catch (e: ApiException) {
            Log.e("GOOGLE_SIGN_IN", "statusCode=${e.statusCode}, message=${e.localizedMessage}", e)
            when (e.statusCode) {
                com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                    viewModel.setGoogleLoginError("Google Sign-In was cancelled.", AuthViewModel.LoginErrorCode.GOOGLE_CANCELLED)
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR ->
                    viewModel.setGoogleLoginError("Network error during Google Sign-In. Please try again.")
                else ->
                    viewModel.setGoogleLoginError("Google Sign-In failed (${e.statusCode}). Please try again.")
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
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
            .background(Color(0xFF0F081D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header: Midnight Purple Gradient Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E1035), Color(0xFF0F081D))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // White card containing Logo
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.size(100.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_khanabook_logo),
                                contentDescription = "KhanaBook logo",
                                modifier = Modifier.size(68.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "KhanaBook",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = "Restaurant POS & Management",
                        color = Color(0xFFA78BFA), // Lavender text
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // White Sheet Container containing the form
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = "Welcome back",
                        color = Color(0xFF0F172A),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Sign in to your restaurant account",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(28.dp))

                    // PHONE NUMBER field
                    Text(
                        text = "PHONE NUMBER",
                        color = Color(0xFF334155),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextField(
                        value = loginId,
                        onValueChange = {
                            val updatedLoginId = it.trim()
                            loginId = updatedLoginId
                            if (ValidationUtils.isValidPhone(updatedLoginId)) {
                                runCatching { passwordFocusRequester.requestFocus() }
                            }
                        },
                        placeholder = { Text("Enter phone number", color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = "+91",
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(Color(0xFFCBD5E1))
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            disabledTextColor = Color(0xFF64748B),
                            errorTextColor = Color(0xFFDC2626),
                            focusedContainerColor = Color(0xFFF5F3FF),
                            unfocusedContainerColor = Color(0xFFF5F3FF),
                            disabledContainerColor = Color(0xFFF5F3FF),
                            errorContainerColor = Color(0xFFFFF1F2),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFFF97316)
                        ),
                        singleLine = true,
                        isError = (loginId.isNotEmpty() && !ValidationUtils.isValidPhone(loginId)) || (loginId.isBlank() && loginStatus is AuthViewModel.LoginResult.Error),
                        supportingText = {
                            if (loginId.isNotEmpty() && !ValidationUtils.isValidPhone(loginId)) {
                                Text("Enter a valid 10-digit phone number", color = Color(0xFFDC2626), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { runCatching { passwordFocusRequester.requestFocus() } }
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // PASSWORD field
                    Text(
                        text = "PASSWORD",
                        color = Color(0xFF334155),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("........", color = Color(0xFF94A3B8)) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.clickable { showPassword = !showPassword }
                            )
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            disabledTextColor = Color(0xFF64748B),
                            errorTextColor = Color(0xFFDC2626),
                            focusedContainerColor = Color(0xFFF5F3FF),
                            unfocusedContainerColor = Color(0xFFF5F3FF),
                            disabledContainerColor = Color(0xFFF5F3FF),
                            errorContainerColor = Color(0xFFFFF1F2),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFFF97316)
                        ),
                        singleLine = true,
                        isError = password.isBlank() && loginStatus is AuthViewModel.LoginResult.Error,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val isLoadingStatus = loginStatus is AuthViewModel.LoginResult.Loading
                                val isLoginEnabledAction = loginId.isNotBlank() && password.isNotBlank() && !isLoadingStatus
                                if (isLoginEnabledAction) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.login(loginId, password)
                                }
                                focusManager.clearFocus()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Forgot Password?",
                        color = Color(0xFFF97316),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { showForgotDialog = true },
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    val loginErrorMessage = (loginStatus as? AuthViewModel.LoginResult.Error)?.message
                    if (loginErrorMessage != null) {
                        Text(
                            text = loginErrorMessage,
                            color = Color(0xFFDC2626),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    val isLoginEnabled = loginId.isNotBlank() && password.isNotBlank() && !isLoading
                    Button(
                        onClick = {
                            if (isLoginEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.login(loginId, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLoginEnabled) Color(0xFFEA580C) else Color(0xFFCBD5E1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = isLoginEnabled
                    ) {
                        if (isLoading && !isGoogleLogin) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Continue", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // "or continue with" divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                        Text(
                            text = "or continue with",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sign in with Google Outlined Button
                    OutlinedButton(
                        onClick = {
                            if (!isLoading) {
                                isGoogleLogin = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading && isGoogleLogin) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFFEA580C),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sign in with Google",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New to KhanaBook? ",
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Sign Up",
                            color = Color(0xFF7C3AED),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable { onSignUpClick() }
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
            type = if (isGoogleLogin) LoadingType.GOOGLE_LOGIN else LoadingType.LOGIN
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
    val iconSize = KhanaBookTheme.iconSize

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
                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        },
        actions = {
            if (step == 1) {
                Button(
                    onClick = { if (isPhoneValid && !isResetLoading) viewModel.sendOtp(phone, "reset") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary, contentColor = MaterialTheme.kbTextOnBrand),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isPhoneValid && !isResetLoading
                ) {
                    if (isResetLoading)
                        CircularProgressIndicator(modifier = Modifier.size(iconSize.small), color = MaterialTheme.kbTextOnBrand, strokeWidth = 2.dp)
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
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbSecondary),
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary, contentColor = MaterialTheme.kbTextOnBrand),
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
                            color = if (resendTimer > 0 || isResetLoading) TextMuted else MaterialTheme.kbTertiary
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
                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = MaterialTheme.kbSecondary) },
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
                Text(
                    text = "We've sent a 6-digit code to +91 $phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.kbTextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OtpInputRow(
                    otp = otp,
                    onOtpChange = { otp = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                val err = fieldError("otp")
                if (err != null) {
                    Text(err, color = ErrorPink, style = MaterialTheme.typography.bodySmall)
                }
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
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.kbSecondary) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.kbSecondary,
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
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.kbSecondary) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.kbSecondary,
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
                    isActive -> MaterialTheme.kbPrimary.copy(alpha = 0.18f)
                    else -> DarkBrown2
                },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isDone -> SuccessGreen.copy(alpha = 0.55f)
                        isActive -> MaterialTheme.kbPrimary.copy(alpha = 0.75f)
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
                        color = if (isDone) SuccessGreen else MaterialTheme.kbTextSecondary.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = label,
                        color = if (isActive || isDone) TextLight else MaterialTheme.kbTextSecondary.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

