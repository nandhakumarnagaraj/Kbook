@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingPhoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(containerColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+91",
            color = KbBrandVioletBright,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingIcon()
        }
    }
}

@Composable
fun OnboardingInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(containerColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingIcon()
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSignUpClick: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val localContext = LocalContext.current

    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var isGoogleLogin by remember { mutableStateOf(false) }
    var shouldCheckUser by remember { mutableStateOf(false) }

    val loginStatus by viewModel.loginStatus.collectAsState()
    val isLoginUserChecking by viewModel.isLoginUserChecking.collectAsState()
    val loginUserCheckError by viewModel.loginUserCheckError.collectAsState()
    val isLoading = loginStatus is AuthViewModel.LoginResult.Loading
    val isLoginIdValid = ValidationUtils.isValidPhone(loginId)
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val googleSignInClient = remember(localContext) {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: localContext.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(localContext, gso)
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

    LaunchedEffect(loginId) {
        val digits = loginId.filter { it.isDigit() }
        if (digits.length == 10) {
            delay(500) // Debounce checking
            if (shouldCheckUser) {
                viewModel.checkUserExistsForLogin(digits)
            }
        } else {
            viewModel.clearLoginUserCheck()
            shouldCheckUser = false
        }
    }

    LaunchedEffect(loginStatus) {
        when (loginStatus) {
            is AuthViewModel.LoginResult.Success -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(localContext.getString(R.string.toast_welcome_back))
                }
                onLoginSuccess()
            }
            is AuthViewModel.LoginResult.Error -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> {
                isGoogleLogin = false
            }
        }
    }

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
                    // White card containing Logo - expanded and elevated
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.size(110.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_khanabook_logo),
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

            // Sheet Container - smoother rounded transition
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 36.dp)
                ) {
                    Text(
                        text = "Welcome back",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sign in to your restaurant account",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // PHONE NUMBER
                    Text(
                        text = "PHONE NUMBER",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.em
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OnboardingPhoneInput(
                        value = loginId,
                        onValueChange = {
                            loginId = it.take(10)
                            shouldCheckUser = true
                        },
                        placeholder = "Enter phone number",
                        isError = loginUserCheckError != null
                            || (loginId.isBlank() && loginStatus is AuthViewModel.LoginResult.Error),
                        trailingIcon = {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isLoginUserChecking -> CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = KbBrandSaffron
                                    )
                                    loginUserCheckError != null -> Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.content_desc_account_not_found),
                                        tint = KbError,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    loginId.length == 10 && !isLoginUserChecking -> Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = stringResource(R.string.content_desc_account_verified),
                                        tint = KbWhatsAppGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
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

                    if (loginUserCheckError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(loginUserCheckError!!, color = KbError, style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // PASSWORD
                    Text(
                        text = "PASSWORD",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.08.em
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OnboardingInputField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Enter password",
                        isError = password.isBlank() && loginStatus is AuthViewModel.LoginResult.Error,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
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
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showPassword) stringResource(R.string.content_desc_hide_password) else stringResource(R.string.content_desc_show_password),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { showForgotDialog = true }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_forgot_password),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val loginErrorMessage = (loginStatus as? AuthViewModel.LoginResult.Error)?.message
                    if (loginErrorMessage != null) {
                        Text(
                            text = loginErrorMessage,
                            color = KbError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

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
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Text(
                            text = "or continue with",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Google Outlined Button
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
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New to KhanaBook? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Box(
                            modifier = Modifier
                                .clickable { onSignUpClick() }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Sign Up",
                                color = KbBrandVioletBright,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
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
                    viewModel.clearLoginUserCheck()
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
    val localContext = LocalContext.current

    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) }
    var resendTimer by remember { mutableIntStateOf(0) }
    val isPhoneValid = ValidationUtils.isValidPhone(phone)

    val resetStatus by viewModel.resetPasswordStatus.collectAsState()
    val resetFieldErrors by viewModel.resetPasswordFieldErrors.collectAsState()
    val isResetLoading = resetStatus is AuthViewModel.ResetPasswordResult.Loading

    val isUserChecking by viewModel.isLoginUserChecking.collectAsState()
    val userExistsError by viewModel.loginUserCheckError.collectAsState()

    fun fieldError(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        resetFieldErrors[key]?.takeIf { it.isNotBlank() }
    }

    LaunchedEffect(phone) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length == 10) {
            viewModel.checkUserExistsForLogin(digits)
        } else {
            viewModel.clearLoginUserCheck()
        }
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
                onSuccess(localContext.getString(R.string.toast_password_reset))
                viewModel.login(phone, newPassword)
                onDismiss()
            }
            else -> {}
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0820))
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header height 310dp with gradient blobs
            KhanaBookPurpleBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
            ) {
                // Back Button Box (48dp target, visual 40dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onDismiss() }
                            .align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Envelope Badge Illustration
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
                            .size(110.dp)
                            .background(Color.Transparent, CircleShape)
                            .border(1.dp, Color(0xFFF97316).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(Color(0xFF3C1E10), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (step == 3) Icons.Default.Lock else Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Forgot Password?",
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
                        color = Color(0xFFB4ACE8), // lavender tint
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            // Surface sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    if (step == 1 || step == 2) {
                        Text(
                            text = "PHONE NUMBER",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val phoneError = phone.isNotEmpty() && !isPhoneValid || userExistsError != null || fieldError("phoneNumber", "loginId", "whatsappNumber") != null

                        OnboardingPhoneInput(
                            value = phone,
                            onValueChange = {
                                if (step == 1) {
                                    phone = it.filter { ch -> ch.isDigit() }.take(10)
                                }
                            },
                            readOnly = step == 2,
                            placeholder = "98765 43210",
                            isError = phoneError,
                            trailingIcon = {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isUserChecking -> CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = KbBrandSaffron
                                        )
                                        userExistsError != null -> Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = KbError,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        phone.length == 10 && !isUserChecking -> Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = KbWhatsAppGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        )

                        val err = fieldError("phoneNumber", "loginId", "whatsappNumber")
                        val errorText = when {
                            err != null -> err
                            userExistsError != null -> userExistsError
                            phone.isNotEmpty() && !isPhoneValid -> "Enter 10-digit number"
                            else -> null
                        }
                        if (errorText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(errorText, color = KbError, style = MaterialTheme.typography.labelSmall)
                        }

                        if (step == 2) {
                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "OTP CODE",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reusing visual OtpInputRow global composable from OtpVerificationScreen.kt
                            OtpInputRow(
                                otp = otp,
                                onOtpChange = { otp = it.filter(Char::isDigit).take(6) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val timerText = if (resendTimer > 0) {
                                val min = resendTimer / 60
                                val sec = resendTimer % 60
                                "Resend OTP in ${String.format("%d:%02d", min, sec)}"
                            } else {
                                "Resend OTP"
                            }

                            Text(
                                text = timerText,
                                color = if (resendTimer > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable(enabled = resendTimer == 0 && !isResetLoading) {
                                        viewModel.sendOtp(phone, "reset")
                                    }
                            )

                            val otpErr = fieldError("otp")
                            if (otpErr != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(otpErr, color = KbError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (step == 2) {
                            Button(
                                onClick = {
                                    if (otp.length == 6) {
                                        step = 3
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
                                enabled = otp.length == 6
                            ) {
                                Text("Verify & Continue", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        val isSendOtpEnabled = isPhoneValid && !isResetLoading && !isUserChecking && userExistsError == null
                        Button(
                            onClick = {
                                if (isSendOtpEnabled) {
                                    viewModel.sendOtp(phone, "reset")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSendOtpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSendOtpEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (isSendOtpEnabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            enabled = isSendOtpEnabled || (step == 2 && resendTimer == 0)
                        ) {
                            if (isResetLoading && step == 1) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = if (isSendOtpEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (step == 2) "Resend OTP" else "Send OTP",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else if (step == 3) {
                        Text(
                            text = "NEW PASSWORD",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val newPasswordError = fieldError("password")
                        OnboardingInputField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            placeholder = "Enter new password",
                            isError = newPasswordError != null,
                            visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showNewPassword = !showNewPassword },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (newPasswordError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(newPasswordError, color = KbError, style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "CONFIRM PASSWORD",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val passwordsMatch = confirmPassword.isEmpty() || newPassword == confirmPassword

                        OnboardingInputField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            placeholder = "Repeat new password",
                            isError = !passwordsMatch,
                            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showConfirmPassword = !showConfirmPassword },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!passwordsMatch) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Passwords do not match", color = KbError, style = MaterialTheme.typography.labelSmall)
                        }

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
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp),
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
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KbBrandVioletBright),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text("Back", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    val resetErrorMessage = (resetStatus as? AuthViewModel.ResetPasswordResult.Error)?.message
                    if (resetErrorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = resetErrorMessage, color = KbError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
