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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.components.KhanaDatePickerField
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.SearchViewModel

@Composable
fun CallCustomerScreen(
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        viewModel: SearchViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var lifetimeId by remember { mutableStateOf("") }
    var dailyId by remember { mutableStateOf("") }
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

    Scaffold(
            modifier = modifier,
            topBar = {
                CenterAlignedTopAppBar(
                        title = {
                            Text("Call Customer", color = PrimaryGold, style = MaterialTheme.typography.titleLarge)
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = PrimaryGold
                                )
                            }
                        },
                        actions = {
                            if (result != null) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = PrimaryGold
                                    )
                                }
                            }
                        },
                        colors =
                                TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = DarkBrown1
                                )
                )
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.padding(padding)
                                .consumeWindowInsets(padding)
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2)))
                                .imePadding()
                                .padding(spacing.large)
        ) {
            TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkBrown1,
                    contentColor = PrimaryGold,
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
                OutlinedTextField(
                        value = dailyId,
                        onValueChange = { 
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                dailyId = it
                            } else {
                                android.widget.Toast.makeText(context, "Please enter a valid number", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Order No") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = outlinedSearchFieldColors(),
                        singleLine = true
                )
                Spacer(modifier = Modifier.height(spacing.medium))

                Box(modifier = Modifier.fillMaxWidth()) {
                    KhanaDatePickerField(
                            label = "Select Date",
                            selectedDate = dailyDate,
                            onDateSelected = { dailyDate = it }
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                Button(
                        onClick = {
                            if (dailyId.isNotBlank() && dailyDate.isNotBlank()) {
                                viewModel.searchByDailyId(dailyId, dailyDate)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        enabled = dailyId.isNotEmpty()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkBrown1, modifier = Modifier.size(iconSize.small))
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Search Customer", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            } else {
                OutlinedTextField(
                        value = lifetimeId,
                        onValueChange = { 
                            val invoiceNo = it.removePrefix("INV").removePrefix("inv")
                            if (invoiceNo.isEmpty() || invoiceNo.all { char -> char.isDigit() }) {
                                lifetimeId = invoiceNo
                            } else {
                                android.widget.Toast.makeText(context, "Please enter a valid number", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Invoice No") },
                        prefix = { Text("INV") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedSearchFieldColors(),
                        singleLine = true,
                        keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType =
                                                androidx.compose.ui.text.input.KeyboardType.Number
                                )
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Button(
                        onClick = {
                            lifetimeId.toLongOrNull()?.let { viewModel.searchByLifetimeId(it) }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        enabled = lifetimeId.isNotEmpty()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkBrown1, modifier = Modifier.size(iconSize.small))
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Search Customer", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            val currentResult = result
            if (currentResult != null) {
                KhanaBookCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(
                            modifier = Modifier.padding(spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                                modifier = Modifier.size(iconSize.heroCircle),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = PrimaryGold.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val name = currentResult.bill.customerName
                                val initial = if (name.isNullOrBlank() || name == "Walking Customer") "G" else name.take(1).uppercase()
                                Text(
                                        text = initial,
                                        color = PrimaryGold,
                                        style = MaterialTheme.typography.headlineLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))

                        Text(
                                text = currentResult.bill.customerName?.takeIf { it != "Walking Customer" } ?: "Guest",
                                color = TextLight,
                                style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                                text = "Phone: ${currentResult.bill.customerWhatsapp ?: "Not Provided"}",
                                color = TextGold,
                                style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(spacing.small))

                        Text(
                                text = "Last Order: #${currentResult.bill.dailyOrderDisplay.split("-").last()} on ${DateUtils.formatDateOnly(currentResult.bill.createdAt)}",
                                color = TextGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(modifier = Modifier.height(spacing.large))

                        Button(
                                onClick = {
                                    currentResult.bill.customerWhatsapp?.let { phone ->
                                        val intent =
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        context.startActivity(intent)
                                    }
                                },
                                enabled = currentResult.bill.customerWhatsapp != null,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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
                                tint = TextGold.copy(alpha = 0.3f),
                                modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
                        )
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Text(
                                "Enter order details to find customer",
                                color = TextGold.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedSearchFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextLight,
        unfocusedTextColor = TextLight,
        focusedBorderColor = PrimaryGold,
        unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
        focusedLabelColor = PrimaryGold,
        unfocusedLabelColor = TextGold
    )
