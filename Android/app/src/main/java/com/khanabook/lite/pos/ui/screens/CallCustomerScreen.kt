package com.khanabook.lite.pos.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.khanabook.lite.pos.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
            modifier = modifier,
            topBar = {
                CenterAlignedTopAppBar(
                        title = {
                            Text("Call Customer", color = PrimaryGold, fontWeight = FontWeight.Bold)
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
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2)))
                                .imePadding()
                                .navigationBarsPadding()
                                .padding(24.dp)
        ) {
            TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkBrown1,
                    contentColor = PrimaryGold
            ) {
                Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Daily ID") }
                )
                Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Lifetime ID") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                        label = { Text("Daily Order ID", color = TextGold) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextLight,
                                        unfocusedTextColor = TextLight,
                                        focusedBorderColor = PrimaryGold,
                                        unfocusedBorderColor = BorderGold
                                ),
                        singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    KhanaDatePickerField(
                            label = "Select Date",
                            selectedDate = dailyDate,
                            onDateSelected = { dailyDate = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkBrown1)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Customer", color = DarkBrown1, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedTextField(
                        value = lifetimeId,
                        onValueChange = { 
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                lifetimeId = it
                            } else {
                                android.widget.Toast.makeText(context, "Please enter a valid number", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Lifetime Order ID", color = TextGold) },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextLight,
                                        unfocusedTextColor = TextLight,
                                        focusedBorderColor = PrimaryGold,
                                        unfocusedBorderColor = BorderGold
                                ),
                        singleLine = true,
                        keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType =
                                                androidx.compose.ui.text.input.KeyboardType.Number
                                )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                        onClick = {
                            lifetimeId.toLongOrNull()?.let { viewModel.searchByLifetimeId(it) }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        enabled = lifetimeId.isNotEmpty()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkBrown1)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Customer", color = DarkBrown1, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val currentResult = result
            if (currentResult != null) {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border =
                                androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        BorderGold.copy(alpha = 0.5f)
                                )
                ) {
                    Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                                modifier = Modifier.size(80.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = PrimaryGold.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val name = currentResult.bill.customerName
                                val initial = if (name.isNullOrBlank() || name == "Walking Customer") "G" else name.take(1).uppercase()
                                Text(
                                        text = initial,
                                        color = PrimaryGold,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                                text = currentResult.bill.customerName?.takeIf { it != "Walking Customer" } ?: "Guest",
                                color = TextLight,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                        )

                        Text(
                                text =
                                        "Phone: ${currentResult.bill.customerWhatsapp ?: "Not Provided"}",
                                color = TextGold,
                                fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                                text =
                                        "Last Order: #${currentResult.bill.lifetimeOrderId} on ${DateUtils.formatDateOnly(currentResult.bill.createdAt)}",
                                color = TextGold.copy(alpha = 0.7f),
                                fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Call Customer", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                                Icons.Default.Call,
                                contentDescription = null,
                                tint = TextGold.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                "Enter order details to find customer",
                                color = TextGold.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
