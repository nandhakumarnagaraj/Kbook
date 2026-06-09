@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.R

// ── OTP Input Row (reusable 6-digit boxes) ───────────────────────────────────
@Composable
fun OtpInputRow(
    otp: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 48.dp,
    boxHeight: Dp = 56.dp,
    spacing: Dp = 12.dp
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
                modifier = Modifier.width(boxSize).height(boxHeight),
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
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
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
                // Bottom line indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isFilled) Color(0xFFF97316) else Color(0xFFE2E8F0))
                )
            }
        }
    }
}

// ── Midnight Purple Header (reusable for OTP + Sync + KYC) ───────────────────
@Composable
fun PurpleGradientHeader(
    title: String,
    subtitle: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
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
            // White elevated logo card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.size(100.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_khanabook_logo),
                        contentDescription = "KhanaBook logo",
                        modifier = Modifier.size(68.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                textAlign = TextAlign.Center
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFA78BFA),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── White Bottom Sheet Form Container (reusable) ─────────────────────────────
@Composable
fun PurpleFormSheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = Color.White
    ) {
        content()
    }
}

// ── Full OTP Verification Screen ─────────────────────────────────────────────
@Composable
fun OtpVerificationHeader(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF97316)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_khanabook_logo),
                contentDescription = "KhanaBook",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "KhanaBook",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OtpVerificationBody(
    phoneNumber: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onChangeNumberClick: () -> Unit,
    onResendClick: () -> Unit,
    resendTimerSeconds: Int,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter OTP",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We've sent a 6-digit code to $phoneNumber",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OtpInputRow(
            otp = otp,
            onOtpChange = onOtpChange
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color(0xFFDC2626),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerifyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF97316),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFCBD5E1),
                disabledContentColor = Color.White.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = otp.length == 6 && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "Verify OTP",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val resendText = if (resendTimerSeconds > 0) {
            val min = resendTimerSeconds / 60
            val sec = resendTimerSeconds % 60
            "Resend code in ${String.format("%02d:%02d", min, sec)}"
        } else {
            "Resend code"
        }
        Text(
            text = resendText,
            color = if (resendTimerSeconds > 0) Color(0xFF94A3B8) else Color(0xFFF97316),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clickable(enabled = resendTimerSeconds == 0 && !isLoading) { onResendClick() }
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Change number?",
            color = Color(0xFF7C3AED),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clickable(enabled = !isLoading) { onChangeNumberClick() }
                .padding(8.dp)
        )
    }
}

@Composable
fun OtpVerificationScreen(
    phoneNumber: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onChangeNumberClick: () -> Unit,
    onResendClick: () -> Unit,
    resendTimerSeconds: Int,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F081D))
    ) {
        // Midnight purple gradient header
        PurpleGradientHeader(
            title = "Verify OTP",
            subtitle = "Enter the code sent to $phoneNumber"
        )

        // White bottom sheet form
        PurpleFormSheet(modifier = Modifier.weight(1f)) {
            OtpVerificationBody(
                phoneNumber = phoneNumber,
                otp = otp,
                onOtpChange = onOtpChange,
                onVerifyClick = onVerifyClick,
                onChangeNumberClick = onChangeNumberClick,
                onResendClick = onResendClick,
                resendTimerSeconds = resendTimerSeconds,
                isLoading = isLoading,
                errorMessage = errorMessage,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
