@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.components.KhanaDatePickerField
import com.khanabook.lite.pos.domain.manager.TrustedExternalAppReturn
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.SearchViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.khanabook.lite.pos.R

@Composable
fun CallCustomerScreen(
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        viewModel: SearchViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var lifetimeId by remember { mutableStateOf("") }
    var dailyId by remember { mutableStateOf("") }
    var showDailyIdError by remember { mutableStateOf(false) }
    var showLifetimeIdError by remember { mutableStateOf(false) }
    var dailyDate by remember {
        mutableStateOf(
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
        )
    }
    val result by viewModel.searchResult.collectAsState()
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    // Standard staggered entry animation
    var headerVisible by remember { mutableStateOf(false) }
    var bodyVisible by remember { mutableStateOf(false) }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))
    LaunchedEffect(Unit) {
        headerVisible = true
        kotlinx.coroutines.delay(80)
        bodyVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgGradient)
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.kbHeaderGradient)
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KhanaBookBackButton(onClick = onBack)
                        Text(
                            text = "Call Customer",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        if (result != null) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.White
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier =
                        Modifier.padding(padding)
                                .consumeWindowInsets(padding)
                                .fillMaxSize()
                                .imePadding()
                                .padding(spacing.large)
            ) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
              Column {
            TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.kbBgCard,
                    contentColor = MaterialTheme.kbSecondary,
                    divider = {}
            ) {
                Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Order No", style = MaterialTheme.typography.labelLarge) }
                )
                Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Invoice No", style = MaterialTheme.typography.labelLarge) }
                )
            }

            Spacer(modifier = Modifier.height(spacing.large))

            if (selectedTab == 0) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    KhanaDatePickerField(
                            label = "Select Date",
                            selectedDate = dailyDate,
                            onDateSelected = { dailyDate = it }
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                KhanaBookInputField(
                        value = dailyId,
                        onValueChange = {
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                dailyId = it
                                showDailyIdError = false
                            } else {
                                showDailyIdError = true
                            }
                        },
                        label = "Order No",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        isError = showDailyIdError,
                        supportingText = {
                            if (showDailyIdError) {
                                Text(context.getString(R.string.field_numbers_only))
                            }
                        }
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                PrimaryButton(
                        text = "Search Customer",
                        onClick = {
                            if (dailyId.isNotBlank() && dailyDate.isNotBlank()) {
                                viewModel.searchByDailyId(dailyId, dailyDate)
                            }
                        },
                        leadingIcon = Icons.Default.Search,
                        enabled = dailyId.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                )
            } else {
                KhanaBookInputField(
                        value = lifetimeId,
                        onValueChange = { 
                            val invoiceNo = it.removePrefix("INV").removePrefix("inv")
                            if (invoiceNo.isEmpty() || invoiceNo.all { char -> char.isDigit() }) {
                                lifetimeId = invoiceNo
                                showLifetimeIdError = false
                            } else {
                                showLifetimeIdError = true
                            }
                        },
                        label = "Invoice No",
                        leadingIcon = { Text("INV", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = showLifetimeIdError,
                        supportingText = {
                            if (showLifetimeIdError) {
                                Text(context.getString(R.string.field_numbers_only))
                            }
                        },
                        keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType =
                                                androidx.compose.ui.text.input.KeyboardType.Number
                                )
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                PrimaryButton(
                        text = "Search Customer",
                        onClick = {
                            lifetimeId.toLongOrNull()?.let { viewModel.searchByLifetimeId(it) }
                        },
                        leadingIcon = Icons.Default.Search,
                        enabled = lifetimeId.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                )
              } // closes else
             } // closes Column
            } // closes AnimatedVisibility header

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec) {
              Column {
                val currentResult = result
                if (currentResult != null) {
                KhanaBookCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                        shape = KbShape.Large
                ) {
                    Column(
                            modifier = Modifier.padding(spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                                modifier = Modifier.size(iconSize.heroCircle),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.kbSecondary.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val name = currentResult.bill.customerName
                                val initial = if (name.isNullOrBlank() || name == "Walking Customer") "G" else name.take(1).uppercase()
                                Text(
                                        text = initial,
                                        color = MaterialTheme.kbSecondary,
                                        style = MaterialTheme.typography.headlineLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))

                        Text(
                                text = currentResult.bill.customerName?.takeIf { it != "Walking Customer" } ?: "Guest",
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                                text = "Phone: ${currentResult.bill.customerWhatsapp ?: "Not Provided"}",
                                color = MaterialTheme.kbSecondary,
                                style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(spacing.small))

                        Text(
                                text = "Last Order: #${currentResult.bill.dailyOrderDisplay.split("-").last()} on ${DateUtils.formatDateOnly(currentResult.bill.createdAt)}",
                                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(modifier = Modifier.height(spacing.large))

                        Button(
                                onClick = {
                                    currentResult.bill.customerWhatsapp?.let { phone ->
                                        val intent =
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        TrustedExternalAppReturn.mark(context)
                                        context.startActivity(intent)
                                    }
                                },
                                enabled = currentResult.bill.customerWhatsapp != null,
                                modifier = Modifier.fillMaxWidth().height(KbButtonSize.HeightLarge),
                                colors = ButtonDefaults.buttonColors(containerColor = KbSuccess),
                                shape = KbShape.Medium
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text("Call Customer", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                } else {
                    Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = spacing.bottomListPadding),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                    Icons.Default.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text(
                                    "Enter order details to find customer",
                                    color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
              } // end Column
            } // end AnimatedVisibility body
        }
    }
}
}


