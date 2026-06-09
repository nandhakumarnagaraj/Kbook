@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.BasicTextField
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
    val isLoginUserChecking by viewModel.isLoginUserChecking.collectAsState()
    val loginUserCheckError by viewModel.loginUserCheckError.collectAsState()
    val isLoading = loginStatus is AuthViewModel.LoginResult.Loading
    val isLoginIdValid = ValidationUtils.isValidPhone(loginId)
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

    LaunchedEffect(loginId) {
        val digits = loginId.filter { it.isDigit() }
        if (digits.length == 10) {
            viewModel.checkUserExistsForLogin(digits)
        } else {
            viewModel.clearLoginUserCheck()
        }
    }

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
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Sign in to your restaurant account",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
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
                            val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                            loginId = filtered
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
                        trailingIcon = {
                            when {
                                isLoginUserChecking -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFF97316)
                                )
                                loginUserCheckError != null -> Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                loginId.length == 10 && !isLoginUserChecking -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(18.dp)
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
                        isError = (loginId.isNotEmpty() && !ValidationUtils.isValidPhone(loginId))
                            || loginUserCheckError != null
                            || (loginId.isBlank() && loginStatus is AuthViewModel.LoginResult.Error),
                        supportingText = {
                            when {
                                loginUserCheckError != null ->
                                    Text(loginUserCheckError!!, color = Color(0xFFDC2626), style = MaterialTheme.typography.labelSmall)
                                loginId.isNotEmpty() && !ValidationUtils.isValidPhone(loginId) ->
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
                        placeholder = { Text("Enter password", color = Color(0xFF94A3B8)) },
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
                                val canLogin = isLoginIdValid && password.isNotBlank()
                                    && loginUserCheckError == null && !isLoginUserChecking
                                    && loginStatus !is AuthViewModel.LoginResult.Loading
                                if (canLogin) {
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

                    Spacer(modifier = Modifier.height(8.dp))

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

                    // Form-filled state drives button color (same as signup page)
                    val isFormFilled = isLoginIdValid && password.isNotBlank() && loginUserCheckError == null
                    val isLoginEnabled = isFormFilled && !isLoading && !isLoginUserChecking

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
                            containerColor = if (isFormFilled) Color(0xFFF97316) else Color(0xFFCBD5E1),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFCBD5E1),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = isFormFilled
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

                    Spacer(modifier = Modifier.height(6.dp))

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
                while (resendTimer > 0) {
                    delay(1000)
                    resendTimer--
                }
            }
            is AuthViewModel.ResetPasswordResult.Success -> {
                onSuccess(context.getString(R.string.toast_password_reset))
                onDismiss()
            }
            else -> {}
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F081D))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Midnight Purple Gradient Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E1035), Color(0xFF0F081D))
                        )
                    )
            ) {
                // Back Button & Logo Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_khanabook_logo),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Envelope / Mail Icon & Text
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(Color(0xFFF97316).copy(alpha = 0.12f), CircleShape)
                            .border(1.5.dp, Color(0xFFF97316).copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .background(Color(0xFFF97316).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (step == 3) Icons.Default.Lock else Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (step == 3) "Reset Password" else "Forgot Password?",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (step) {
                            1 -> "Enter your registered phone number. We'll send a 6-digit OTP to reset your password."
                            2 -> "Enter the 6-digit OTP sent to +91 $phone."
                            else -> "Create a new strong password for your account."
                        },
                        color = Color(0xFFA78BFA),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
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
                    if (step == 1 || step == 2) {
                        // PHONE NUMBER field
                        Text(
                            text = "PHONE NUMBER",
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = phone,
                            onValueChange = {
                                if (step == 1) {
                                    phone = it.filter { ch -> ch.isDigit() }.take(10)
                                }
                            },
                            readOnly = step == 2,
                            placeholder = { Text("98765 43210", color = Color(0xFF94A3B8).copy(alpha = 0.6f)) },
                            leadingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                                ) {
                                    Text(
                                        text = "+91",
                                        color = Color(0xFF7C3AED),
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
                                focusedContainerColor = Color(0xFFF5F3FF),
                                unfocusedContainerColor = Color(0xFFF5F3FF),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFFF97316)
                            ),
                            singleLine = true,
                            isError = phone.isNotEmpty() && !isPhoneValid || fieldError("phoneNumber", "loginId", "whatsappNumber") != null,
                            supportingText = {
                                val err = fieldError("phoneNumber", "loginId", "whatsappNumber")
                                when {
                                    err != null -> Text(err, color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                                    phone.isNotEmpty() && !isPhoneValid -> Text("Enter 10-digit number", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            )
                        )

                        if (step == 2) {
                            Spacer(modifier = Modifier.height(20.dp))

                            // OTP CODE label
                            Text(
                                text = "OTP CODE",
                                color = Color(0xFF334155),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            ForgotPasswordOtpInputRow(
                                otp = otp,
                                onOtpChange = { otp = it.filter(Char::isDigit).take(6) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val timerText = if (resendTimer > 0) {
                                val min = resendTimer / 60
                                val sec = resendTimer % 60
                                "Resend OTP in ${String.format("%d:%02d", min, sec)}"
                            } else {
                                "Resend OTP"
                            }

                            Text(
                                text = timerText,
                                color = if (resendTimer > 0) Color(0xFF94A3B8) else Color(0xFFF97316),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable(enabled = resendTimer == 0 && !isResetLoading) {
                                        viewModel.sendOtp(phone, "reset")
                                    }
                            )

                            val err = fieldError("otp")
                            if (err != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(err, color = ErrorPink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (step == 2) {
                            // "Verify & Continue" Button (Active in Step 2)
                            Button(
                                onClick = {
                                    if (otp.length == 6) {
                                        step = 3
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (otp.length == 6) Color(0xFFF97316) else Color(0xFFCBD5E1),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                enabled = otp.length == 6
                            ) {
                                Text("Verify & Continue", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // "Send OTP" Button
                        OutlinedButton(
                            onClick = {
                                if (isPhoneValid && !isResetLoading) {
                                    viewModel.sendOtp(phone, "reset")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7C3AED)),
                            border = BorderStroke(1.5.dp, if (isPhoneValid && !isResetLoading) Color(0xFF7C3AED) else Color(0xFFE2E8F0)),
                            enabled = isPhoneValid && !isResetLoading && (step == 1 || resendTimer == 0)
                        ) {
                            if (isResetLoading && step == 1) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF7C3AED), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (step == 2) "Resend OTP" else "Send OTP",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else if (step == 3) {
                        // NEW PASSWORD field
                        Text(
                            text = "NEW PASSWORD",
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            placeholder = { Text("Enter new password", color = Color(0xFF94A3B8)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.clickable { showNewPassword = !showNewPassword }
                                )
                            },
                            visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedContainerColor = Color(0xFFF5F3FF),
                                unfocusedContainerColor = Color(0xFFF5F3FF),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFFF97316)
                            ),
                            singleLine = true,
                            isError = fieldError("password") != null,
                            supportingText = {
                                fieldError("password")?.let { Text(it, color = ErrorPink, style = MaterialTheme.typography.labelSmall) }
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // CONFIRM PASSWORD field
                        Text(
                            text = "CONFIRM PASSWORD",
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val passwordsMatch = confirmPassword.isEmpty() || newPassword == confirmPassword

                        TextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            placeholder = { Text("Repeat new password", color = Color(0xFF94A3B8)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.clickable { showConfirmPassword = !showConfirmPassword }
                                )
                            },
                            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedContainerColor = Color(0xFFF5F3FF),
                                unfocusedContainerColor = Color(0xFFF5F3FF),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFFF97316)
                            ),
                            singleLine = true,
                            isError = !passwordsMatch,
                            supportingText = {
                                if (!passwordsMatch) Text("Passwords do not match", color = ErrorPink, style = MaterialTheme.typography.labelSmall)
                            }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        val isResetEnabled = newPassword.isNotBlank() && passwordsMatch && !isResetLoading

                        Button(
                            onClick = {
                                if (isResetEnabled) {
                                    viewModel.resetPassword(phone, otp, newPassword)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isResetEnabled) Color(0xFFF97316) else Color(0xFFCBD5E1),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = isResetEnabled
                        ) {
                            if (isResetLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Reset Password", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                step = 2
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7C3AED)),
                            border = BorderStroke(1.5.dp, Color(0xFF7C3AED))
                        ) {
                            Text("Back", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    val resetErrorMessage = (resetStatus as? AuthViewModel.ResetPasswordResult.Error)?.message
                    if (resetErrorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = resetErrorMessage,
                            color = ErrorPink,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordOtpInputRow(
    otp: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 48.dp,
    boxHeight: Dp = 56.dp,
    spacing: Dp = 10.dp
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }

    LaunchedEffect(Unit) {
        runCatching { focusRequesters[0].requestFocus() }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 6) {
            val isFilled = i < otp.length
            val char = otp.getOrElse(i) { ' ' }

            Box(
                modifier = Modifier
                    .width(boxSize)
                    .height(boxHeight)
                    .background(Color(0xFFF5F3FF), RoundedCornerShape(12.dp))
                    .then(
                        if (isFilled) Modifier.border(1.5.dp, Color(0xFF7C3AED), RoundedCornerShape(12.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = if (isFilled) char.toString() else "",
                    onValueChange = { value ->
                        if (value.length <= 1 && value.all { it.isDigit() }) {
                            val sb = StringBuilder(otp)
                            if (value.isEmpty()) {
                                if (i < sb.length) sb.deleteCharAt(i)
                                if (i > 0) runCatching { focusRequesters[i - 1].requestFocus() }
                            } else {
                                if (i >= sb.length) sb.append(value.first())
                                else sb[i] = value.first()
                                if (i < 5) runCatching { focusRequesters[i + 1].requestFocus() }
                            }
                            onOtpChange(sb.toString())
                        } else if (value.isEmpty()) {
                            if (i > 0) runCatching { focusRequesters[i - 1].requestFocus() }
                        }
                    },
                    modifier = Modifier.fillMaxSize().focusRequester(focusRequesters[i]),
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        color = Color(0xFF0F172A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFFF97316)),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

