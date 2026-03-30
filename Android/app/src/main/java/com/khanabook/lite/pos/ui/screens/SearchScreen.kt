@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.components.KhanaDatePickerField
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.SearchViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SearchScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    billingViewModel: BillingViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var lifetimeQuery by remember { mutableStateOf("") }
    var dailyId by remember { mutableStateOf("") }
    var dailyDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    val result by viewModel.searchResult.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        color = PrimaryGold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2)))
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
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

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = dailyId,
                        onValueChange = { 
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                dailyId = it
                            }
                        },
                        label = { Text("Daily Order ID", color = TextGold) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    KhanaDatePickerField(
                        label = "Select Date",
                        selectedDate = dailyDate,
                        onDateSelected = { dailyDate = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (dailyId.isNotBlank() && dailyDate.isNotBlank()) {
                                viewModel.searchByDailyId(dailyId, dailyDate)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = RoundedCornerShape(12.dp),
                        enabled = dailyId.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = DarkBrown1,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search Order", color = DarkBrown1, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedTextField(
                        value = lifetimeQuery,
                        onValueChange = { 
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                lifetimeQuery = it
                            } else {
                                android.widget.Toast.makeText(context, "Please enter a valid number", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Lifetime Order ID", color = TextGold) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            lifetimeQuery.toLongOrNull()?.let {
                                viewModel.searchByLifetimeId(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = RoundedCornerShape(12.dp),
                        enabled = lifetimeQuery.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = DarkBrown1,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search Order", color = DarkBrown1, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val currentResult = result
            if (currentResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp).wrapContentHeight()) {
                        // Header Section: Phone and Date in single line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, tint = TextGold, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    currentResult.bill.customerWhatsapp ?: "N/A",
                                    color = TextLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Text(" | ", color = BorderGold.copy(alpha = 0.3f))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, null, tint = TextGold, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    DateUtils.formatDisplay(currentResult.bill.createdAt),
                                    color = TextGold,
                                    fontSize = 11.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))

                            Surface(
                                color = if (currentResult.bill.paymentStatus == "success")
                                    SuccessGreen.copy(alpha = 0.1f)
                                else DangerRed.copy(alpha = 0.1f),
                                shape = CircleShape
                            ) {
                                Text(
                                    currentResult.bill.paymentStatus.uppercase(),
                                    color = if (currentResult.bill.paymentStatus == "success") SuccessGreen else DangerRed,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderGold.copy(alpha = 0.2f)
                        )

                        // Scrollable Items Section
                        if (currentResult.items.isEmpty()) {
                            Text(
                                text = "No items found in this order.",
                                color = TextLight.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false) // Allow it to take available space but not force it
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp) // Responsive limit for smaller screens
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(currentResult.items) { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${item.itemName} x${item.quantity}",
                                                color = TextLight,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                CurrencyUtils.formatPrice(item.itemTotal),
                                                color = TextLight,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderGold.copy(alpha = 0.2f)
                        )

                        // Fixed Footer Section
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Total Amount",
                                    color = PrimaryGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "${CurrencyUtils.formatPrice(currentResult.bill.totalAmount)}",
                                    color = PrimaryGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (title.contains("Status", ignoreCase = true)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text("Payment Mode", color = TextGold, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                PaymentMode.fromDbValue(currentResult.bill.paymentMode).displayLabel,
                                                color = TextLight,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Order ID", color = TextGold, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        Text(
                                            "#${currentResult.bill.dailyOrderDisplay.split("-").last()}",
                                            color = TextLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            result?.let { shareBillOnWhatsApp(context, it, profile) }
                                        },
                                        enabled = currentResult.items.isNotEmpty(),
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            result?.let {
                                                directPrint(context, it, profile, billingViewModel.printerManager)
                                            }
                                        },
                                        enabled = currentResult.items.isNotEmpty(),
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                                        border = BorderStroke(1.5.dp, PrimaryGold)
                                    ) {
                                        Icon(Icons.Default.Print, null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Print Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (hasSearched) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = null,
                            tint = TextGold.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (hasSearched) "No Order Found" else "Search for an order to view details",
                            color = TextGold.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
