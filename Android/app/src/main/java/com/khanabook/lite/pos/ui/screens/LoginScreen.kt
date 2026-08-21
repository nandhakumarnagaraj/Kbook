@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.khanabook.lite.pos.ui.screens.auth.ForgotPasswordDialog
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel

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
                    viewModel.setGoogleLoginError("Google Sign-In failed. Please try again or use phone number login.")
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(loginStatus) {
        when (val s = loginStatus) {
            is AuthViewModel.LoginResult.Loading -> {}
            is AuthViewModel.LoginResult.Success -> {
                isGoogleLogin = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    KhanaToast.show(context.getString(R.string.toast_welcome_back), ToastKind.Success)
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

    val layout = KhanaBookTheme.layout

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))),
        contentAlignment = Alignment.Center
    ) {
        Column(
                modifier =
                        Modifier.fillMaxWidth(layout.dialogWidthFraction)
                                .widthIn(max = layout.dialogMaxWidth)
                                .verticalScroll(rememberScrollState())
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = spacing.large, vertical = spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
        ) {
            KhanaBookLogo(
                    modifier = Modifier.padding(bottom = spacing.medium)
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
                    shape = KhanaRadii.xl,
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
                                modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge).clickable { showPassword = !showPassword }.padding(KhanaBookTheme.spacing.smallMedium)
                        )
                    },
                    visualTransformation =
                            if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                    shape = KhanaRadii.xl,
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
                    modifier = Modifier.align(Alignment.End).heightIn(min = KhanaBookTheme.spacing.buttonHeightCompact).clickable { showForgotDialog = true }.padding(end = spacing.medium),
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
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightLarge),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (isLoginEnabled) PrimaryGold else TextMuted,
                                    contentColor = DarkBrown1
                            ),
                    shape = KhanaRadii.pill,
                    enabled = isLoginEnabled
            ) {
                if (isLoading && !isGoogleLogin) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize.medium),
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
                                Modifier.size(KhanaBookTheme.iconSize.avatar)
                                        .border(1.dp, BorderGold, CircleShape)
                                        .clickable(enabled = !isLoading) {
                                            isGoogleLogin = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            googleSignInClient.signOut().addOnCompleteListener {
                                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                            }
                                        },
                        shape = CircleShape,
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isLoading && isGoogleLogin) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(iconSize.medium),
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
                    },
                    onSuccess = { message ->
                        coroutineScope.launch { KhanaToast.show(message, ToastKind.Success) }
                    }
            )
        }
    }
}
