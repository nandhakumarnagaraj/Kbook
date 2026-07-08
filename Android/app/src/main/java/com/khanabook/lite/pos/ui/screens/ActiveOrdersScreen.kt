@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.LocalPrintshop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.khanabook.lite.pos.ui.theme.WarningYellow
import com.khanabook.lite.pos.ui.viewmodel.ActiveOrderSummaryRow
import com.khanabook.lite.pos.ui.viewmodel.ActiveOrdersViewModel
import kotlinx.coroutines.launch

@Composable
fun ActiveOrdersScreen(
    onBack: () -> Unit,
    onOpenActiveOrder: (Long) -> Unit,
    onCollectPayment: (Long) -> Unit,
    viewModel: ActiveOrdersViewModel = hiltViewModel()
) {
    val activeRows by viewModel.rows.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
        snackbarHost = { KhanaBookSnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Active Orders",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                        )
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
            if (activeRows.isEmpty()) {
                EmptyActiveOrders()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items(activeRows, key = { it.bill.id }) { row ->
                        ActiveOrderCard(
                            row = row,
                            onOpen = { onOpenActiveOrder(row.bill.id) },
                            onPayment = { onCollectPayment(row.bill.id) },
                            onPrint = { viewModel.printBill(row.bill.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(spacing.bottomListPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyActiveOrders() {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            tint = TextGold.copy(alpha = 0.28f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            text = "No active orders right now.",
            color = TextGold.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Saved table orders will appear here.",
            color = TextGold.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ActiveOrderCard(
    row: ActiveOrderSummaryRow,
    onOpen: () -> Unit,
    onPayment: () -> Unit,
    onPrint: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val bill = row.bill
    val title = when (bill.orderType.trim().lowercase()) {
        "takeaway", "take_away" -> {
            val name = bill.customerName?.takeIf { it.isNotBlank() }
            if (name != null) "Takeaway - $name" else "Takeaway"
        }
        "parcel", "online", "online_order" -> {
            val name = bill.customerName?.takeIf { it.isNotBlank() }
            if (name != null) "Online - $name" else "Online Order"
        }
        else -> { // Dine-in
            val name = bill.customerName?.takeIf { it.isNotBlank() }
            if (name == null || name.lowercase() == "table") {
                "Table"
            } else if (name.startsWith("table", ignoreCase = true)) {
                name
            } else {
                "Table $name"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.26f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ageMinutes = (System.currentTimeMillis() - bill.updatedAt) / 60_000L
            val barColor = when {
                ageMinutes < 5 -> null
                ageMinutes < 15 -> WarningYellow
                else -> DangerRed
            }
            if (barColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(barColor)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Text(
                            text = title,
                            color = TextLight,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        KhanaStatusBadge(
                            text = "ACTIVE",
                            kind = KhanaStatusKind.Info,
                            filled = false
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    val itemsLabel = if (row.itemCount == 1 && !row.singleItemName.isNullOrBlank()) {
                        row.singleItemName
                    } else {
                        "${row.itemCount} item${if (row.itemCount == 1) "" else "s"}"
                    }
                    Text(
                        text = "${orderTypeLabel(bill.orderType)} - $itemsLabel",
                        color = TextGold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${bill.dailyOrderDisplay} - ${CurrencyUtils.formatPrice(bill.totalAmount)}",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Updated ${DateUtils.getRelativeTimeString(bill.updatedAt)}",
                        color = TextGold.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        KhanaStatusBadge(
                            text = if (row.hasNewKitchenItems) "KOT Pending" else "KOT Sent",
                            kind = if (row.hasNewKitchenItems) KhanaStatusKind.Warning else KhanaStatusKind.Success
                        )
                        KhanaStatusBadge(text = "Unpaid", kind = KhanaStatusKind.Warning)
                    }
                    Spacer(modifier = Modifier.height(spacing.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onOpen,
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                        ) {
                            Text("Open", color = DarkBrown1)
                        }
                        Button(
                            onClick = onPayment,
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Icon(Icons.Default.Payments, null, tint = TextLight, modifier = Modifier.size(16.dp))
                            Text("Payment", color = TextLight)
                        }
                        OutlinedButton(
                            onClick = onPrint,
                            modifier = Modifier.weight(1f).height(40.dp),
                            border = BorderStroke(1.dp, BorderGold)
                        ) {
                            Icon(Icons.Default.LocalPrintshop, contentDescription = "Print", tint = PrimaryGold, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                )
            }
        }
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
