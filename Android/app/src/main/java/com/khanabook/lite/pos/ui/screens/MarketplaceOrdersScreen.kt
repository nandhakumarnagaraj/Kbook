package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderDto
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.ui.designsystem.SkeletonListItem
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.MarketplaceOrderViewModel
import com.khanabook.lite.pos.ui.viewmodel.MarketplaceOrdersUiState

/**
 * Marketplace (Swiggy / Zomato) order inbox.
 *
 * Per design D13 this screen is backed directly by the REST repository — there is
 * no Room cache — and renders an explicit offline state when the network is
 * unavailable. New orders also surface as FCM pushes (notification type
 * "marketplace_order") so the bell badge fires even when this screen is closed.
 */
@Composable
fun MarketplaceOrdersScreen(
    onBack: () -> Unit,
    viewModel: MarketplaceOrderViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    val toolbarHeight = 64.dp
    val bottomListPadding = if (layout.isCompact) 88.dp else 24.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        KhanaBookScreenScaffold(
            title = "Marketplace Orders",
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = toolbarHeight)
            ) {
                when (val state = uiState) {
                    is MarketplaceOrdersUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            repeat(6) { SkeletonListItem() }
                        }
                    }

                    is MarketplaceOrdersUiState.Error -> {
                        val message = (state as MarketplaceOrdersUiState.Error).message
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val iconTint = if (isOffline) TextGold else DangerRed
                            val icon = if (isOffline) Icons.Default.CloudOff else Icons.Default.Error
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text(
                                text = message,
                                color = TextLight,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            TextButton(
                                onClick = { viewModel.refresh() },
                                enabled = !isOffline,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry", color = PrimaryGold)
                            }
                        }
                    }

                    is MarketplaceOrdersUiState.Online -> {
                        val online = state as MarketplaceOrdersUiState.Online
                        val orders = online.orders
                        if (orders.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(spacing.large),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = TextGold.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(spacing.medium))
                                Text(
                                    text = "No pending marketplace orders",
                                    color = TextGold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = bottomListPadding)
                            ) {
                                orders.forEach { order ->
                                    MarketplaceOrderItemRow(
                                        order = order,
                                        onAction = { viewModel.refresh() },
                                        onAccept = { viewModel.accept(order.id) },
                                        onReject = { viewModel.reject(order.id, "") },
                                        onMarkReady = { viewModel.markReady(order.id) },
                                        onComplete = { viewModel.complete(order.id) }
                                    )
                                    if (order != orders.last()) {
                                        Divider(color = BorderGold.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceOrderItemRow(
    order: MarketplaceOrderDto,
    onAction: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onMarkReady: () -> Unit,
    onComplete: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val canAccept = order.orderStatus == "pending"
    val canReject = order.orderStatus == "pending"
    val canMarkReady = order.orderStatus == "accepted" || order.orderStatus == "preparing"
    val canComplete = order.orderStatus == "ready"

    KhanaBookCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        onClick = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.customerName ?: "Customer",
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = order.displayAmount.ifEmpty { "—" },
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = platformLabel(order.platform) + " · " + statusLabel(order.orderStatus),
                color = TextGold,
                style = MaterialTheme.typography.bodySmall
            )
            if (canAccept || canReject || canMarkReady || canComplete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canAccept) ActionChip("Accept", selected = true) { onAccept(); onAction() }
                    if (canMarkReady) ActionChip("Ready", selected = true) { onMarkReady(); onAction() }
                    if (canComplete) ActionChip("Complete", selected = true) { onComplete(); onAction() }
                    if (canReject) ActionChip("Reject", selected = false) { onReject(); onAction() }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) DarkBrown2 else androidx.compose.ui.graphics.Color.Transparent
        )
    ) {
        Text(text = text, color = if (selected) PrimaryGold else NonVegRed)
    }
}

@Composable
private fun platformLabel(platform: String): String = when (platform.uppercase()) {
    "SWIGGY" -> "Swiggy"
    "ZOMATO" -> "Zomato"
    else -> platform
}

@Composable
private fun statusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> "Pending"
    "accepted" -> "Accepted"
    "preparing" -> "Preparing"
    "ready" -> "Ready"
    "completed" -> "Completed"
    "rejected" -> "Rejected"
    else -> status
}
