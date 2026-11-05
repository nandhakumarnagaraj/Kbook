@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.billing.ui

import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.core.util.CurrencyUtils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.LocalPrintshop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.core.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.core.designsystem.verticalScrollbar
import com.khanabook.lite.pos.core.navigation.horizontalNavigationSwipe
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.billing.viewmodel.ActiveOrderSummaryRow
import com.khanabook.lite.pos.feature.billing.viewmodel.ActiveOrdersViewModel

@Composable
fun ActiveOrdersScreen(
    onBack: () -> Unit,
    onOpenActiveOrder: (Long) -> Unit,
    onCollectPayment: (Long) -> Unit,
    viewModel: ActiveOrdersViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredRows by viewModel.filteredRows.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    LaunchedEffect(Unit) {
        viewModel.message.collect { event ->
            KhanaToast.show(event.message, event.kind)
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
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
                actions = {
                    IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.setSearchQuery("")
                        }
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchExpanded) "Close search" else "Search orders",
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
                .horizontalNavigationSwipe(onSwipeRight = onBack),
            contentAlignment = Alignment.TopCenter
        ) {
            com.khanabook.lite.pos.core.designsystem.ListLayout(
                modifier = Modifier.fillMaxSize().widthIn(max = layout.maxContentWidth),
                filterBar = {
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        KhanaBookInputField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            label = "Search active orders",
                            placeholder = "OrderNo, CustomerName, PhoneNumber, ItemName, BillAmount, OrderType, InvoiceNo",
                            marqueePlaceholder = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryGold) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = TextGold
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium)
                                .padding(top = spacing.small, bottom = spacing.smallMedium),
                            singleLine = true
                        )
                    }
                },
                isEmpty = filteredRows.isEmpty(),
                emptyState = { EmptyActiveOrders() }
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
                    contentPadding = PaddingValues(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items(filteredRows, key = { it.bill.id }) { row ->
                        ActiveOrderCard(
                            row = row,
                            onOpen = { onOpenActiveOrder(row.bill.id) },
                            onPayment = { onCollectPayment(row.bill.id) },
                            onMoreActions = { onOpenActiveOrder(row.bill.id) }
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
    onMoreActions: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val bill = row.bill

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.26f)),
        shape = KhanaRadii.md
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(KhanaBookTheme.iconSize.small)
                    )
                    Text(
                        text = "OrderNo: ${bill.dailyOrderDisplay}",
                        color = TextGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                KhanaStatusBadge(
                    text = if (row.hasNewKitchenItems) "KOT Pending" else "KOT Sent",
                    kind = if (row.hasNewKitchenItems) KhanaStatusKind.Warning else KhanaStatusKind.Success,
                    filled = true
                )
            }
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    val customerInfo = buildString {
                        if (!bill.customerName.isNullOrBlank()) {
                            append(bill.customerName)
                        }
                        if (!bill.customerWhatsapp.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(bill.customerWhatsapp)
                        }
                        if (isEmpty() && bill.orderType.isNotBlank()) {
                            append(bill.orderType.replaceFirstChar { it.uppercase() })
                        }
                    }
                    if (customerInfo.isNotEmpty()) {
                        Text(
                            text = customerInfo,
                            color = TextLight.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val itemsSubtitle = when {
                        row.singleItemName != null -> row.singleItemName
                        row.itemCount > 0 -> "${row.itemCount} items"
                        else -> "No items"
                    }
                    Text(
                        text = itemsSubtitle,
                        color = TextGold.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = CurrencyUtils.formatPrice(bill.totalAmount),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onPayment,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = KhanaRadii.md,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Payments, null, tint = TextLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (row.requiresPaymentRecovery) "Recover" else "Payment",
                        color = TextLight,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
                OutlinedButton(
                    onClick = onMoreActions,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    border = BorderStroke(1.dp, BorderGold),
                    shape = KhanaRadii.md,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Actions", tint = PrimaryGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "More Actions",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

