package com.khanabook.lite.ui.screens.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true, backgroundColor = 0xFF0A0603)
@Composable
private fun LoginScreenPreview() {
    LoginScreen()
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0603)
@Composable
private fun LoginScreenLoadingPreview() {
    LoginScreen(isLoading = true)
}

private val GoldPrimary = Color(0xFFD4A417)
private val GoldSecondary = Color(0xFFE8C84A)
private val Background = Color(0xFF0A0603)
private val Surface = Color(0xFF1A0D02)
private val InputFill = Color(0x0AFFFFFF)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0x59FFFFFF)

private val BrandFontFamily = FontFamily.Serif
private val BodyFontFamily = FontFamily.SansSerif

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onLoginClick: (email: String, password: String) -> Unit = { _, _ -> }
) {
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.ime)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.3f))

        Text(
            text = "KhanaBook",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BrandFontFamily,
            color = GoldPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Restaurant POS",
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            fontFamily = BodyFontFamily,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(56.dp))

        Text(
            text = "Welcome Back",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BrandFontFamily,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Sign in to continue",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = BodyFontFamily,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        LoginTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            placeholder = "Enter your email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "Enter your password",
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Forgot Password?",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = BodyFontFamily,
            color = GoldPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        LoginButton(
            isLoading = isLoading,
            onClick = {
                focusManager.clearFocus()
                onLoginClick(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        Text(
            text = "Don't have an account? Register",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = BodyFontFamily,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = BodyFontFamily,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = BodyFontFamily,
                    color = TextMuted
                )
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { },
                onNext = { }
            ),
            visualTransformation = visualTransformation,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = TextMuted,
                disabledBorderColor = TextMuted.copy(alpha = 0.5f),
                focusedContainerColor = InputFill,
                unfocusedContainerColor = InputFill,
                disabledContainerColor = InputFill.copy(alpha = 0.5f),
                cursorColor = GoldPrimary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextMuted
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LoginButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shimmerBrush = Brush(
        colors = listOf(
            GoldPrimary.copy(alpha = 0.7f),
            GoldSecondary.copy(alpha = 0.9f),
            GoldPrimary.copy(alpha = 0.7f)
        ),
        start = Offset(0f, 0f),
        end = Offset(x = 1000f, y = 0f)
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val animatedShimmerBrush = Brush.linearGradient(
        colors = listOf(
            GoldPrimary.copy(alpha = 0.5f),
            GoldSecondary.copy(alpha = 0.8f),
            GoldPrimary.copy(alpha = 0.5f)
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 500f, 0f)
    )

    Box(modifier = modifier) {
        Button(
            onClick = { if (!isLoading) onClick() },
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (isLoading) shimmerBrush else animatedShimmerBrush,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Background,
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(24.dp)
                )
            } else {
                Text(
                    text = "Sign In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BodyFontFamily,
                    color = Background
                )
            }
        }
    }
}
