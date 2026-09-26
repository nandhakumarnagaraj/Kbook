@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.printing.ui
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*

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
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.getInvoiceNumberDisplay
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.components.KhanaDatePickerField
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.navigation.horizontalNavigationSwipe
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.billing.viewmodel.BillingViewModel
import com.khanabook.lite.pos.feature.billing.viewmodel.SearchViewModel
import com.khanabook.lite.pos.feature.printing.ui.printFeedbackKind
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReprintKdsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = hiltViewModel(),
    billingViewModel: BillingViewModel = hiltViewModel(),
    settingsViewModel: com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel = hiltViewModel()
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
    // GST toggle drives invoice terminology — same rule as Orders table and
    // printed receipts.
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val invoiceLabel = if (profile?.gstEnabled == true) "Tax Invoice No" else "Invoice No"
    val vmHasSearched by searchViewModel.hasSearched.collectAsStateWithLifecycle()
    val isKitchenPrinting by billingViewModel.kitchenPrinting.collectAsStateWithLifecycle()
    val billingError by billingViewModel.error.collectAsStateWithLifecycle()
    val printStatus by billingViewModel.printStatus.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val layout = KhanaBookTheme.layout
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
                val found = searchViewModel.searchByInvoiceNumber(q)
                searchViewModel.publishSearchResult(found)
            }
        }
    }

    LaunchedEffect(billingError) {
        billingError?.let { message ->
            KhanaToast.show(message, ToastKind.Error)
            billingViewModel.clearError()
        }
    }

    LaunchedEffect(printStatus) {
        printStatus?.let { message ->
            val kind = printFeedbackKind(message)
            KhanaToast.show(message, kind)
            billingViewModel.clearPrintStatus()
            if (kind == ToastKind.Success) {
                searchViewModel.clearSearch()
                invoiceQuery = ""
                dailyId = ""
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
            .horizontalNavigationSwipe(onSwipeRight = onBack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                .align(Alignment.TopCenter)
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
                    Text("Reprint KOT", color = PrimaryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box {}
                }
            }

            // All content below the header shares one horizontal padding so the
            // tab underline, fields and result card align — same rhythm as
            // Find Bill / Call Customer.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium)
            ) {
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
                    text = { Text(invoiceLabel, style = MaterialTheme.typography.labelLarge) }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Search Fields
            if (selectedTab == 0) {
                KhanaDatePickerField(
                    label = "Select Date",
                    selectedDate = dailyDate,
                    onDateSelected = { dailyDate = it }
                )

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
                    label = { Text("Order No") },
                    isError = showDailyIdError,
                    supportingText = {
                        if (showDailyIdError) {
                            Text("Please enter numbers only")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.height(spacing.medium))

                KhanaPrimaryButton(
                    text = "Search Order",
                    onClick = {
                        keyboardController?.hide()
                        doSearch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Search,
                    enabled = dailyId.isNotEmpty()
                )
            } else {
                OutlinedTextField(
                    value = invoiceQuery,
                    onValueChange = {
                        invoiceQuery = it.trim()
                        showInvoiceError = false
                    },
                    label = { Text(invoiceLabel) },
                    placeholder = { Text("e.g. 26A1-000042 or A01") },
                    isError = showInvoiceError,
                    supportingText = {
                        if (showInvoiceError) {
                            Text("Enter an invoice number")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { doSearch() },
                        onDone = { doSearch() }
                    ),
                    colors = outlinedSearchFieldColors()
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                KhanaPrimaryButton(
                    text = "Search Order",
                    onClick = {
                        keyboardController?.hide()
                        doSearch()
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                    }
                )
            } ?: run {
                KhanaEmptyState(
                    title = if (vmHasSearched) "No Order Found" else "Search for an order to reprint",
                    message = if (vmHasSearched) {
                        "Check the order or invoice number and try again."
                    } else {
                        "Use Order No with date, or $invoiceLabel for older bills."
                    },
                    icon = if (vmHasSearched) Icons.Default.SearchOff else Icons.Default.Print,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.huge)
                )
            }
            } // end shared-padding content column
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
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
                            text = bill.getInvoiceNumberDisplay(),
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
                    text = if (isPrinting) "Printing KOT..." else "Reprint KOT",
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
