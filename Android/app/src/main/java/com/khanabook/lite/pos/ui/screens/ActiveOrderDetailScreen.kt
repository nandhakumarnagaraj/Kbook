@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.gesture.horizontalNavigationSwipe
import com.khanabook.lite.pos.ui.screens.activeorder.ActiveOrderActionGrid
import com.khanabook.lite.pos.ui.screens.activeorder.ActiveOrderSummaryCard
import com.khanabook.lite.pos.ui.screens.activeorder.ItemSection
import com.khanabook.lite.pos.ui.screens.activeorder.activeOrderTitle
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.RichEspresso
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.ActiveOrderDetailViewModel

@Composable
fun ActiveOrderDetailScreen(
    onBack: () -> Unit,
    onAddItems: (Long) -> Unit,
    onCollectPayment: (Long) -> Unit,
    viewModel: ActiveOrderDetailViewModel = hiltViewModel()
) {
    val billWithItems by viewModel.bill.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }

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
                            hasSentItems = sentItems.isNotEmpty(),
                            onAddItems = { onAddItems(detail.bill.id) },
                            onUpdateKot = viewModel::updateKot,
                            onPayment = { onCollectPayment(detail.bill.id) },
                            onPrintBill = viewModel::printBill,
                            onReprintKot = viewModel::reprintKot,
                            onCancel = { showCancelDialog = true }
                        )
                    }
                    item {
                        ItemSection(
                            title = "Sent to Kitchen",
                            items = sentItems,
                            emptyText = "No items sent to kitchen yet",
                            highlighted = false
                        )
                    }
                    item {
                        ItemSection(
                            title = "Pending KOT",
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
