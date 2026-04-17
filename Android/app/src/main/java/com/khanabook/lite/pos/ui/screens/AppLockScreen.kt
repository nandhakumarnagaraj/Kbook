package com.khanabook.lite.pos.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel

@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val enteredPin by viewModel.enteredPin.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val showBiometric = remember { viewModel.hasBiometric(context) }
    val scrollState = rememberScrollState()
    var showForgotPinDialog by remember { mutableStateOf(false) }

    // Shake animation on error
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            repeat(4) {
                shakeOffset.animateTo(8f, tween(50))
                shakeOffset.animateTo(-8f, tween(50))
            }
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    // Auto-verify at 4 digits
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) viewModel.verifyPin(onSuccess = onUnlock)
    }

    // Biometric prompt setup
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val biometricPrompt = remember(context) {
        if (context is FragmentActivity) {
            BiometricPrompt(
                context,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlock()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                    override fun onAuthenticationFailed() {}
                }
            )
        } else null
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock KhanaBook Lite")
            .setSubtitle("Use biometric or device lock")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    // Auto-show biometric on launch
    LaunchedEffect(showBiometric, biometricPrompt) {
        if (showBiometric && biometricPrompt != null) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(spacing.extraLarge))

            Icon(
                Icons.Default.Lock,
                contentDescription = "App locked",
                tint = PrimaryGold,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            Text(
                "KhanaBook Lite",
                color = PrimaryGold,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(spacing.small))

            Text(
                "Enter your PIN to continue",
                color = TextLight,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            // Dot indicators with shake
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = if (filled) PrimaryGold else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = 2.dp,
                                color = if (filled) PrimaryGold else BorderGold,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Error message
            Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                errorMessage?.let { message ->
                    Text(
                        message,
                        color = ErrorPink,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Numpad
            PinNumpad(
                onDigit = { viewModel.appendDigit(it) },
                onDelete = { viewModel.deleteDigit() },
                onBiometric = { 
                    if (showBiometric && biometricPrompt != null) {
                        biometricPrompt.authenticate(promptInfo)
                    }
                },
                showBiometric = showBiometric && biometricPrompt != null
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            TextButton(onClick = { showForgotPinDialog = true }) {
                Text(
                    "Forgot PIN?",
                    color = PrimaryGold.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }

        if (showForgotPinDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPinDialog = false },
                containerColor = DarkBrown2,
                title = { Text("Forgot PIN?", color = PrimaryGold, style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text(
                        "Your PIN will be cleared and you'll be logged out. Log back in to set a new PIN from Settings.",
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showForgotPinDialog = false
                        viewModel.forgotPin { onNavigateToLogin() }
                    }) {
                        Text("Log Out & Reset", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPinDialog = false }) {
                        Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    }
}

@Composable
fun PinNumpad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
    showBiometric: Boolean
) {
    val spacing = KhanaBookTheme.spacing
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "del")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.large)
            ) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> PinKey(
                            onClick = onBiometric,
                            enabled = showBiometric
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Biometric",
                                tint = if (showBiometric) PrimaryGold else Color.Transparent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        "del" -> PinKey(onClick = onDelete) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = TextLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> PinKey(onClick = { onDigit(key) }) {
                            Text(
                                key,
                                color = TextLight,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinKey(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        onClick = {
            if (enabled) onClick()
        },
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = if (enabled) 0.08f else 0.0f),
        enabled = enabled
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
fun InlinePinEntry(
    pin: String,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    errorMessage: String? = null
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            color = if (filled) PrimaryGold else Color.Transparent,
                            shape = CircleShape
                        )
                        .border(1.5.dp, if (filled) PrimaryGold else BorderGold, CircleShape)
                )
            }
        }
        if (errorMessage != null) {
            Text(errorMessage, color = ErrorPink, style = MaterialTheme.typography.labelSmall)
        }
        PinNumpad(
            onDigit = onDigit,
            onDelete = onDelete,
            onBiometric = {},
            showBiometric = false
        )
    }
}
