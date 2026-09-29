package com.khanabook.lite.pos.feature.auth.ui
import com.khanabook.lite.pos.core.theme.*

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.auth.viewmodel.AppLockViewModel

// The previous scale (64dp icon, headlineLarge title, 20dp dots, 72dp keys) put the
// content at ~676dp, which overflowed most phones and forced the column to scroll.
// These values land the whole screen at ~542dp so it fits without scrolling.
// PIN_KEY_SIZE stays well clear of the 48dp minimum touch target.
private val LOCK_ICON_SIZE = 40.dp
private val PIN_DOT_SIZE = 14.dp
private val PIN_KEY_SIZE = 60.dp
private val ERROR_SLOT_HEIGHT = 28.dp

@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val enteredPin by viewModel.enteredPin.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val showBiometric = remember { viewModel.hasBiometric(context) }
    val allowedAuthenticators = remember { viewModel.allowedAuthenticators(context) }
    val scrollState = rememberScrollState()

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
        runCatching {
            if (context is FragmentActivity) {
                BiometricPrompt(
                    context,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onUnlock()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            android.util.Log.e("AppLockScreen", "Biometric error: $errorCode - $errString")
                        }
                        override fun onAuthenticationFailed() {}
                    }
                )
            } else null
        }.onFailure {
            android.util.Log.e("AppLockScreen", "Failed to create BiometricPrompt", it)
        }.getOrNull()
    }

    val promptInfo = remember {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock KhanaBook Lite")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setSubtitle("Use fingerprint, face, or screen lock")
                .setAllowedAuthenticators(allowedAuthenticators)
        } else {
            builder.setSubtitle("Use biometric to unlock")
                .setNegativeButtonText("Use PIN")
        }
        builder.build()
    }

    // Auto-show biometric on launch
    val effectiveShowBiometric = showBiometric && biometricPrompt != null
    LaunchedEffect(effectiveShowBiometric) {
        if (effectiveShowBiometric) {
            runCatching {
                biometricPrompt!!.authenticate(promptInfo)
            }.onFailure {
                android.util.Log.e("AppLockScreen", "Biometric authenticate failed", it)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))),
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
            Spacer(modifier = Modifier.height(spacing.large))

            Icon(
                Icons.Default.Lock,
                contentDescription = "App locked",
                tint = PrimaryGold,
                modifier = Modifier.size(LOCK_ICON_SIZE)
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            Text(
                "KhanaBook Lite",
                color = PrimaryGold,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            Text(
                "Enter your PIN to continue",
                color = TextLight,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(spacing.large))

            // Dot indicators with shake
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(PIN_DOT_SIZE)
                            .background(
                                color = if (filled) PrimaryGold else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (filled) PrimaryGold else BorderGold,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Error message
            Box(modifier = Modifier.height(ERROR_SLOT_HEIGHT), contentAlignment = Alignment.Center) {
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
                    if (effectiveShowBiometric) {
                        runCatching {
                            biometricPrompt!!.authenticate(promptInfo)
                        }.onFailure {
                            android.util.Log.e("AppLockScreen", "Biometric authenticate failed", it)
                        }
                    }
                },
                showBiometric = effectiveShowBiometric
            )

            Spacer(modifier = Modifier.height(spacing.large))
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
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "del")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.smallMedium),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> PinKey(
                            onClick = onBiometric,
                            enabled = showBiometric
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Biometric or screen lock",
                                tint = if (showBiometric) PrimaryGold else Color.Transparent,
                                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                            )
                        }
                        "del" -> PinKey(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDelete()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = TextLight,
                                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                            )
                        }
                        else -> PinKey(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigit(key)
                        }) {
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
        modifier = Modifier.size(PIN_KEY_SIZE),
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
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)) {
            repeat(4) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(KhanaBookTheme.iconSize.xsmall)
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
