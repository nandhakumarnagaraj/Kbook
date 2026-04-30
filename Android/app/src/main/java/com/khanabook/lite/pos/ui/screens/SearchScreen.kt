@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.ui.components.KhanaDatePickerField
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.SearchViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.khanabook.lite.pos.R

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
    var showDailyIdError by remember { mutableStateOf(false) }
    var showLifetimeQueryError by remember { mutableStateOf(false) }
    var dailyDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    val result by viewModel.searchResult.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    var pendingKdsBills by remember { mutableStateOf<List<BillWithItems>>(emptyList()) }
    var loadingKds by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            loadingKds = true
            pendingKdsBills = runCatching { viewModel.getBillsWithPendingKds() }.getOrDefault(emptyList())
            loadingKds = false
        }
    }

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
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
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
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
                .imePadding()
                .padding(horizontal = spacing.medium, vertical = spacing.medium)
        ) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
              Column(modifier = Modifier.wrapContentHeight()) {
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
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("KDS Pending", style = MaterialTheme.typography.labelLarge) }
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = dailyId,
                        onValueChange = {
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                dailyId = it
                                showDailyIdError = false
                            } else {
                                showDailyIdError = true
                            }
                        },
                        label = { Text("Order No") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedSearchFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = showDailyIdError,
                        supportingText = {
                            if (showDailyIdError) {
                                Text(context.getString(R.string.field_numbers_only))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(spacing.small))

                    KhanaDatePickerField(
                        label = "Select Date",
                        selectedDate = dailyDate,
                        onDateSelected = { dailyDate = it }
                    )

                    Spacer(modifier = Modifier.height(spacing.medium))

                    Button(
                        onClick = {
                            if (dailyId.isNotBlank() && dailyDate.isNotBlank()) {
                                viewModel.searchByDailyId(dailyId, dailyDate)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = RoundedCornerShape(12.dp),
                        enabled = dailyId.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = DarkBrown1,
                            modifier = Modifier.size(iconSize.small)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Search Order", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                } else {
                    OutlinedTextField(
                        value = lifetimeQuery,
                        onValueChange = { 
                            val invoiceNo = it.removePrefix("INV").removePrefix("inv")
                            if (invoiceNo.isEmpty() || invoiceNo.all { char -> char.isDigit() }) {
                                lifetimeQuery = invoiceNo
                                showLifetimeQueryError = false
                            } else {
                                showLifetimeQueryError = true
                            }
                        },
                        label = { Text("Invoice No") },
                        prefix = { Text("INV") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedSearchFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = showLifetimeQueryError,
                        supportingText = {
                            if (showLifetimeQueryError) {
                                Text(context.getString(R.string.field_numbers_only))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Button(
                        onClick = {
                            lifetimeQuery.toLongOrNull()?.let {
                                viewModel.searchByLifetimeId(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = RoundedCornerShape(12.dp),
                        enabled = lifetimeQuery.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = DarkBrown1,
                            modifier = Modifier.size(iconSize.small)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Search Order", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    } // closes Button
              } // closes else
              } // closes header Column
             } // closes AnimatedVisibility header

            Spacer(modifier = Modifier.height(spacing.large))

            if (selectedTab == 2) {
                if (loadingKds) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryGold)
                    }
                } else if (pendingKdsBills.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text("All KDS orders printed", color = TextGold.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                        contentPadding = PaddingValues(bottom = spacing.bottomListPadding)
                    ) {
                        items(pendingKdsBills) { billWithItems ->
                            KdsPendingCard(
                                billWithItems = billWithItems,
                                profile = profile,
                                onPrint = { billingViewModel.printReceipt(it) }
                            )
                        }
                    }
                }
            } else {

            AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec) {
              Column {
                val currentResult = result
                if (currentResult != null) {
                    KhanaBookCard(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = CardBG),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium).wrapContentHeight()) {
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, tint = TextGold, modifier = Modifier.size(iconSize.xsmall))
                                Spacer(modifier = Modifier.width(spacing.extraSmall))
                                Text(
                                    currentResult.bill.customerWhatsapp ?: "N/A",
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Text(" | ", color = BorderGold.copy(alpha = 0.3f))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, null, tint = TextGold, modifier = Modifier.size(iconSize.xsmall))
                                Spacer(modifier = Modifier.width(spacing.extraSmall))
                                Text(
                                    DateUtils.formatDisplay(currentResult.bill.createdAt),
                                    color = TextGold,
                                    style = MaterialTheme.typography.labelSmall
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
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = spacing.medium),
                            color = BorderGold.copy(alpha = 0.2f)
                        )

                        
                        if (currentResult.items.isEmpty()) {
                            Text(
                                text = "No items found in this order.",
                                color = TextLight.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = spacing.medium).align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(spacing.small)
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
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                CurrencyUtils.formatPrice(item.itemTotal),
                                                color = TextLight,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = spacing.medium),
                            color = BorderGold.copy(alpha = 0.2f)
                        )

                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Total Amount",
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    CurrencyUtils.formatPrice(currentResult.bill.totalAmount),
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }

                            Spacer(modifier = Modifier.height(spacing.medium))

                            if (title.contains("Status", ignoreCase = true)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                                ) {
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text("Payment Mode", color = TextGold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                                        Surface(
                                            color = DarkBrown1.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                PaymentMode.fromDbValue(currentResult.bill.paymentMode).displayLabel,
                                                color = TextLight,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Order ID", color = TextGold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                                        Text(
                                            "#${currentResult.bill.dailyOrderDisplay.split("-").last()}",
                                            color = TextLight,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                                ) {
                                    Button(
                                        onClick = {
                                            result?.let {
                                                if (it.bill.serverId == null) {
                                                    sendInvoiceViaSms(context, it, profile)
                                                } else {
                                                    shareInvoiceViaWhatsAppLink(context, it, profile)
                                                }
                                            }
                                        },
                                        enabled = currentResult.items.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(iconSize.small))
                                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                                        Text("Share Invoice", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }

                                    Button(
                                        onClick = {
                                            result?.let { billingViewModel.printReceipt(it) }
                                        },
                                        enabled = currentResult.items.isNotEmpty() && currentResult.bill.orderStatus != "cancelled",
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Receipt, null, tint = DarkBrown1, modifier = Modifier.size(iconSize.small))
                                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                                        Text("Print Receipt", color = DarkBrown1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }

                            }
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
                                imageVector = if (hasSearched) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = null,
                                tint = TextGold.copy(alpha = 0.3f),
                                modifier = Modifier.size(KhanaBookTheme.iconSize.xxlarge)
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text(
                                if (hasSearched) "No Order Found" else "Search for an order to view details",
                                color = TextGold.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
               } // end inner Column
             } // end AnimatedVisibility body
             } // end else (selectedTab != 2)
        }
    }
}

@Composable
private fun KdsPendingCard(
    billWithItems: BillWithItems,
    profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?,
    onPrint: (BillWithItems) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val bill = billWithItems.bill
    com.khanabook.lite.pos.ui.theme.KhanaBookCard(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.khanabook.lite.pos.ui.theme.CardBG),
        shape = com.khanabook.lite.pos.ui.theme.RoundedCornerShape(12.dp)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(spacing.medium)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(
                        text = "Order #${bill.dailyOrderDisplay.split("-").last()}",
                        color = com.khanabook.lite.pos.ui.theme.PrimaryGold,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    androidx.compose.material3.Text(
                        text = "INV${bill.lifetimeOrderId}",
                        color = com.khanabook.lite.pos.ui.theme.TextLight,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                androidx.compose.material3.Surface(
                    color = com.khanabook.lite.pos.ui.theme.DangerRed.copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    androidx.compose.material3.Text(
                        text = "KDS PENDING",
                        color = com.khanabook.lite.pos.ui.theme.DangerRed,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = spacing.small),
                color = com.khanabook.lite.pos.ui.theme.BorderGold.copy(alpha = 0.2f)
            )
            androidx.compose.material3.Text(
                text = billWithItems.items.joinToString(", ") { "${it.quantity}x ${it.itemName}" },
                color = com.khanabook.lite.pos.ui.theme.TextLight,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(spacing.small))
            androidx.compose.material3.Button(
                onClick = { onPrint(billWithItems) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.khanabook.lite.pos.ui.theme.PrimaryGold),
                shape = com.khanabook.lite.pos.ui.theme.RoundedCornerShape(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Print,
                    null,
                    tint = com.khanabook.lite.pos.ui.theme.DarkBrown1,
                    modifier = Modifier.size(com.khanabook.lite.pos.ui.theme.KhanaBookTheme.iconSize.xsmall)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
                androidx.compose.material3.Text(
                    "Reprint",
                    color = com.khanabook.lite.pos.ui.theme.DarkBrown1,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                )
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
