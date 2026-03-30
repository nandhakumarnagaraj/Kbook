@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SignUpScreen(
        onSignUpSuccess: () -> Unit,
        onLoginClick: () -> Unit = {},
        viewModel: AuthViewModel = hiltViewModel()
) {
    var shopName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val phoneFocusRequester = remember { FocusRequester() }
    val otpFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    
    val isNameValid = ValidationUtils.isValidName(shopName)
    val isPhoneValid = ValidationUtils.isValidPhone(phoneNumber)
    val isPasswordValid = ValidationUtils.isValidPassword(newPassword)
    val passwordsMatch = newPassword == confirmPassword && newPassword.isNotEmpty()
    @Suppress("UNUSED_VARIABLE")
    val isOtpValid = ValidationUtils.isValidOtp(otp) 

    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableIntStateOf(120) }
    val signUpStatus by viewModel.signUpStatus.collectAsState()
    val loginStatus by viewModel.loginStatus.collectAsState()
    val isUserChecking by viewModel.isUserChecking.collectAsState()
    val userExistsError by viewModel.userExistsError.collectAsState()

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
                otpTimer = 120
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                otpFocusRequester.requestFocus()
                snackbarHostState.showSnackbar(
                        "OTP Sent to your WhatsApp!",
                        duration = SnackbarDuration.Long
                )
            }
            is AuthViewModel.SignUpResult.Error -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                snackbarHostState.showSnackbar(status.message)
            }
            else -> {}
        }
    }

    // Clear user check error if phone changes
    LaunchedEffect(phoneNumber) {
        if (phoneNumber.length < 10) {
            viewModel.clearUserCheck()
        }
    }

    // Handle the auto-login success separately if needed, 
    // though MainActivity observes currentUser.
    LaunchedEffect(loginStatus) {
        if (loginStatus is AuthViewModel.LoginResult.Success) {
            onSignUpSuccess()
            viewModel.resetSignUpStatus()
        }
    }

    fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    LaunchedEffect(otpSent, otpTimer) {
        if (otpSent && otpTimer > 0) {
            delay(1000)
            otpTimer--
        }
    }

    Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Brush.verticalGradient(listOf(DarkBrown1, Color.Black)))
                                .padding(padding)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .imePadding()
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 24.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                        painter = painterResource(id = R.drawable.khanabook_logo),
                        contentDescription = "KhanaBook Lite logo",
                        modifier = Modifier.size(130.dp).padding(bottom = 8.dp),
                        contentScale = ContentScale.Fit
                )

                Text(
                        text = "Sign Up",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGold
                )

                Text(
                        text =
                                "Create your account to start managing\nbilling with KhanaBook Lite.",
                        fontSize = 10.sp,
                        color = TextLight.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 25.dp)
                )

                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            placeholder = {
                                Text("Shop Name", color = TextGold.copy(alpha = 0.5f))
                            },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Business,
                                        contentDescription = null,
                                        tint = PrimaryGold
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = outlinedTextFieldColors(),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { phoneFocusRequester.requestFocus() }
                            ),
                            isError = shopName.isNotEmpty() && !isNameValid,
                            supportingText = {
                                if (shopName.isNotEmpty() && !isNameValid)
                                        Text("Shop name too short", color = DangerRed)
                            }
                    )

                    
                    OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = {
                                val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                                phoneNumber = filtered
                                if (filtered.length == 10) {
                                    viewModel.checkUserExists(filtered)
                                }
                            },
                            placeholder = {
                                Text("WhatsApp Number", color = TextGold.copy(alpha = 0.5f))
                            },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = VegGreen
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = if (otpSent) ImeAction.Next else ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { if (otpSent) otpFocusRequester.requestFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(phoneFocusRequester),
                            shape = RoundedCornerShape(24.dp),
                            colors = outlinedTextFieldColors(),
                            singleLine = true,
                            enabled = !isLoading,
                            isError = (phoneNumber.isNotEmpty() && !isPhoneValid) || userExistsError != null,
                            supportingText = {
                                if (userExistsError != null) {
                                    Text(userExistsError!!, color = DangerRed)
                                } else if (phoneNumber.isNotEmpty() && !isPhoneValid) {
                                    Text("Enter 10-digit number", color = DangerRed)
                                }
                            },
                            trailingIcon = {
                                if (isUserChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = PrimaryGold
                                    )
                                } else if (!otpSent || otpTimer == 0) {
                                    Button(
                                            onClick = {
                                                if (isPhoneValid && userExistsError == null) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.sendOtp(phoneNumber)
                                                }
                                            },
                                            modifier = Modifier.padding(end = 4.dp).height(36.dp),
                                            colors =
                                                    ButtonDefaults.buttonColors(
                                                            containerColor = PrimaryGold
                                                    ),
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            enabled = isPhoneValid && !isLoading && !isUserChecking && userExistsError == null
                                    ) {
                                        Text(
                                                "Send OTP",
                                                color = DarkBrown1,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                    )

                    
                    if (otpSent) {
                        OutlinedTextField(
                                value = otp,
                                onValueChange = {
                                    if (it.length <= 6) {
                                        val filtered = it.filter { ch -> ch.isDigit() }
                                        otp = filtered
                                        if (filtered.length == 6) {
                                            passwordFocusRequester.requestFocus()
                                        }
                                    }
                                },
                                placeholder = {
                                    Text("Enter OTP", color = TextGold.copy(alpha = 0.5f))
                                },
                                leadingIcon = {
                                    Icon(
                                            Icons.Default.Dialpad,
                                            contentDescription = null,
                                            tint = PrimaryGold
                                    )
                                },
                                keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next
                                        ),
                                keyboardActions = KeyboardActions(
                                    onNext = { passwordFocusRequester.requestFocus() }
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(otpFocusRequester),
                                shape = RoundedCornerShape(24.dp),
                                colors = outlinedTextFieldColors(),
                                singleLine = true,
                                enabled = !isLoading,
                                isError = false,
                                trailingIcon = {
                                    if (otpTimer > 0) {
                                        Text(
                                                text = formatTime(otpTimer),
                                                color = TextLight,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(end = 16.dp)
                                        )
                                    }
                                }
                        )
                    }

                    
                    OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            placeholder = {
                                Text("Create New Password", color = TextGold.copy(alpha = 0.5f))
                            },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = PrimaryGold
                                )
                            },
                            trailingIcon = {
                                Icon(
                                        imageVector =
                                                if (showNewPassword) Icons.Default.Visibility
                                                else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = PrimaryGold,
                                        modifier =
                                                Modifier.clickable(enabled = !isLoading) {
                                                            showNewPassword = !showNewPassword
                                                        }
                                                        .padding(end = 8.dp)
                                )
                            },
                            visualTransformation =
                                    if (showNewPassword) VisualTransformation.None
                                    else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                            shape = RoundedCornerShape(24.dp),
                            colors = outlinedTextFieldColors(),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { confirmPasswordFocusRequester.requestFocus() }
                            ),
                            isError = newPassword.isNotEmpty() && !isPasswordValid,
                            supportingText = {
                                if (newPassword.isNotEmpty() && !isPasswordValid)
                                        Text(
                                                "Min 8 chars, uppercase, digit & special character",
                                                color = DangerRed
                                        )
                            }
                    )

                    
                    OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            placeholder = {
                                Text("Confirm New Password", color = TextGold.copy(alpha = 0.5f))
                            },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = PrimaryGold
                                )
                            },
                            trailingIcon = {
                                Icon(
                                        imageVector =
                                                if (showConfirmPassword) Icons.Default.Visibility
                                                else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = PrimaryGold,
                                        modifier =
                                                Modifier.clickable(enabled = !isLoading) {
                                                            showConfirmPassword =
                                                                    !showConfirmPassword
                                                        }
                                                        .padding(end = 8.dp)
                                )
                            },
                            visualTransformation =
                                    if (showConfirmPassword) VisualTransformation.None
                                    else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().focusRequester(confirmPasswordFocusRequester),
                            shape = RoundedCornerShape(24.dp),
                            colors = outlinedTextFieldColors(),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val isFormValidAction =
                                        isNameValid && isPhoneValid && isPasswordValid &&
                                                passwordsMatch && otp.length == 6 && !isLoading && userExistsError == null
                                    if (isFormValidAction) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.signUp(shopName, phoneNumber, otp, newPassword)
                                    }
                                    focusManager.clearFocus()
                                }
                            ),
                            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                            supportingText = {
                                if (confirmPassword.isNotEmpty() && !passwordsMatch)
                                        Text("Passwords do not match", color = DangerRed)
                            }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                
                val isFormValid =
                        isNameValid &&
                                isPhoneValid &&
                                isPasswordValid &&
                                passwordsMatch &&
                                otp.length == 6 && !isLoading && userExistsError == null

                Button(
                        onClick = {
                            if (isFormValid) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.signUp(shopName, phoneNumber, otp, newPassword)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFormValid) PrimaryGold else Color.Gray,
                            contentColor = DarkBrown1
                        ),
                        shape = RoundedCornerShape(28.dp),
                        enabled = isFormValid
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = DarkBrown1,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(
                                "Sign Up",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                        )
                    }
                }


                Spacer(modifier = Modifier.height(24.dp))

                Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Already have an account? ", color = TextLight, fontSize = 14.sp)
                    Text(
                            text = "Log In",
                            color = PrimaryGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isLoading) { onLoginClick() }
                    )
                }
            }

            // Full-screen Loading Overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {}, // This consumes all touch events
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PrimaryGold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (signUpStatus is AuthViewModel.SignUpResult.Loading) "Creating Account..." else "Logging in...",
                                color = TextLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = DarkBrown1,
                focusedContainerColor = DarkBrown2,
                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                focusedBorderColor = PrimaryGold,
                cursorColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
        )
