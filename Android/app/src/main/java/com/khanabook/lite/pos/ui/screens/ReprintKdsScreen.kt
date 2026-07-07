@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.components.KhanaDatePickerField
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReprintKdsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = hiltViewModel(),
    billingViewModel: BillingViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var invoiceQuery by remember { mutableStateOf("") }
    var dailyId by remember { mutableStateOf("") }
    var showDailyIdError by remember { mutableStateOf(false) }
    var showInvoiceError by remember { mutableStateOf(false) }
    var dailyDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    val vmResult by searchViewModel.searchResult.collectAsStateWithLifecycle()
    val vmHasSearched by searchViewModel.hasSearched.collectAsStateWithLifecycle()
    val isKitchenPrinting by billingViewModel.kitchenPrinting.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    fun doSearch() {
        keyboardController?.hide()
        scope.launch {
            if (selectedTab == 0) {
                val q = dailyId.trim()
                if (q.isEmpty()) { showDailyIdError = true; return@launch }
                showDailyIdError = false
                val found = searchViewModel.searchByDailyId(q, dailyDate)
                searchViewModel.publishSearchResult(found)
            } else {
                val q = invoiceQuery.trim()
                if (q.isEmpty()) { showInvoiceError = true; return@launch }
                showInvoiceError = false
                val found = searchViewModel.searchByInvoiceNo(q.toLongOrNull() ?: 0L)
                searchViewModel.publishSearchResult(found)
            }
        }
    }

    LaunchedEffect(vmResult, vmHasSearched) {
        if (vmHasSearched && vmResult == null) {
            KhanaToast.show("No pending KDS found", ToastKind.Warning)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBrown1.copy(alpha = 0.6f))
                    .padding(horizontal = spacing.medium, vertical = spacing.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                    }
                    Text("Reprint KDS", color = PrimaryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box {}
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PrimaryGold,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; searchViewModel.clearSearch(); dailyId = "" },
                    text = { Text("Order No", style = MaterialTheme.typography.labelLarge) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; searchViewModel.clearSearch(); invoiceQuery = "" },
                    text = { Text("Invoice No", style = MaterialTheme.typography.labelLarge) }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Search Fields
            if (selectedTab == 0) {
                Box(modifier = Modifier.padding(horizontal = spacing.medium)) {
                    KhanaDatePickerField(
                        label = "Select Date",
                        selectedDate = dailyDate,
                        onDateSelected = { dailyDate = it }
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

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
                    label = { Text("Order No", color = TextGold) },
                    isError = showDailyIdError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { doSearch() },
                        onDone = { doSearch() }
                    ),
                    colors = outlinedSearchFieldColors()
                )
                if (showDailyIdError) {
                    Text("Please enter numbers only", color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = spacing.medium))
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                KhanaPrimaryButton(
                    text = "Search Order",
                    onClick = {
                        keyboardController?.hide()
                        doSearch()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    leadingIcon = Icons.Default.Search,
                    enabled = dailyId.isNotEmpty()
                )
            } else {
                OutlinedTextField(
                    value = invoiceQuery,
                    onValueChange = {
                        val invoiceNo = it.removePrefix("INV").removePrefix("inv")
                        if (invoiceNo.isEmpty() || invoiceNo.all { char -> char.isDigit() }) {
                            invoiceQuery = invoiceNo
                            showInvoiceError = false
                        } else {
                            showInvoiceError = true
                        }
                    },
                    label = { Text("Invoice No", color = TextGold) },
                    prefix = { Text("INV") },
                    isError = showInvoiceError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { doSearch() },
                        onDone = { doSearch() }
                    ),
                    colors = outlinedSearchFieldColors()
                )
                if (showInvoiceError) {
                    Text("Please enter numbers only", color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = spacing.medium))
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                KhanaPrimaryButton(
                    text = "Search Order",
                    onClick = {
                        keyboardController?.hide()
                        doSearch()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    leadingIcon = Icons.Default.Search,
                    enabled = invoiceQuery.isNotEmpty()
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Result
            vmResult?.let { billWithItems ->
                KdsBillCard(
                    billWithItems = billWithItems,
                    isPrinting = isKitchenPrinting,
                    onPrint = {
                        billingViewModel.printKitchenTicket(it)
                        scope.launch {
                            delay(800)
                            searchViewModel.clearSearch()
                            invoiceQuery = ""
                            dailyId = ""
                        }
                    }
                )
            } ?: run {
                if (vmHasSearched) {
                    KhanaEmptyState(
                        title = "No pending KDS found",
                        message = "Try another order or invoice number.",
                        icon = Icons.Default.SearchOff,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.huge)
                    )
                }
            }
        }
    }
}

@Composable
private fun KdsBillCard(
    billWithItems: BillWithItems,
    isPrinting: Boolean,
    onPrint: (BillWithItems) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val bill = billWithItems.bill
    val isCancelled = bill.orderStatus == "cancelled"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Order #${bill.dailyOrderDisplay.split("-").last()}",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "INV${bill.lifetimeOrderId}",
                            color = TextLight,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    KhanaStatusBadge(
                        text = if (isCancelled) "CANCELLED" else "KDS PENDING",
                        kind = if (isCancelled) KhanaStatusKind.Danger else KhanaStatusKind.Warning,
                        filled = false
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.small),
                    color = BorderGold.copy(alpha = 0.2f)
                )
                billWithItems.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.itemName}",
                            color = TextLight,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.small),
                    color = BorderGold.copy(alpha = 0.2f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Text(CurrencyUtils.formatPrice(bill.totalAmount), color = PrimaryGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (isCancelled) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        text = "This order has been cancelled, due to that it can't be printed.",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = spacing.small)
                    )
                }
                Spacer(modifier = Modifier.height(spacing.medium))
                KhanaPrimaryButton(
                    text = if (isPrinting) "Printing KDS..." else "Reprint KDS",
                    onClick = { if (!isCancelled && !isPrinting) onPrint(billWithItems) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCancelled && !isPrinting,
                    leadingIcon = Icons.Default.Print
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
