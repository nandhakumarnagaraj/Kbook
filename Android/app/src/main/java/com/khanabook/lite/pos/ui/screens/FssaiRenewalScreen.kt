package com.khanabook.lite.pos.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.data.repository.EasebuzzSdkPaymentRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "FssaiRenewalScreen"

enum class FssaiScreenState {
    INPUT,       // Show selector and license input (if not prefilled)
    INITIATING,  // Call createFssaiOrder on server
    PAYING,      // Wait for Easebuzz payment
    SUCCESS,     // Show payment completed
    FAILED       // Show error message
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FssaiRenewalScreen(
    fssaiNo: String,
    paymentRepository: EasebuzzPaymentRepository,
    sdkPaymentRepository: EasebuzzSdkPaymentRepository,
    sessionManager: SessionManager,
    onBack: () -> Unit,
    onPaymentComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing

    var screenState by remember { mutableStateOf(FssaiScreenState.INPUT) }
    var inputFssai by remember { mutableStateOf(fssaiNo) }
    var selectedYears by remember { mutableIntStateOf(1) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var paymentUrl by remember { mutableStateOf<String?>(null) }
    var currentTxnid by remember { mutableStateOf<String?>(null) }
    var payMode by remember { mutableStateOf("test") }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var sdkLaunched by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            val paymentResult = data.getStringExtra("result")
            val paymentResponse = data.getStringExtra("payment_response")
            Log.i(TAG, "Easebuzz FSSAI SDK callback txnid=$currentTxnid result=$paymentResult response=$paymentResponse")
            if (paymentResult == "payment_successfull") {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                screenState = FssaiScreenState.SUCCESS
            } else if (paymentResult == "user_cancelled" || paymentResult == "back_pressed") {
                errorMessage = "Payment cancelled by user"
                screenState = FssaiScreenState.FAILED
            } else {
                errorMessage = paymentResult ?: "Payment failed"
                screenState = FssaiScreenState.FAILED
            }
        } else {
            Log.w(TAG, "Easebuzz FSSAI SDK callback missing data txnid=$currentTxnid")
            errorMessage = "No response received from payment gateway"
            screenState = FssaiScreenState.FAILED
        }
    }

