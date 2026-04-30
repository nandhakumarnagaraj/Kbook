@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReprintKdsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    billingViewModel: BillingViewModel = hiltViewModel()
) {
    val viewModel: com.khanabook.lite.pos.ui.viewmodel.HomeViewModel = hiltViewModel()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingBills by remember { mutableStateOf<List<BillWithItems>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    fun loadPending() {
        scope.launch {
            loading = true
            pendingBills = runCatching {
                viewModel.reprintPendingKdsList()
            }.getOrDefault(emptyList())
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        loadPending()
        viewModel.message.collect { msg ->
            KhanaToast.show(msg, ToastKind.Info)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                    Box {
                        IconButton(onClick = {
                            refreshing = true
                            loadPending()
                        }) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = PrimaryGold)
                        }
                    }
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryGold)
                }
            } else if (pendingBills.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen.copy(alpha = 0.4f), modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Text("All KDS orders printed", color = TextGold.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                    contentPadding = PaddingValues(start = spacing.medium, end = spacing.medium, top = spacing.medium, bottom = spacing.bottomListPadding),
                    state = rememberLazyListState()
                ) {
                    item {
                        // "Reprint All" button at the top
                        ReprintAllCard(
                            count = pendingBills.size,
                            onReprintAll = {
                                scope.launch {
                                    viewModel.reprintPendingKds()
                                    delay(500)
                                    loadPending()
                                }
                            }
                        )
                    }
                    items(pendingBills, key = { it.bill.id }) { billWithItems ->
                        KdsPendingCard(
                            billWithItems = billWithItems,
                            onPrint = {
                                billingViewModel.printReceipt(it)
                                scope.launch {
                                    delay(500)
                                    loadPending()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReprintAllCard(
    count: Int,
    onReprintAll: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reprint All Pending",
                    color = DangerRed,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$count pending ticket(s)",
                    color = DangerRed.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onReprintAll,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Print, null, modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reprint All")
            }
        }
    }
}

@Composable
private fun KdsPendingCard(
    billWithItems: BillWithItems,
    onPrint: (BillWithItems) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val bill = billWithItems.bill
    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
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
                Surface(
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "KDS PENDING",
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = spacing.small),
                color = BorderGold.copy(alpha = 0.2f)
            )
            Text(
                text = billWithItems.items.joinToString(", ") { "${it.quantity}x ${it.itemName}" },
                color = TextLight,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Button(
                onClick = { onPrint(billWithItems) },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Print,
                    null,
                    tint = DarkBrown1,
                    modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Reprint",
                    color = DarkBrown1,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
