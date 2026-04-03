package com.khanabook.lite.pos.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel

@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val enteredPin by viewModel.enteredPin.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val showBiometric = remember { viewModel.hasBiometric(context) }

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

    // Biometric prompt
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val biometricPrompt = remember {
        BiometricPrompt(
            context as FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlock()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                override fun onAuthenticationFailed() {}
            }
        )
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock KhanaBook Lite")
            .setSubtitle("Use biometric to unlock")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    // Auto-show biometric on launch
    LaunchedEffect(Unit) {
        if (showBiometric) biometricPrompt.authenticate(promptInfo)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Spacer(modifier = Modifier.height(spacing.extraLarge))

            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(48.dp)
            )

            Text(
                "KhanaBook Lite",
                color = PrimaryGold,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(spacing.small))

            Text(
                "Enter your PIN",
                color = TextLight,
                style = MaterialTheme.typography.bodyLarge
            )

            // Dot indicators with shake
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
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
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = ErrorPink,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))

            // Numpad
            PinNumpad(
                onDigit = { viewModel.appendDigit(it) },
                onDelete = { viewModel.deleteDigit() },
                onBiometric = { if (showBiometric) biometricPrompt.authenticate(promptInfo) },
                showBiometric = showBiometric
            )

            Spacer(modifier = Modifier.height(spacing.extraLarge))
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
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.large),
                modifier = Modifier.align(Alignment.CenterHorizontally)
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
    val scale = remember { Animatable(1f) }
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

// ─── Mini PIN pad for dialogs (Settings) ────────────────────────────────────

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
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
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