    fun launchPaymentFlow() {
        val fssaiNumber = inputFssai.trim()
        if (fssaiNumber.length != 14 || !fssaiNumber.all { it.isDigit() }) {
            errorMessage = "Please enter a valid 14-digit FSSAI License Number"
            screenState = FssaiScreenState.FAILED
            return
        }

        screenState = FssaiScreenState.INITIATING
        errorMessage = null

        scope.launch {
            try {
                val restaurantId = sessionManager.getRestaurantId()
                Log.i(TAG, "Creating FSSAI renewal order: restaurantId=$restaurantId, years=$selectedYears, fssai=$fssaiNumber")
                val response = paymentRepository.createFssaiOrder(
                    restaurantId = restaurantId,
                    years = selectedYears,
                    fssaiNumber = fssaiNumber
                )

                val status = response["status"] as? String ?: "failure"
                if (status == "success") {
                    accessToken = response["access_token"] as? String
                    paymentUrl = response["payment_url"] as? String
                    currentTxnid = response["txnid"] as? String
                    payMode = (response["pay_mode"] as? String) ?: "test"

                    val token = accessToken
                    if (!token.isNullOrBlank()) {
                        screenState = FssaiScreenState.PAYING
                        sdkLaunched = true
                        try {
                            Log.i(TAG, "Launching native Easebuzz SDK for FSSAI renewal. txnid=$currentTxnid")
                            val intent = sdkPaymentRepository.createSdkIntent(token, payMode, context)
                            launcher.launch(intent)
                        } catch (e: Exception) {
                            val url = paymentUrl
                            if (!url.isNullOrBlank()) {
                                Log.w(TAG, "Easebuzz SDK fallback to Custom Tab: ${e.message}")
                                sdkPaymentRepository.launchFallback(context, url)
                            } else {
                                errorMessage = "Payment gateway SDK initialization failed and fallback URL not available."
                                screenState = FssaiScreenState.FAILED
                            }
                        }
                    } else {
                        val url = paymentUrl
                        if (!url.isNullOrBlank()) {
                            screenState = FssaiScreenState.PAYING
                            sdkLaunched = true
                            Log.i(TAG, "Launching Custom Tab fallback (no access token). URL=$url")
                            sdkPaymentRepository.launchFallback(context, url)
                        } else {
                            errorMessage = "Invalid response from server: payment parameters missing."
                            screenState = FssaiScreenState.FAILED
                        }
                    }
                } else {
                    errorMessage = (response["error"] as? String) ?: "Payment order creation failed."
                    screenState = FssaiScreenState.FAILED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create FSSAI payment order: ${e.message}", e)
                errorMessage = e.message ?: "Connection error. Please try again."
                screenState = FssaiScreenState.FAILED
            }
        }
    }

    KhanaBookScreenScaffold(
        title = "FSSAI Renewal",
        onBack = if (screenState == FssaiScreenState.INPUT || screenState == FssaiScreenState.FAILED || screenState == FssaiScreenState.SUCCESS) onBack else null,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.large)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            when (screenState) {
                FssaiScreenState.INPUT -> {
                    Text(
                        text = "Renew Your Food License Compliance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.kbTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    KhanaBookCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            Text(
                                text = "License Information",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.kbTextPrimary
                            )

                            KhanaBookInputField(
                                value = inputFssai,
                                onValueChange = { if (it.length <= 14) inputFssai = it },
                                label = "14-Digit FSSAI License Number",
                                readOnly = fssaiNo.isNotBlank(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                placeholder = "Enter 14-digit FSSAI number",
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Dropdown selector for years
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ExposedDropdownMenuBox(
                                    expanded = dropdownExpanded,
                                    onExpandedChange = { dropdownExpanded = it }
                                ) {
                                    KhanaBookInputField(
                                        value = "$selectedYears ${if (selectedYears == 1) "Year" else "Years"}",
                                        onValueChange = {},
                                        label = "Renewal Period",
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.kbSecondary
                                            )
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false }
                                    ) {
                                        (1..5).forEach { yr ->
                                            DropdownMenuItem(
                                                text = { Text("$yr ${if (yr == 1) "Year" else "Years"}") },
                                                onClick = {
                                                    selectedYears = yr
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pricing details Card
                    val renewalPrice = selectedYears * 1000.00
                    KhanaBookCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = KbBrandSaffron.copy(alpha = 0.10f))
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.medium),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Total Renewal Fee",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.kbTextSecondary
                            )
                            Text(
                                text = "₹${String.format("%,.2f", renewalPrice)}",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = KbBrandSaffron,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.kbTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Secure online transaction via Easebuzz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.kbTextSecondary
                                )
                            }
                        }
                    }

                    PrimaryButton(
                        text = "Proceed to Pay",
                        onClick = { launchPaymentFlow() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                FssaiScreenState.INITIATING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier.padding(vertical = 48.dp)
                    ) {
                        CircularProgressIndicator(
                            color = KbBrandSaffron,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Initiating payment gateway session...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kbTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                FssaiScreenState.PAYING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier.padding(vertical = 48.dp)
                    ) {
                        CircularProgressIndicator(
                            color = KbBrandSaffron,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Completing transaction...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kbTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Text(
                            text = "If you paid via web browser, your food license compliance status will update automatically on the server upon verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.kbTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = spacing.medium)
                        )
                        Spacer(modifier = Modifier.height(spacing.large))
                        PrimaryButton(
                            text = "Verify & Return",
                            onClick = { onPaymentComplete() }
                        )
                    }
                }

                FssaiScreenState.SUCCESS -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = KbSuccess,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Renewal Fee Paid Successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KbSuccess,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Your food license renewal has been registered. The compliance team will process the update and update the license validity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kbTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = spacing.medium)
                        )
                        Spacer(modifier = Modifier.height(spacing.large))
                        PrimaryButton(
                            text = "Back to Home",
                            onClick = { onPaymentComplete() }
                        )
                    }
                }

                FssaiScreenState.FAILED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Error",
                            tint = KbError,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Transaction Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KbError,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = errorMessage ?: "An unknown error occurred during transaction setup.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kbTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = spacing.medium)
                        )
                        Spacer(modifier = Modifier.height(spacing.large))
                        PrimaryButton(
                            text = "Try Again",
                            onClick = { screenState = FssaiScreenState.INPUT }
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        SecondaryButton(
                            text = "Go Back",
                            onClick = { onBack() }
                        )
                    }
                }
            }
        }
    }
}
