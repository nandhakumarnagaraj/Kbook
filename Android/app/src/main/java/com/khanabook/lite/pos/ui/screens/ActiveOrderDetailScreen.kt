@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocalPrintshop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.PaymentStatus
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSnackbarHost
import com.khanabook.lite.pos.ui.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.ui.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.ui.gesture.horizontalNavigationSwipe
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.RichEspresso
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.VegGreen
import com.khanabook.lite.pos.ui.viewmodel.ActiveOrderDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun ActiveOrderDetailScreen(
    onBack: () -> Unit,
    onAddItems: (Long) -> Unit,
    onCollectPayment: (Long) -> Unit,
    viewModel: ActiveOrderDetailViewModel = hiltViewModel()
) {
    val billWithItems by viewModel.bill.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
        snackbarHost = { KhanaBookSnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = billWithItems?.let(::activeOrderTitle) ?: "Active Order",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
                .horizontalNavigationSwipe(onSwipeRight = onBack)
        ) {
            val detail = billWithItems
            if (detail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryGold)
                }
            } else {
                val sentItems = detail.items.filter { it.sentToKot }
                val newItems = detail.items.filterNot { it.sentToKot }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(KhanaBookTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small)
                ) {
                    item {
                        ActiveOrderSummaryCard(detail)
                    }
                    item {
                        ActiveOrderActionGrid(
                            hasNewItems = newItems.isNotEmpty(),
                            onAddItems = { onAddItems(detail.bill.id) },
                            onUpdateKot = {
                                if (newItems.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("No new items to send") }
                                } else {
                                    viewModel.updateKot()
                                }
                            },
                            onPayment = { onCollectPayment(detail.bill.id) },
                            onPrintBill = viewModel::printBill,
                            onReprintKot = viewModel::reprintKot,
                            onCancel = { showCancelDialog = true }
                        )
                    }
                    item {
                        ItemSection(
                            title = "Already sent to kitchen",
                            items = sentItems,
                            emptyText = "No items sent to kitchen yet",
                            highlighted = false
                        )
                    }
                    item {
                        ItemSection(
                            title = "New items to send",
                            items = newItems,
                            emptyText = "No new items to send",
                            highlighted = true
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.bottomListPadding))
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBrown1.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryGold)
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = DarkBrown2,
            title = { Text("Cancel active order?", color = PrimaryGold) },
            text = {
                Text(
                    "Are you sure you want to cancel this active order?",
                    color = TextLight
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelOrder(onBack)
                    }
                ) {
                    Text("Cancel Order", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Order", color = TextGold)
                }
            }
        )
    }
}

@Composable
private fun ActiveOrderSummaryCard(detail: BillWithItems) {
    val bill = detail.bill
    val spacing = KhanaBookTheme.spacing
    val newCount = detail.items.count { !it.sentToKot }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.customerName?.takeIf { it.isNotBlank() } ?: orderTypeLabel(bill.orderType),
                        color = TextLight,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${bill.dailyOrderDisplay} - ${orderTypeLabel(bill.orderType)}",
                        color = TextGold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = CurrencyUtils.formatPrice(bill.totalAmount),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                KhanaStatusBadge(
                    text = if (newCount > 0) "KOT Pending" else "KOT Sent",
                    kind = if (newCount > 0) KhanaStatusKind.Warning else KhanaStatusKind.Success
                )
                KhanaStatusBadge(
                    text = if (bill.paymentStatus == PaymentStatus.SUCCESS.dbValue) "Paid" else "Unpaid",
                    kind = if (bill.paymentStatus == PaymentStatus.SUCCESS.dbValue) KhanaStatusKind.Success else KhanaStatusKind.Warning
                )
            }
            Text(
                text = "Created ${DateUtils.formatDisplayDate(bill.createdAt)} - Updated ${DateUtils.formatDisplayDate(bill.updatedAt)}",
                color = TextGold.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ActiveOrderActionGrid(
    hasNewItems: Boolean,
    onAddItems: () -> Unit,
    onUpdateKot: () -> Unit,
    onPayment: () -> Unit,
    onPrintBill: () -> Unit,
    onReprintKot: () -> Unit,
    onCancel: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onAddItems,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
            ) {
                Icon(Icons.Default.Add, null, tint = DarkBrown1, modifier = Modifier.size(18.dp))
                Text("Add Items", color = DarkBrown1)
            }
            Button(
                onClick = onUpdateKot,
                enabled = hasNewItems,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VegGreen)
            ) {
                Icon(Icons.Default.Restaurant, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Update KOT", color = Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPayment,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Icon(Icons.Default.Payments, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Payment", color = Color.White)
            }
            Button(
                onClick = onPrintBill,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
            ) {
                Icon(Icons.Default.Print, null, tint = DarkBrown1, modifier = Modifier.size(18.dp))
                Text("Print Bill", color = DarkBrown1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onReprintKot,
                modifier = Modifier.weight(1f).height(46.dp),
                border = BorderStroke(1.dp, BorderGold)
            ) {
                Icon(Icons.Default.LocalPrintshop, null, tint = PrimaryGold, modifier = Modifier.size(18.dp))
                Text("Reprint KOT", color = PrimaryGold)
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(46.dp),
                border = BorderStroke(1.dp, DangerRed)
            ) {
                Icon(Icons.Default.Cancel, null, tint = DangerRed, modifier = Modifier.size(18.dp))
                Text("Cancel Order", color = DangerRed)
            }
        }
    }
}

@Composable
private fun ItemSection(
    title: String,
    items: List<BillItemEntity>,
    emptyText: String,
    highlighted: Boolean
) {
    val spacing = KhanaBookTheme.spacing
    val bg = if (highlighted) PrimaryGold.copy(alpha = 0.12f) else CardBG
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.border(1.dp, PrimaryGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                text = title,
                color = if (highlighted) PrimaryGold else TextLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (items.isEmpty()) {
                Text(emptyText, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            } else {
                items.forEach { item ->
                    OrderItemLine(item)
                }
            }
        }
    }
}

@Composable
private fun OrderItemLine(item: BillItemEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.itemName,
                color = TextLight,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.variantName.isNullOrBlank() || !item.specialInstruction.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(item.variantName, item.specialInstruction?.takeIf { it.isNotBlank() }).joinToString(" - "),
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "${item.quantity} x ${CurrencyUtils.formatPrice(item.price)}",
            color = PrimaryGold,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun activeOrderTitle(detail: BillWithItems): String {
    val bill = detail.bill
    val name = bill.customerName?.takeIf { it.isNotBlank() }
    return when {
        bill.orderType.equals("takeaway", ignoreCase = true) -> "Takeaway Active Order"
        bill.orderType.equals("parcel", ignoreCase = true) -> "Online Order Active Order"
        bill.orderType.equals("online", ignoreCase = true) -> "Online Order Active Order"
        !name.isNullOrBlank() -> "$name Active Order"
        else -> "Table Active Order"
    }
}

private fun orderTypeLabel(value: String?): String {
    return when (value?.trim()?.lowercase()) {
        "takeaway", "take_away" -> "Takeaway"
        "parcel", "online", "online_order" -> "Online Order"
        "dine_in", "dine-in" -> "Dine-in"
        else -> "Dine-in"
    }
}
